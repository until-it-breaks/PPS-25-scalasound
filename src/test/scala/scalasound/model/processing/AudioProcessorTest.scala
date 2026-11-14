package scalasound.model.processing

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scalasound.model.domain.{AudioChunk, AudioTrack, MonoTrack, StereoTrack}
import scalasound.model.domain.AudioChunk.{MonoChunk, StereoChunk}
import scalasound.model.processing.AudioEffectType.{Autopan, Gain, Pan, Volume}

/**
 * Test class containing all the checks for the different audio processors
 * by verifying the behavior in different situations.
 */
final class AudioProcessorTest extends AnyFlatSpec with Matchers with EitherValues:

  private val SizeChunk: Int = 10
  private val SizeTrack: Int = 3
  private val SampleRate = 44100
  private val StartingIndex: Int = 0
  private val InitialValue: Float = 0.2f

  private val gainConfig = AudioConfiguration.parameterConfiguration(Gain).gainConfiguration
  private val volumeConfig = AudioConfiguration.parameterConfiguration(Volume).volumeConfiguration
  private val panConfig = AudioConfiguration.parameterConfiguration(Pan).panConfiguration

  private val gain = GainEffect(gainConfig.default)
  private val volume = VolumeEffect(volumeConfig.default)
  private val pan = PanEffect(panConfig.default)

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

  /*
   * New type of effect added to check how the processor
   * adapts with new filters unknown, so not already in
   * the known effect list.
   */
  private case object NewEffect extends AudioEffect:
    override def apply(chunk: AudioChunk): Either[AudioError, AudioChunk] =
      chunk match
        case MonoChunk(samples) =>
          Right(MonoChunk(samples.map(v => -v)))
        case StereoChunk(left, right) =>
          Right(StereoChunk(left.map(v => -v), right.map(v => -v)))

  "AudioProcessor" should "identify the emptiness of a chunk" in:
    val emptyChunk: AudioChunk = MonoChunk(Vector.empty)
    val resultChunk = SimpleAudioProcessor(emptyChunk, simpleEffectsList, StartingIndex)
    resultChunk.left.value shouldBe a [AudioError.AudioProcessorError]

  it should "return an AudioError when the track is empty" in:
    val emptyTrack = MonoTrack(SampleRate, Vector.empty)
    val resultTrack = SimpleAudioProcessor(emptyTrack, simpleEffectsList)
    resultTrack.left.value shouldBe a [AudioError.AudioProcessorError]

  it should "identify if an error is present in the effects" in:
    val invalidValue = AudioConfiguration.parameterConfiguration(Gain).gainConfiguration.min - 1.0f
    val invalidGain = GainEffect(invalidValue)
    val invalidEffectsList = List(invalidGain, volume, pan)
    val resultChunk = SimpleAudioProcessor(chunk, invalidEffectsList, StartingIndex)
    resultChunk.left.value shouldBe a [AudioError]

  it should "identify the lack of the effects" in:
    val emptyEffects: List[AudioEffect] = List.empty
    val resultChunk = SimpleAudioProcessor(chunk, emptyEffects, StartingIndex)
    resultChunk.value shouldBe chunk

  "SimpleAudioProcessor" should "apply the effects in the same order " +
    "in which they were added to the list" in:
    val resultChunk = SimpleAudioProcessor(chunk, simpleEffectsList, StartingIndex)
    resultChunk.value shouldBe pipeline(simpleEffectsList, chunk, StartingIndex).value
    // pipeline.value shouldBe PanEffect()(VolumeEffect()(GainEffect()(chunk).value).value).value

  it should "apply the effects to all the chunks of the track" in:
    val track = createTrack
    val resultTrack = SimpleAudioProcessor(track, simpleEffectsList)
    val pipe = pipeline(simpleEffectsList, chunk, StartingIndex)
    resultTrack.value.getVectors.forall(c => c == pipe.value) shouldBe true

  it should "pass incremental index when a TimeAudioEffect is in the effects' list" in:
    val autopanConfig = AudioConfiguration.parameterConfiguration(Autopan).autopanConfiguration
    val frequency = (autopanConfig.min + autopanConfig.max) / 2
    val autopan = AutopanEffect(frequency, SampleRate.toFloat)
    val trackVectors = SimpleAudioProcessor(createTrack, List(autopan)).value.getVectors

    // The first chunk should be equal to the default chunk with autopan applied.
    trackVectors.head shouldBe autopan(chunk, chunkIndex = 0).value
    // The second chunk should be equal to the default chunk with autopan applied, but
    // with the index of the chunk incremented by 1.
    trackVectors(1) shouldBe autopan(chunk, chunkIndex = 1).value
    // The two final chunks are different to each others.
    trackVectors.head should not be trackVectors(1)

  it should "convert the MonoTrack in StereoTrack when an effect produces StereoChunks" in:
    val track = createTrack
    val resultTrack = SimpleAudioProcessor(track, List(pan))
    resultTrack.value shouldBe a [StereoTrack]
    val resultTrackLonger = SimpleAudioProcessor(track, simpleEffectsList)
    resultTrackLonger.value shouldBe a [StereoTrack]

  "AudioProcessorWithRules" should "apply the effects in a specific order: [Gain, Pan, Volume]" in:
    val resultChunk = AudioProcessorWithRules(chunk, simpleEffectsList, StartingIndex)
    resultChunk.value shouldBe pipeline(orderedEffects, chunk, StartingIndex).value
    // pipeline.value shouldBe VolumeEffect()(PanEffect()(GainEffect()(chunk).value).value).value

  it should "apply the effects in a specific order for all the chunks inside the track" in:
    val resultTrack = AudioProcessorWithRules(createTrack, simpleEffectsList)
    val pipe = pipeline(orderedEffects, chunk, StartingIndex)
    resultTrack.value.getVectors.forall(c => c == pipe.value) shouldBe true

  it should "enforce priority on the effects even if a new effect previously " +
    "unknown is passed to the processor" in:
    val unorderedList = List(gain, NewEffect, volume, pan)
    val orderedList = List(gain, pan, volume, NewEffect)
    val resultChunk = AudioProcessorWithRules(chunk, unorderedList, StartingIndex)
    resultChunk.value shouldBe pipeline(orderedList, chunk, StartingIndex).value
