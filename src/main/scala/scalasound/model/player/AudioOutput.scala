package scalasound.model.player

import cats.effect.{IO, Resource}
import scalasound.model.domain.AudioChunk

import scala.concurrent.duration.{FiniteDuration, NANOSECONDS}

/** Destination for [[AudioChunk]]s, such as the sound card or the console.
  */
trait AudioOutput:
  /** Outputs one chunk.
    *
    * @param chunk
    *   the [[AudioChunk]] to be played
    * @param sampleRate
    *   sample rate of the chunk, in Hz
    * @return
    *   an effect that completes once the chunk has been accepted by the output
    */
  def write(chunk: AudioChunk, sampleRate: Int): IO[Unit]

object AudioOutput:

  /** Prints chunk metadata to the console, without pacing. */
  val console: AudioOutput = (chunk, sampleRate) =>
    IO.println(s"Playing ${chunk.kind} chunk with ${chunk.frameCount} frames @ $sampleRate Hz")

  /** Plays chunks via the system sound card.
    *
    * The sound line is opened on the first write and closed when the resource is released. Each
    * write blocks until the line's buffer has room for the chunk, so the audio advances at
    * real-time speed.
    */
  def speakers: Resource[IO, AudioOutput] = SpeakerAudioOutput.make

  extension (out: AudioOutput)
    /** Returns an output that writes through `out`, then sleeps for the chunk's playback duration,
      * so the stream advances at real-time speed.
      *
      * Meant for outputs that return immediately, such as the console. Outputs that already block
      * until the audio has been consumed, such as the speakers, do not need it.
      */
    def paced: AudioOutput = (chunk, sampleRate) =>
      for
        _ <- out.write(chunk, sampleRate)
        _ <- IO.sleep(playbackDuration(chunk, sampleRate))
      yield ()

  extension (chunk: AudioChunk)
    private def kind: String = chunk match
      case AudioChunk.MonoChunk(_)      => "mono"
      case AudioChunk.StereoChunk(_, _) => "stereo"

  private def playbackDuration(chunk: AudioChunk, sampleRate: Int): FiniteDuration =
    FiniteDuration(chunk.frameCount.toLong * 1_000_000_000L / sampleRate, NANOSECONDS)
