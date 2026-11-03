package scalasound.model.player

import cats.effect.IO
import scalasound.model.domain.AudioChunk

import scala.concurrent.duration.{Duration, FiniteDuration, NANOSECONDS}

/**
 * Destination for processed audio, typically the sound card.
 */
trait AudioOutput:
  /**
   * Outputs one chunk.
   *
   * @param chunk mono or stereo samples in the range [-1.0, 1.0]
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
    println(s"[CONSOLE_SOUND_OUTPUT] <- $kind chunk, $frames frames")
  }

/**
 * Decorator that strictly wraps a [[ConsoleAudioOutput]] and adds real-time pacing
 * via asynchronous sleep, matching the chunk's actual playback duration.
 */
class BlockingConsoleAudioOutput(out: AudioOutput = ConsoleAudioOutput()) extends AudioOutput:
  override def write(chunk: AudioChunk, sampleRate: Int): IO[Unit] =
    val frames = chunk match
      case AudioChunk.MonoChunk(samples)       => samples.length
      case AudioChunk.StereoChunk(left, right) => left.length
    val durationMs = (frames.toDouble / sampleRate) * 1000.0
    val duration = FiniteDuration((durationMs * 1_000_000).toLong, NANOSECONDS)
    for
      _ <- out.write(chunk, sampleRate)
      _ <- IO.sleep(duration.max(Duration.Zero))
    yield ()

object AudioOutput:
  /** Creates an unpaced console output that logs chunk metadata instantly. */
  def console: AudioOutput = ConsoleAudioOutput()
  /** Creates a paced console output that simulates real-time audio playback delays. */
  def blockingConsole: AudioOutput = BlockingConsoleAudioOutput()
  /** Creates a hardware speaker output streaming to the system sound card. */
  def speakers: AudioOutput = SpeakerAudioOutput()