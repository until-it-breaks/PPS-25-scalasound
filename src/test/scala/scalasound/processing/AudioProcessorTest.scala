package scalasound.processing

import org.scalatest.{BeforeAndAfter, EitherValues}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scalasound.domain.{AudioChunk, AudioTrack, MonoTrack}
import scalasound.domain.AudioChunk.MonoChunk

// TODO: Complete when all the effects are completed and add test for process track too.
class AudioProcessorTest extends AnyFlatSpec with Matchers with BeforeAndAfter with EitherValues:

  val sizeChunk: Int = 10
  val sizeTrack: Int = 3
  val sampleRate = 44100
  val initialValue: Float = 0.2f
  val effects: List[AudioEffect] = List(GainEffect(), VolumeEffect(), PanEffect())
  var chunk: AudioChunk = MonoChunk(Vector.fill(sizeChunk)(initialValue))

  private def pipeline(effects: List[AudioEffect], chunk: AudioChunk): Either[AudioError, AudioChunk] =
    effects.foldLeft[Either[AudioError, AudioChunk]](Right(chunk)){
      (currentChunk, effect) => currentChunk.flatMap(chunky => effect(chunky))
    }

  private def createTrack: AudioTrack =
    MonoTrack(sampleRate, Vector.fill(sizeTrack)(MonoChunk(chunk.getVectors.head)))

  "AudioProcessor" should "identify the emptiness of a chunk" in:
    val emptyChunk: AudioChunk = MonoChunk(Vector.empty)
    val resultChunk = SimpleAudioProcessor(emptyChunk, effects)
    resultChunk.left.value shouldBe a [AudioError.AudioProcessorError]

  it should "identify the lack of the effects" in:
    val emptyEffects: List[AudioEffect] = List.empty
    val resultChunk = SimpleAudioProcessor(chunk, emptyEffects)
    resultChunk.value shouldBe chunk

  "SimpleAudioProcessor" should "apply the effects in the same order " +
    "in which they were added to the list" in:
    val resultChunk = SimpleAudioProcessor(chunk, effects)
    resultChunk.value shouldBe pipeline(effects, chunk).value
    // pipeline.value shouldBe PanEffect()(VolumeEffect()(GainEffect()(chunk).value).value).value

  "AudioProcessorWithRules" should "apply the effects in a specific order: [Gain, Pan, Volume]" in:
    val resultChunk = AudioProcessorWithRules(chunk, effects)
    val orderedEffects = List(GainEffect(), PanEffect(), VolumeEffect())
    resultChunk.value shouldBe pipeline(orderedEffects, chunk).value
    // pipeline.value shouldBe VolumeEffect()(PanEffect()(GainEffect()(chunk).value).value).value

  "SimpleAudioProcessor" should "apply the effects to all the chunks of the track" in:
    val track = createTrack
    val resultTrack = SimpleAudioProcessor(track, effects)
    val pipe = pipeline(effects, chunk)
    resultTrack.value.getVectors.forall(c => c == pipe.value)

  "AudioProcessorWithRules" should "apply the effects in a specific order: [Gain, Pan, Volume] " +
    "for all the chunk inside the track" in:
    val track = createTrack
    val resultTrack = AudioProcessorWithRules(track, effects)
    val orderedEffects = List(GainEffect(), PanEffect(), VolumeEffect())
    val pipe = pipeline(orderedEffects, chunk)
    resultTrack.value.getVectors.forall(c => c == pipe.value)

end AudioProcessorTest

