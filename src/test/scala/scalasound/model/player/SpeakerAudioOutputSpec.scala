package scalasound.model.player

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scalasound.model.domain.AudioChunk
import scalasound.model.player.SpeakerAudioOutput.toPcmBytes

import java.nio.{ByteBuffer, ByteOrder}

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
    AudioChunk.StereoChunk(Vector.fill(10)(0.0f), Vector.fill(10)(0.0f)).toPcmBytes.length shouldBe 40
  }

  it should "produce no bytes for an empty chunk" in {
    AudioChunk.MonoChunk(Vector.empty).toPcmBytes shouldBe empty
  }