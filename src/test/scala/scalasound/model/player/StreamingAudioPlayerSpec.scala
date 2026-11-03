package scalasound.model.player

import cats.effect.IO
import munit.CatsEffectSuite
import scalasound.model.domain.{AudioChunk, MonoTrack, StereoTrack}
import scalasound.model.player.{AudioOutput, StreamingAudioPlayer}
import scalasound.model.processing.SimpleAudioProcessor
import scalasound.model.state.{AppStateStore, Command, ImportState, PlaybackState}

import scala.concurrent.duration.DurationInt

class StreamingAudioPlayerSpec extends CatsEffectSuite:

  class FakeAudioOutput extends AudioOutput:
    var writtenChunks: List[(AudioChunk, Int)] = Nil
    override def write(chunk: AudioChunk, sampleRate: Int): IO[Unit] = IO {
      writtenChunks = writtenChunks :+ (chunk, sampleRate)
    }

  test("StreamingAudioPlayer plays mono chunks when state is Playing and advances") {
    for
      store <- AppStateStore.make
      fakeOut = FakeAudioOutput()
      player = StreamingAudioPlayer(SimpleAudioProcessor, fakeOut)
      mockTrack = MonoTrack(
        sampleRate = 44100,
        chunks = Vector(
          AudioChunk.MonoChunk(Vector(0.1f, 0.2f)),
          AudioChunk.MonoChunk(Vector(0.3f, 0.4f))
        )
      )
      _ <- player.run(store).compile.drain.background.use { _ =>
        for
          _ <- store.dispatch(Command.UpdateImport(ImportState.Imported(mockTrack)))
          _ <- IO.sleep(20.millis)
          _ <- store.dispatch(Command.Play)
          // Wait until playback reaches the end
          _ <- store.playbackChanges
            .dropWhile(_ != PlaybackState.Playing)
            .filter(_ == PlaybackState.Stopped)
            .take(1)
            .compile
            .drain
        yield ()
      }
      state <- store.current
    yield
      assertEquals(fakeOut.writtenChunks.length, 2)
      assertEquals(state.playbackState, PlaybackState.Stopped)
  }

  test("StreamingAudioPlayer plays stereo chunks correctly") {
    for
      store <- AppStateStore.make
      fakeOut = FakeAudioOutput()
      player = StreamingAudioPlayer(SimpleAudioProcessor, fakeOut)
      stereoTrack = StereoTrack(
        sampleRate = 44100,
        chunks = Vector(
          AudioChunk.StereoChunk(Vector(0.1f, 0.2f), Vector(0.1f, 0.2f)),
          AudioChunk.StereoChunk(Vector(0.3f, 0.4f), Vector(0.3f, 0.4f))
        )
      )
      _ <- player.run(store).compile.drain.background.use { _ =>
        for
          _ <- store.dispatch(Command.UpdateImport(ImportState.Imported(stereoTrack)))
          _ <- IO.sleep(20.millis)
          _ <- store.dispatch(Command.Play)
          // Wait until playback reaches the end
          _ <- store.playbackChanges
            .dropWhile(_ != PlaybackState.Playing)
            .filter(_ == PlaybackState.Stopped)
            .take(1)
            .compile
            .drain
        yield ()
      }
      state <- store.current
    yield
      assertEquals(fakeOut.writtenChunks.length, 2)
      val allStereo = fakeOut.writtenChunks.forall {
        case (AudioChunk.StereoChunk(_, _), _) => true
        case _ => false
      }
      assert(allStereo)
      assertEquals(state.playbackState, PlaybackState.Stopped)
  }