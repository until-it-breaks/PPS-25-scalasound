package scalasound.model.player

import cats.effect.IO
import scalasound.model.domain.AudioChunk

import scala.concurrent.duration.{FiniteDuration, NANOSECONDS}

/**
 * Destination for [[AudioChunk]]s, such as the sound card or the console.
 */
trait AudioOutput:
  /**
   * Outputs one chunk.
   *
   * @param chunk mono or stereo samples in the range [-1.0, 1.0]
   * @param sampleRate sample rate of the chunk, in Hz; must be positive
   * @return an effect that completes once the chunk has been accepted by the output
   */
  def write(chunk: AudioChunk, sampleRate: Int): IO[Unit]

object AudioOutput:
  private val ConsoleTag = "[CONSOLE_SOUND_OUTPUT]"

  /** Prints chunk metadata to the console, without pacing. */
  val console: AudioOutput = (chunk, sampleRate) =>
    IO.println(s"$ConsoleTag <- ${chunk.kind} chunk, ${chunk.frameCount} frames @ $sampleRate Hz")

  extension (out: AudioOutput)
    /**
     * Returns an output that writes through `out`, then sleeps for the chunk's playback
     * duration, so the stream advances at real-time speed.
     */
    def paced: AudioOutput = (chunk, sampleRate) =>
      for
        _ <- out.write(chunk, sampleRate)
        _ <- IO.sleep(playbackDuration(chunk, sampleRate))
      yield ()

  /** Streams to the system sound card. */
  def speakers: AudioOutput = SpeakerAudioOutput()

  extension (chunk: AudioChunk)
    private def kind: String = chunk match
      case AudioChunk.MonoChunk(_) => "mono"
      case AudioChunk.StereoChunk(_, _) => "stereo"

  private def playbackDuration(chunk: AudioChunk, sampleRate: Int): FiniteDuration =
    FiniteDuration(chunk.frameCount.toLong * 1_000_000_000L / sampleRate, NANOSECONDS)