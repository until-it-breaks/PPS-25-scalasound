package scalasound.io

import java.nio.ByteBuffer
import java.nio.ByteOrder.LITTLE_ENDIAN
import java.nio.charset.StandardCharsets.US_ASCII

/** A collection of fixtures for creating WAV files for testing. */
object WavFixtures:

  val PcmFormatTag: Int = 1
  val FloatFormatTag: Int = 3

  /** Builds a WAV file with 16-bit little-endian signed PCM data.
   *
   *  @param sampleRate the sample rate in Hz
   *  @param channels the number of audio channels (1 for mono, 2 for stereo)
   *  @param samples the audio samples as a sequence of integers
   *  @return a byte array representing the WAV file
   **/
  def pcm16Wav(sampleRate: Int, channels: Int, samples: Seq[Int]): Array[Byte] =
    wav(PcmFormatTag, sampleRate, channels, 16, pcm16Data(samples))

  private def pcm16Data(samples: Seq[Int]): Array[Byte] =
    val buffer = ByteBuffer.allocate(samples.length * 2).order(LITTLE_ENDIAN)
    samples.foreach(s => buffer.putShort(s.toShort))
    buffer.array()

  private def wav(
      formatTag: Int,
      sampleRate: Int,
      channels: Int,
      bitsPerSample: Int,
      data: Array[Byte],
      declaredDataSize: Option[Int] = None
  ): Array[Byte] =
    val blockAlign = channels * bitsPerSample / 8
    val dataSize = declaredDataSize.getOrElse(data.length)
    val buffer = ByteBuffer.allocate(44 + data.length).order(LITTLE_ENDIAN)
    buffer.put("RIFF".getBytes(US_ASCII)).putInt(36 + dataSize).put("WAVE".getBytes(US_ASCII))
    buffer.put("fmt ".getBytes(US_ASCII)).putInt(16)
    buffer.putShort(formatTag.toShort).putShort(channels.toShort).putInt(sampleRate)
    buffer.putInt(sampleRate * blockAlign).putShort(blockAlign.toShort).putShort(bitsPerSample.toShort)
    buffer.put("data".getBytes(US_ASCII)).putInt(dataSize).put(data)
    buffer.array()