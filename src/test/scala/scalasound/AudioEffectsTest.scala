package scalasound

import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scalasound.domain.AudioChunk
import scalasound.domain.AudioChunk.{MonoChunk, StereoChunk}
import scalasound.processing.{GainEffect, IdentityEffect, PanEffect, VolumeEffect}

/**
 * Test class containing all the checks for the effects applied on an audio chunk.
 */
class AudioEffectsTest extends AnyFlatSpec with Matchers with BeforeAndAfter:

  val sizeChunk: Int = 10
  val valueChunk: Float = 1.0f
  var chunk: AudioChunk = MonoChunk(Vector.empty)

  before {
    chunk = MonoChunk(Vector.fill(sizeChunk)(valueChunk))
  }

  "Chunk" should "not be empty" in {
    chunk.getVectors should not be empty
  }

  it should "have length of 10" in {
    chunk.getVectors.head.size shouldEqual 10
  }

  "IdentityEffect" should "return the same initial chunk" in {
    val resultChunk = IdentityEffect()(chunk)
    resultChunk shouldBe chunk
  }

  "GainEffect" should "return a new chunk where values are in [-1.0,1.0] interval" in {
    val gain = 1.9f
    val resultChunk = GainEffect(gain)(chunk)
    resultChunk shouldBe MonoChunk(Vector.fill(sizeChunk)(valueChunk))
  }

  it should "throw an [IllegalArgumentException] if the gain is > 2.0f or gain < 0.0f" in {
    assertThrows[IllegalArgumentException] {
      val gain = 3.0f
      val resultChunk = GainEffect(gain)(chunk)
    }
  }

  "PanEffect" should "return a new StereoChunk" in {
    val pan = 0.5f
    val resultChunk = PanEffect(pan)(chunk)
    resultChunk.isInstanceOf[StereoChunk] shouldBe true
  }

  it should "throw an [IllegalArgumentException] if the pan is < -1.0f or pan > 1.0f" in {
    assertThrows[IllegalArgumentException] {
      val pan = 3.0f
      val resultChunk = PanEffect(pan)(chunk)
    }
  }

  "VolumeEffect" should "return the same chunk if the volume is set to default" in {
    val resultChunk = VolumeEffect()(chunk)
    resultChunk shouldBe chunk
  }

  it should "throw an [IllegalArgumentException] if the volume is < 0.0f or volume > 1.0f" in {
    assertThrows[IllegalArgumentException] {
      val volume = 2.0f
      val resultChunk = VolumeEffect(volume)(chunk)
    }
  }

  // TODO: add test for other effects and for the processing?
  // TODO: Remove tests with throw IllegalArgumentException