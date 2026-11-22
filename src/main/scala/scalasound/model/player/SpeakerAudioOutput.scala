package scalasound.model.player

import cats.effect.IO
import scalasound.model.domain.AudioChunk

import java.nio.{ByteBuffer, ByteOrder}
import javax.sound.sampled.{AudioFormat, AudioSystem, SourceDataLine}

/** An [[AudioOutput]] that plays [[AudioChunk]]s to the speakers using the Java Sound API.
  *
  * Keeps one [[SourceDataLine]] open and reuses it while the format (sample rate and channel count)
  * stays the same; a format change drains and replaces it.
  */
private final class SpeakerAudioOutput extends AudioOutput:
  import SpeakerAudioOutput.*

  private var active: Option[ActiveLine] = None

  /** Writes a chunk to the sound card, opening or reopening the line if the format changed.
    * Completes when the bytes have been accepted by the line's buffer.
    */
  override def write(chunk: AudioChunk, sampleRate: Int): IO[Unit] =
    for
      bytes <- IO(chunk.toPcmBytes)
      _     <- IO.blocking {
        val line = lineFor(Format(sampleRate, chunk.channelCount))
        line.write(bytes, 0, bytes.length)
      }
    yield ()

  private def lineFor(format: Format): SourceDataLine =
    active.filter(_.format == format) match
      case Some(current) => current.line
      case None          =>
        val previous = active
        active = None
        previous.foreach(p => release(p.line))
        val line = open(format)
        active = Some(ActiveLine(format, line))
        line

  private def open(format: Format): SourceDataLine =
    val line = AudioSystem.getSourceDataLine(format.toAudioFormat)
    line.open(format.toAudioFormat, format.bufferBytes)
    line.start()
    line

  private def release(line: SourceDataLine): Unit =
    line.drain()
    line.close()

private object SpeakerAudioOutput:
  private val BitsPerSample  = 16
  private val BytesPerSample = BitsPerSample / 8
  private val MaxAmplitude   = Short.MaxValue.toFloat
  private val BufferMillis   = 200

  private final case class ActiveLine(format: Format, line: SourceDataLine)

  private final case class Format(sampleRate: Int, channels: Int):
    def bufferBytes: Int = channels * BytesPerSample * sampleRate * BufferMillis / 1000

    def toAudioFormat: AudioFormat =
      AudioFormat(
        AudioFormat.Encoding.PCM_SIGNED,
        sampleRate.toFloat,
        BitsPerSample,
        channels,
        channels * BytesPerSample,
        sampleRate.toFloat,
        false // little-endian, must match ByteOrder.LITTLE_ENDIAN in toPcmBytes
      )

  extension (chunk: AudioChunk)
    private def channelCount: Int = chunk match
      case AudioChunk.MonoChunk(_)      => 1
      case AudioChunk.StereoChunk(_, _) => 2

    /** Encodes the [[AudioChunk]] as raw 16-bit signed PCM, little-endian.
      *
      * Mono chunks produce one 2-byte sample per frame. Stereo chunks are interleaved (left, right,
      * left, right, ...), so each frame takes 4 bytes.
      *
      * @return
      *   the encoded bytes, `frameCount * channels * 2` of them
      */
    private[player] def toPcmBytes: Array[Byte] =
      val buffer = ByteBuffer
        .allocate(chunk.frameCount * chunk.channelCount * BytesPerSample)
        .order(ByteOrder.LITTLE_ENDIAN)
      chunk match
        case AudioChunk.MonoChunk(samples) =>
          samples.foreach(sample => buffer.putShort(sample.toPcm16))
        case AudioChunk.StereoChunk(left, right) =>
          left
            .zip(right)
            .foreach: (l, r) =>
              buffer.putShort(l.toPcm16)
              buffer.putShort(r.toPcm16)
      buffer.array()

  extension (sample: Float)
    private def toPcm16: Short =
      (sample.max(-1f).min(1f) * MaxAmplitude).toShort
