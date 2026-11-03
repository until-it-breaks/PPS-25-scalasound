package scalasound.model.player

import cats.effect.IO
import scalasound.model.domain.AudioChunk

import java.nio.{ByteBuffer, ByteOrder}
import javax.sound.sampled.{AudioFormat, AudioSystem, SourceDataLine}

object PcmAudioEncoding:
  val BitsPerSample: Int = 16
  private val BytesPerSample: Int = BitsPerSample / 8
  private val Pcm16MaxAmplitude: Float = 32767.0f

  extension (chunk: AudioChunk)

    /**
     * Converts an immutable [[AudioChunk]] into raw 16-bit PCM bytes.
     */
    def toPcmBytes: Array[Byte] =
      import AudioChunk.*
      val frameCount = chunk match
        case MonoChunk(samples)      => samples.size
        case StereoChunk(left, right) => math.min(left.size, right.size)
      val buffer = ByteBuffer.allocate(
        frameCount * BytesPerSample * (chunk match
          case MonoChunk(_) => 1
          case StereoChunk(_, _) => 2
          )
      )
      buffer.order(ByteOrder.LITTLE_ENDIAN)
      chunk match
        case MonoChunk(samples) =>
          samples.foreach { sample =>
            val clamped = math.max(-1.0f, math.min(1.0f, sample))
            buffer.putShort((clamped * Pcm16MaxAmplitude).toShort)
          }
        case StereoChunk(left, right) =>
          left.zip(right).foreach { (left, right) =>
            val clampedLeft = math.max(-1.0f, math.min(1.0f, left))
            val clampedRight = math.max(-1.0f, math.min(1.0f, right))
            buffer.putShort((clampedLeft * Pcm16MaxAmplitude).toShort)
            buffer.putShort((clampedRight * Pcm16MaxAmplitude).toShort)
          }
      buffer.array()

import PcmAudioEncoding.*

/**
 * An [[AudioOutput]] that plays audio chunks to the speakers using the Java Sound API.
 */
class SpeakerAudioOutput extends AudioOutput:

  private case class ActiveLine(line: SourceDataLine, sampleRate: Int, channels: Int)
  private var activeLine: Option[ActiveLine] = None

  /**
   * Writes an [[AudioChunk]] at the specified sample rate to the speaker output device.
   *
   * This operation blocks until the audio bytes are successfully written to the
   * underlying line buffer.
   *
   * @param chunk the [[AudioChunk]] to be played
   * @param sampleRate the sample rate in Hz (e.g., 44100, 48000)
   * @return an [[IO]] effect wrapping the write operation
   */
  override def write(chunk: AudioChunk, sampleRate: Int): IO[Unit] =
    IO.blocking {
      val channels = chunk match
        case AudioChunk.MonoChunk(_) => 1
        case AudioChunk.StereoChunk(_, _) => 2
      val bytes = chunk.toPcmBytes
      val line = lineFor(sampleRate, channels)
      line.write(bytes, 0, bytes.length)
    }.void

  /**
   * Closes any active audio output line and releases system resources associate with the speaker playback.
   *
   * @return an [[IO]] effect wrapping the cleanup operation
   */
  def close: IO[Unit] =
    IO.blocking {
      activeLine.foreach(active => closeLine(active.line))
      activeLine = None
    }

  private def lineFor(sampleRate: Int, channels: Int): SourceDataLine =
    activeLine.filter(active => active.sampleRate == sampleRate && active.channels == channels) match
      case Some(active) => active.line
      case None =>
        activeLine.foreach(active => closeLine(active.line))
        val line = openLine(sampleRate, channels)
        activeLine = Some(ActiveLine(line, sampleRate, channels))
        line

  private def openLine(sampleRate: Int, channels: Int): SourceDataLine =
    val frameSize = channels * (BitsPerSample / 8)
    val format = new AudioFormat(
      AudioFormat.Encoding.PCM_SIGNED,
      sampleRate.toFloat,
      BitsPerSample,
      channels,
      frameSize,
      sampleRate.toFloat,
      false
    )
    val line = AudioSystem.getSourceDataLine(format)
    line.open(format, frameSize * (sampleRate / 5))
    line.start()
    line

  private def closeLine(line: SourceDataLine): Unit =
    try
      line.drain()
      line.stop()
      line.close()
    catch case _: Exception => ()