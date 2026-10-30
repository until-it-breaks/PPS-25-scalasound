package scalasound.player

import cats.effect.{IO, Resource}
import scalasound.domain.AudioChunk

import javax.sound.sampled.{AudioFormat, AudioSystem, SourceDataLine}

object PcmAudioEncoding:
  private val Pcm16MaxAmplitude: Float = 32767.0f
  val BitsPerSample: Int = 16

  private def sampleToBytes(sample: Float): Vector[Byte] =
    val clamped = math.max(-1.0f, math.min(1.0f, sample))
    val pcm = (clamped * Pcm16MaxAmplitude).toShort
    Vector((pcm & 0xff).toByte, ((pcm >> 8) & 0xff).toByte)

  extension (chunk: AudioChunk)
    /**
     * Converts an immutable [[AudioChunk]] into raw 16-bit PCM bytes.
     */
     def toPcmBytes: Array[Byte] =
      import AudioChunk.*
      val bytes = chunk match
        case MonoChunk(samples) =>
          samples.flatMap(sampleToBytes)
        case StereoChunk(left, right) =>
          left.zip(right).flatMap { (leftSample, rightSample) =>
            sampleToBytes(leftSample) ++ sampleToBytes(rightSample)
          }
      bytes.toArray

import PcmAudioEncoding.*

/**
 * An [[AudioOutput]] implementation that plays audio chunks to speakers using the Java Sound API.
 */
class SpeakerAudioOutput extends AudioOutput:

  private def acquireLine(sampleRate: Int, channels: Int): Resource[IO, SourceDataLine] =
    Resource.make(
      IO.blocking {
        val frameSize = channels * (PcmAudioEncoding.BitsPerSample / 8)
        val format = new AudioFormat(
          AudioFormat.Encoding.PCM_SIGNED,
          sampleRate.toFloat,
          PcmAudioEncoding.BitsPerSample,
          channels,
          frameSize,
          sampleRate.toFloat,
          false
        )
        val line = AudioSystem.getSourceDataLine(format)
        line.open(format)
        line.start()
        line
      }
    )(line =>
      IO.blocking {
        line.drain()
        line.stop()
        line.close()
      }
    )

  /**
   * Writes an immutable [[AudioChunk]] to the system sound card at the specified sample rate.
   */
  override def write(chunk: AudioChunk, sampleRate: Int): IO[Unit] =
    val channels = chunk match
      case AudioChunk.MonoChunk(_) => 1
      case AudioChunk.StereoChunk(_, _) => 2

    acquireLine(sampleRate, channels).use { line =>
      IO.blocking {
        val bytes = chunk.toPcmBytes
        line.write(bytes, 0, bytes.length)
      }.void
    }