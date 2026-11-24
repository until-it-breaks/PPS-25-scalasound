package scalasound.model.player

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scalasound.model.domain.AudioChunk
import scalasound.model.player.SpeakerAudioOutput.{Format, toPcmBytes}

import java.nio.{ByteBuffer, ByteOrder}
import javax.sound.sampled.AudioFormat

class SpeakerAudioOutputSpec extends AnyFlatSpec with Matchers:

  /** Reads the encoded bytes back as 16-bit samples, in the order they were written. */
  private def encodedSamples(chunk: AudioChunk): List[Int] =
    val shorts = ByteBuffer
      .wrap(chunk.toPcmBytes)
      .order(ByteOrder.LITTLE_ENDIAN)
      .asShortBuffer
    List.tabulate(shorts.remaining)(i => shorts.get(i).toInt)

  "toPcmBytes" should "scale a sample to a 16-bit value" in {
    // 0.0 -> 0, 0.5 -> 16383 (half of 32767, truncated), 1.0 -> 32767
    encodedSamples(AudioChunk.MonoChunk(Vector(0.0f, 0.5f, 1.0f))) shouldBe List(0, 16383, 32767)
  }

  it should "keep negative samples negative" in {
    encodedSamples(AudioChunk.MonoChunk(Vector(-0.5f, -1.0f))) shouldBe List(-16383, -32767)
  }

  it should "clamp samples above 1.0 to the maximum" in {
    encodedSamples(AudioChunk.MonoChunk(Vector(2.0f))) shouldBe List(32767)
  }

  it should "clamp samples below -1.0 to the minimum" in {
    encodedSamples(AudioChunk.MonoChunk(Vector(-2.0f))) shouldBe List(-32767)
  }

  it should "alternate left and right samples for stereo" in {
    val chunk = AudioChunk.StereoChunk(left = Vector(1.0f, 0.0f), right = Vector(-1.0f, 0.5f))
    // frame 0: left, right, frame 1: left, right
    encodedSamples(chunk) shouldBe List(32767, -32767, 0, 16383)
  }

  it should "write the low byte of each sample first (little-endian)" in {
    // 1.0 -> 32767 = 0x7FFF: low byte 0xFF (which is -1 as a signed byte), then high byte 0x7F (127)
    AudioChunk.MonoChunk(Vector(1.0f)).toPcmBytes.toList shouldBe List[Byte](-1, 127)
  }

  it should "use 2 bytes per mono frame" in {
    AudioChunk.MonoChunk(Vector.fill(10)(0.0f)).toPcmBytes.length shouldBe 20
  }

  it should "use 4 bytes per stereo frame" in {
    AudioChunk
      .StereoChunk(Vector.fill(10)(0.0f), Vector.fill(10)(0.0f))
      .toPcmBytes
      .length shouldBe 40
  }

  it should "produce no bytes for an empty chunk" in {
    AudioChunk.MonoChunk(Vector.empty).toPcmBytes shouldBe empty
  }

  "Format.bufferBytes" should "hold 200 ms of mono audio" in {
    // 1 channel * 2 bytes * 44100 frames/s * 0.2 s
    Format(44100, 1).bufferBytes shouldBe 17640
  }

  it should "double for stereo" in {
    Format(44100, 2).bufferBytes shouldBe 35280
  }

  it should "scale with the sample rate" in {
    Format(48000, 1).bufferBytes shouldBe 19200
  }

  "Format.toAudioFormat" should "describe 16-bit signed little-endian PCM" in {
    val format = Format(44100, 2).toAudioFormat
    format.getEncoding shouldBe AudioFormat.Encoding.PCM_SIGNED
    format.getSampleSizeInBits shouldBe 16
    format.getSampleRate shouldBe 44100f
    format.getFrameRate shouldBe 44100f
    format.getChannels shouldBe 2
    format.isBigEndian shouldBe false
  }

  it should "use 2 bytes per frame for mono and 4 for stereo" in {
    Format(44100, 1).toAudioFormat.getFrameSize shouldBe 2
    Format(44100, 2).toAudioFormat.getFrameSize shouldBe 4
  }
