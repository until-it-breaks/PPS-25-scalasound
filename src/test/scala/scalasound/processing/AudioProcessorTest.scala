package scalasound.processing

import org.scalatest.{BeforeAndAfter, EitherValues}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scalasound.domain.AudioChunk
import scalasound.domain.AudioChunk.MonoChunk

// TODO: Complete when all the effects are completed.
class AudioProcessorTest extends AnyFlatSpec with Matchers with BeforeAndAfter with EitherValues:

  val sizeChunk: Int = 10
  val initialValue: Float = 0.2f
  val effects: List[AudioEffect] = List(GainEffect(), VolumeEffect(), PanEffect())
  var chunk: AudioChunk = MonoChunk(Vector.empty)

  before {
    chunk = MonoChunk(Vector.fill(sizeChunk)(initialValue))
  }

  "AudioProcessor" should "identify the emptiness of a chunk" in {
    val emptyChunk: AudioChunk = MonoChunk(Vector.empty)
    val resultChunk = IdentityAudioProcessor(emptyChunk, effects)
    resultChunk.left.value shouldBe a [AudioError.AudioProcessorError]
  }

  it should "identify the lack of the effects" in {
    val emptyEffects: List[AudioEffect] = List.empty
    val resultChunk = IdentityAudioProcessor(chunk, emptyEffects)
    resultChunk.value shouldBe chunk
  }

  "IdentityAudioProcessor" should "apply the effects in the same order " +
    "in which they were added to the list" in {
    val resultChunk = IdentityAudioProcessor(chunk, effects)
    val pipeline = effects.foldLeft[Either[AudioError, AudioChunk]](Right(chunk)){
      (currentChunk, effect) => currentChunk.flatMap(chunky => effect(chunky))
    }
    resultChunk.value shouldBe pipeline.value
    // pipeline.value shouldBe PanEffect()(VolumeEffect()(GainEffect()(chunk).value).value).value
  }

  "AudioProcessorWithRules" should "apply the effects in a specific order: [Gain, Pan, Volume]" in {
    val resultChunk = AudioProcessorWithRules(chunk, effects)
    val orderedEffects = List(GainEffect(), PanEffect(), VolumeEffect())
    val pipeline = orderedEffects.foldLeft[Either[AudioError, AudioChunk]](Right(chunk)) {
      (currentChunk, effect) => currentChunk.flatMap(chunky => effect(chunky))
    }
    resultChunk.value shouldBe pipeline.value
    // pipeline.value shouldBe VolumeEffect()(PanEffect()(GainEffect()(chunk).value).value).value
  }

end AudioProcessorTest

