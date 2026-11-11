package scalasound.model.processing

import org.scalatest.{BeforeAndAfter, EitherValues}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scalasound.model.domain.{AudioChunk, AudioTrack, MonoTrack}
import scalasound.model.domain.AudioChunk.MonoChunk
import scalasound.model.processing.AudioEffectType.{Gain, Pan, Volume}
import scalasound.model.processing.{AudioEffect, AudioError, AudioProcessorWithRules, GainEffect, PanEffect, SimpleAudioProcessor, VolumeEffect}

// TODO: Potenziare i tests.
class AudioProcessorTest extends AnyFlatSpec with Matchers with BeforeAndAfter with EitherValues:

  private val SizeChunk: Int = 10
  private val SizeTrack: Int = 3
  private val SampleRate = 44100
  private val StartingIndex: Int = 0
  private val InitialValue: Float = 0.2f

  private val gain = GainEffect(AudioConfiguration.parameterConfiguration(Gain).gainConfiguration.default)
  private val volume = VolumeEffect(AudioConfiguration.parameterConfiguration(Volume).volumeConfiguration.default)
  private val pan = PanEffect(AudioConfiguration.parameterConfiguration(Pan).panConfiguration.default)
  private val simpleEffectsList: List[AudioEffect] = List(gain, volume, pan)
  private val orderedEffects = List(gain, pan, volume)
  private val chunk: AudioChunk = MonoChunk(Vector.fill(SizeChunk)(InitialValue))

  private def pipeline(effects: List[AudioEffect], chunk: AudioChunk, chunkIndex: Int = 0): Either[AudioError, AudioChunk] =
    effects.foldLeft[Either[AudioError, AudioChunk]](Right(chunk)):
      (currentChunk, effect) =>
        currentChunk.flatMap:
          chunky => effect match
            // Checks if the effect is type TimeAudioEffect since this type
            // of effects need the chunk index to compute the final result.
            case timeEffect: TimeAudioEffect => timeEffect(chunky, chunkIndex)
            case standardEffect: AudioEffect => standardEffect(chunky)

  private def createTrack: AudioTrack =
    MonoTrack(SampleRate, Vector.fill(SizeTrack)(MonoChunk(chunk.getVectors.head)))

  "AudioProcessor" should "identify the emptiness of a chunk" in:
    val emptyChunk: AudioChunk = MonoChunk(Vector.empty)
    val resultChunk = SimpleAudioProcessor(emptyChunk, simpleEffectsList, StartingIndex)
    resultChunk.left.value shouldBe a [AudioError.AudioProcessorError]

  it should "identify the lack of the effects" in:
    val emptyEffects: List[AudioEffect] = List.empty
    val resultChunk = SimpleAudioProcessor(chunk, emptyEffects, StartingIndex)
    resultChunk.value shouldBe chunk

  "SimpleAudioProcessor" should "apply the effects in the same order " +
    "in which they were added to the list" in:
    val resultChunk = SimpleAudioProcessor(chunk, simpleEffectsList, StartingIndex)
    resultChunk.value shouldBe pipeline(simpleEffectsList, chunk, StartingIndex).value
    // pipeline.value shouldBe PanEffect()(VolumeEffect()(GainEffect()(chunk).value).value).value

  "AudioProcessorWithRules" should "apply the effects in a specific order: [Gain, Pan, Volume]" in:
    val resultChunk = AudioProcessorWithRules(chunk, simpleEffectsList, StartingIndex)
    resultChunk.value shouldBe pipeline(orderedEffects, chunk, StartingIndex).value
    // pipeline.value shouldBe VolumeEffect()(PanEffect()(GainEffect()(chunk).value).value).value

  "SimpleAudioProcessor" should "apply the effects to all the chunks of the track" in:
    val track = createTrack
    val resultTrack = SimpleAudioProcessor(track, simpleEffectsList)
    val pipe = pipeline(simpleEffectsList, chunk, StartingIndex)
    resultTrack.value.getVectors.forall(c => c == pipe.value) shouldBe true

  "AudioProcessorWithRules" should "apply the effects in a specific order: [Gain, Pan, Volume] " +
    "for all the chunk inside the track" in:
    val track = createTrack
    val resultTrack = AudioProcessorWithRules(track, simpleEffectsList)
    val pipe = pipeline(orderedEffects, chunk, StartingIndex)
    resultTrack.value.getVectors.forall(c => c == pipe.value) shouldBe true

