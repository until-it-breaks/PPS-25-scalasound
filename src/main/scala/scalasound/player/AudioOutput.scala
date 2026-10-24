package scalasound.player

import cats.effect.IO
import scalasound.domain.AudioChunk

/**
 * Destination for processed audio, typically the sound card.
 */
trait AudioOutput:
  /**
   * Outputs one chunk.
   *
   * @param chunk      mono or stereo samples in the range [-1.0, 1.0]
   * @param sampleRate sample rate of the chunk, in Hz
   */
  def write(chunk: AudioChunk, sampleRate: Int): IO[Unit]

/**
 * Simple [[AudioOutput]] implementation that logs chunk metadata to the console.
 */
class ConsoleAudioOutput extends AudioOutput:
  override def write(chunk: AudioChunk, sampleRate: Int): IO[Unit] = IO {
    val (kind, frames) = chunk match
      case AudioChunk.MonoChunk(samples) => ("mono", samples.length)
      case AudioChunk.StereoChunk(left, right) => ("stereo", left.length)
    println(s"[SOUND_OUTPUT] <- $kind chunk, $frames frames")
  }

/**
 * Decorator that strictly wraps a [[ConsoleAudioOutput]] and adds real-time pacing
 * via blocking sleep, matching the chunk's actual playback duration.
 */
class BlockingConsoleAudioOutput extends AudioOutput:
  private val out: AudioOutput = ConsoleAudioOutput()
  override def write(chunk: AudioChunk, sampleRate: Int): IO[Unit] =
    import scala.util.Try

    for
      _ <- out.write(chunk, sampleRate)
      frames = chunk match
        case AudioChunk.MonoChunk(samples) => samples.length
        case AudioChunk.StereoChunk(left, right) => left.length

      durationMs = (frames.toDouble / sampleRate) * 1000.0
      millis = math.max(0L, durationMs.toLong)
      nanos = math.max(0, ((durationMs - millis) * 1_000_000).toInt)

      _ <- IO.blocking {
        Try(Thread.sleep(millis, nanos)).recover {
          case _: InterruptedException => Thread.currentThread().interrupt()
        }
      }
    yield ()

object AudioOutput:
  def console: AudioOutput = ConsoleAudioOutput()
  def blocking: AudioOutput = BlockingConsoleAudioOutput()


import cats.effect.IOApp

object AudioOutputMain extends IOApp.Simple:
  val run: IO[Unit] =
    for
      sampleRate = 44100
      chunks = Vector.fill(10)(AudioChunk.MonoChunk(Vector.fill(4410)(0.0f)))
      _ <- IO.println("Starting audio playback...")
      output = AudioOutput.blocking
      _ <- chunks.foldLeft(IO.unit) { (acc, chunk) =>
        acc *> output.write(chunk, sampleRate)
      }
      _ <- IO.println("Playback complete")
    yield ()