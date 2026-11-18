package scalasound.model.player

import cats.effect.IO
import munit.CatsEffectSuite
import scalasound.model.domain.{AudioChunk, AudioTrack, MonoTrack, StereoTrack}
import scalasound.model.player.AudioOutput
import scalasound.model.processing.{
  AudioEffect,
  AudioError,
  AudioProcessor,
  AudioProcessorWithRules
}
import scalasound.model.state.*

import scala.concurrent.duration.{Duration, DurationInt}

class StreamingAudioPlayerSpec extends CatsEffectSuite:
  override def munitIOTimeout: Duration = 5.seconds

  /** [[AudioOutput]] that records every chunk it receives, with the sample rate it was written at.
    */
  private final class RecordingOutput extends AudioOutput:
    var chunks: Vector[AudioChunk] = Vector.empty
    var sampleRates: Vector[Int]   = Vector.empty

    override def write(chunk: AudioChunk, sampleRate: Int): IO[Unit] = IO {
      chunks = chunks :+ chunk
      sampleRates = sampleRates :+ sampleRate
    }

  /** [[AudioOutput]] that always fails. */
  private object FailingOutput extends AudioOutput:
    override def write(chunk: AudioChunk, sampleRate: Int): IO[Unit] =
      IO.raiseError(RuntimeException("error"))

  /** [[AudioProcessor]] that reports an error for every chunk. */
  private object FailingProcessor extends AudioProcessor:
    override def apply(
        chunk: AudioChunk,
        effects: List[AudioEffect],
        chunkIndex: Int
    ): Either[AudioError, AudioChunk] =
      Left(AudioError.AudioProcessorError(this, "error"))

  private val sampleRate = 44100

  private val monoTrack = MonoTrack(
    sampleRate,
    Vector(
      AudioChunk.MonoChunk(Vector(0.1f, 0.2f)),
      AudioChunk.MonoChunk(Vector(0.3f, 0.4f))
    )
  )

  private val stereoTrack = StereoTrack(
    sampleRate,
    Vector(
      AudioChunk.StereoChunk(Vector(0.1f, 0.2f), Vector(0.1f, 0.2f)),
      AudioChunk.StereoChunk(Vector(0.3f, 0.4f), Vector(0.3f, 0.4f))
    )
  )

  private def untilStopped(store: AppStateStore): IO[Unit] =
    store.playbackChanges
      .dropWhile(_ != PlaybackState.Playing)
      .find(_ == PlaybackState.Stopped)
      .compile
      .drain

  /** Runs a player on a fresh store: imports the track, sends `Play` and waits for `Stopped`. */
  private def playToEnd(
      track: AudioTrack,
      out: AudioOutput,
      processor: AudioProcessor = AudioProcessorWithRules
  ): IO[AppState] =
    for
      store <- AppStateStore.make
      _     <- AudioPlayer.streaming(processor, out).run(store).compile.drain.background.use { _ =>
        for
          _      <- store.dispatch(Command.UpdateImport(ImportState.Imported(track)))
          waiter <- untilStopped(store).start
          _      <- store.dispatch(Command.Play)
          _      <- waiter.joinWithNever
        yield ()
      }
      state <- store.current
    yield state

  test("plays every mono chunk in order, then stops") {
    val out = RecordingOutput()
    playToEnd(monoTrack, out).map: state =>
      assertEquals(out.chunks, monoTrack.chunks)
      assertEquals(state.playbackState, PlaybackState.Stopped)
  }

  test("plays every stereo chunk in order, then stops") {
    val out = RecordingOutput()
    playToEnd(stereoTrack, out).map: state =>
      assertEquals(out.chunks, stereoTrack.chunks)
      assertEquals(state.playbackState, PlaybackState.Stopped)
  }

  test("writes every chunk at the track's sample rate") {
    val out = RecordingOutput()
    playToEnd(monoTrack, out).map: _ =>
      assertEquals(out.sampleRates, Vector.fill(monoTrack.chunks.length)(sampleRate))
  }

  test("a track with no chunks stops immediately and writes nothing") {
    val out = RecordingOutput()
    playToEnd(MonoTrack(sampleRate, Vector.empty), out).map: state =>
      assertEquals(out.chunks, Vector.empty)
      assertEquals(state.playbackState, PlaybackState.Stopped)
  }

  test("a failing output stops playback without advancing the play head") {
    playToEnd(monoTrack, FailingOutput).map: state =>
      assertEquals(state.playbackState, PlaybackState.Stopped)
      assertEquals(state.chunkIndex, 0)
  }

  test("a processor error falls back to the original chunk and keeps playing") {
    val out = RecordingOutput()
    playToEnd(monoTrack, out, FailingProcessor).map: state =>
      assertEquals(out.chunks, monoTrack.chunks)
      assertEquals(state.playbackState, PlaybackState.Stopped)
  }
