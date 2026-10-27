package scalasound.processing

import org.scalatest.{BeforeAndAfter, EitherValues}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scalasound.domain.AudioChunk
import scalasound.domain.AudioChunk.{MonoChunk, StereoChunk}
import scalasound.processing.{GainEffect, IdentityEffect, PanEffect, VolumeEffect}

/**
 * Test class containing all the checks for the effects applied on an audio chunk.
 */
class AudioEffectsTest extends AnyFlatSpec with Matchers with BeforeAndAfter with EitherValues:

  val sizeChunk: Int = 10
  val minValueChunk: Float = -1.0f
  val defaultValueChunk: Float = 1.0f
  val maxValueChunk: Float = 1.0f
  var chunk: AudioChunk = MonoChunk(Vector.empty)

  before:
    chunk = MonoChunk(Vector.fill(sizeChunk)(defaultValueChunk))

  "Chunk" should "not be empty" in:
    chunk.getVectors should not be empty

  it should "have length of 10" in:
    chunk.getVectors.head.size shouldEqual 10

  "IdentityEffect" should "return the same initial chunk" in:
    val resultChunk = IdentityEffect()(chunk)
    resultChunk.value shouldBe chunk

  "GainEffect" should "return a new chunk where values are clamped in [-1.0,1.0] interval" in:
    val saturationGain = 1.9f
    val upperChunk = GainEffect(saturationGain)(chunk)
    upperChunk.value shouldBe MonoChunk(Vector.fill(sizeChunk)(maxValueChunk))
    val gain = 0.5f
    val resultChunk = GainEffect(gain)(chunk)
    resultChunk.value shouldBe MonoChunk(Vector.fill(sizeChunk)(gain))

  it should "correctly applying the gain on the chunk when the value does not saturate" in:
    val gain = 0.5f
    val resultChunk = GainEffect(gain)(chunk)
    resultChunk.value shouldBe MonoChunk(Vector.fill(sizeChunk)(gain))

  it should "return an AudioErrors if the gain is > 2.0f or gain < 0.0f" in:
    val gain = 3.0f
    val resultChunk = GainEffect(gain)(chunk)
    resultChunk.left.value shouldBe a [AudioError.AudioEffectsError]

  "PanEffect" should "return a new StereoChunk" in:
    val pan = 0.5f
    val resultChunk = PanEffect(pan)(chunk)
    resultChunk.value shouldBe a [StereoChunk]

  it should "return an AudioErrors if the pan is < -1.0f or pan > 1.0f" in:
    val pan = 3.0f
    val resultChunk = PanEffect(pan)(chunk)
    resultChunk.left.value shouldBe a [AudioError.AudioEffectsError]

  "VolumeEffect" should "return the same chunk if the volume is set to default" in:
    val resultChunk = VolumeEffect()(chunk)
    resultChunk.value shouldBe chunk

  it should "process both channel of a StereoChunk" in:
    val leftValue = 0.5f
    val leftChunk = Vector.fill(sizeChunk)(leftValue)
    val rightValue = -0.5f
    val rightChunk = Vector.fill(sizeChunk)(rightValue)
    val stereoChunk = StereoChunk(leftChunk, rightChunk)
    val volume = 0.2f
    val resultChunk = VolumeEffect(volume)(stereoChunk)
    resultChunk.value shouldBe StereoChunk(Vector.fill(sizeChunk)(0.1f), Vector.fill(sizeChunk)(-0.1f))

  it should "return an AudioErrors if the volume is < 0.0f or volume > 1.0f" in:
    val volume = 2.0f
    val resultChunk = VolumeEffect(volume)(chunk)
    resultChunk.left.value shouldBe a [AudioError.AudioEffectsError]

  // TODO: add test for other effects: distortion, autopan and echo
  // TODO: add test for StereoChunk

end AudioEffectsTest