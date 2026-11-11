package scalasound.model.processing

import org.scalatest.{BeforeAndAfter, EitherValues}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scalasound.model.domain.AudioChunk.{MonoChunk, StereoChunk}
import scalasound.model.domain.AudioChunk
import scalasound.model.processing.AudioEffectType.{Autopan, Distortion, Echo, Gain, Identity, Pan, Volume}
import scalasound.model.processing.{AudioEffect, AudioError, AutopanEffect, DistortionEffect, EchoEffect, GainEffect, IdentityEffect, PanEffect, VolumeEffect}

import scala.util.Random

/**
 * Test class containing all the checks for the effects applied on an audio chunk.
 */
class AudioEffectsTest extends AnyFlatSpec with Matchers with EitherValues:

  private enum Config(parameters: ParameterConfiguration):
    def min: Float = parameters.min
    def max: Float = parameters.max
    def default: Float = parameters.default
    
    case GainConfig extends Config(AudioConfiguration.parameterConfiguration(Gain).gainConfiguration)
    case DistortionConfig extends Config(AudioConfiguration.parameterConfiguration(Distortion).distortionConfiguration)
    case DelayConfig extends Config(AudioConfiguration.parameterConfiguration(Echo).delayConfiguration)
    case AlphaConfig extends Config(AudioConfiguration.parameterConfiguration(Echo).alphaConfiguration)
    case PanConfig extends Config(AudioConfiguration.parameterConfiguration(Pan).panConfiguration)
    case AutopanConfig extends Config(AudioConfiguration.parameterConfiguration(Autopan).autopanConfiguration)
    case VolumeConfig extends Config(AudioConfiguration.parameterConfiguration(Volume).volumeConfiguration)
  
  private val sizeChunk: Int = 10
  private val minValueChunk: Float = -1.0f
  private val defaultValueChunk: Float = 0.5f
  private val maxValueChunk: Float = 1.0f
  private val sampleRateStandard = 44100
  private val chunk: AudioChunk = MonoChunk(Vector.fill(sizeChunk)(defaultValueChunk)) //MonoChunk(Vector.empty)

  "Chunk" should "not be empty" in:
    chunk.getVectors should not be empty
    chunk.getVectors.head.size shouldEqual sizeChunk

  it should "be a MonoChunk and not a StereoChunk" in:
    chunk shouldBe a [MonoChunk]
    chunk should not be a [StereoChunk]

  "IdentityEffect" should "return the same initial chunk" in:
    val resultChunk = IdentityEffect(chunk)
    resultChunk.value shouldBe chunk

  "GainEffect" should "return a new chunk where values are clamped in [-1.0,1.0] interval" in:
    val gain = Random().between(Config.GainConfig.min, Config.GainConfig.max)
    val resultChunk = GainEffect(gain)(chunk)
    resultChunk.value shouldBe MonoChunk(Vector.fill(sizeChunk)(defaultValueChunk * gain))

  it should "return an AudioError if the gain out of bounds" in:
    val invalidGainMax = Config.GainConfig.max + 1.0f
    val resultChunkMax = GainEffect(invalidGainMax)(chunk)
    resultChunkMax.left.value shouldBe a [AudioError.AudioEffectsError]
    val invalidGainMin = Config.GainConfig.min - 1.0f
    val resultChunkMin = GainEffect(invalidGainMin)(chunk)
    resultChunkMin.left.value shouldBe a[AudioError.AudioEffectsError]

  "PanEffect" should "return a new StereoChunk" in:
    val pan = Random().between(Config.PanConfig.min, Config.PanConfig.max)
    val resultChunk = PanEffect(pan)(chunk)
    resultChunk.value shouldBe a [StereoChunk]

  "PanEffect" should "disable right channel when the min pan value is inserted" in :
    val pan = Config.PanConfig.min
    val resultChunk = PanEffect(pan)(chunk)
    resultChunk.value.getVectors(1) shouldBe Vector.fill(sizeChunk)(0.0f)

  it should "return an AudioError if the pan is out of bounds" in:
    val invalidMaxPan = Config.PanConfig.max + 1.0f
    val resultChunkMax = PanEffect(invalidMaxPan)(chunk)
    resultChunkMax.left.value shouldBe a [AudioError.AudioEffectsError]
    val invalidMinPan = Config.PanConfig.min - 1.0f
    val resultChunkMin = PanEffect(invalidMinPan)(chunk)
    resultChunkMin.left.value shouldBe a[AudioError.AudioEffectsError]

  "VolumeEffect" should "return the same chunk if the volume is set to its default value" in:
    val resultChunk = VolumeEffect(Config.VolumeConfig.default)(chunk)
    resultChunk.value shouldBe chunk

  it should "process both channel of a StereoChunk" in:
    val leftValue = defaultValueChunk / 2.0f
    val leftChunk = Vector.fill(sizeChunk)(leftValue)
    val rightValue = -defaultValueChunk / 2.0f
    val rightChunk = Vector.fill(sizeChunk)(rightValue)
    val stereoChunk = StereoChunk(leftChunk, rightChunk)
    val volume = Random().between(Config.VolumeConfig.min, Config.VolumeConfig.max)
    val resultChunk = VolumeEffect(volume)(stereoChunk)
    resultChunk.value shouldBe StereoChunk(
      Vector.fill(sizeChunk)(leftValue * volume), 
      Vector.fill(sizeChunk)(rightValue * volume))

  it should "return an AudioError if the volume is out of bounds" in:
    val invalidVolumeMax = Config.VolumeConfig.max + 1.0f
    val resultChunkMax = VolumeEffect(invalidVolumeMax)(chunk)
    resultChunkMax.left.value shouldBe a [AudioError.AudioEffectsError]
    val invalidVolumeMin = Config.VolumeConfig.min - 1.0f
    val resultChunkMin = VolumeEffect(invalidVolumeMin)(chunk)
    resultChunkMin.left.value shouldBe a[AudioError.AudioEffectsError]

  "DistortionEffect" should "return a new AudioChunk with " +
    "the desired effect applied: tanh or sin" in:
    val distortion = Random().between(Config.DistortionConfig.min, Config.DistortionConfig.max)

    // Test with Soft Clipping (Tanh)
    val resultChunkTanh = DistortionEffect(distortion, isSoftClipping = true)(chunk)
    val resultValueTanh = math.tanh(distortion * defaultValueChunk).toFloat
    resultChunkTanh.value shouldBe MonoChunk(Vector.fill(sizeChunk)(resultValueTanh))

    // Test with Wave Folding (Sin)
    val resultChunkSin = DistortionEffect(distortion, isSoftClipping = false)(chunk)
    val resultValueSin = math.sin(distortion * defaultValueChunk).toFloat
    resultChunkSin.value shouldBe MonoChunk(Vector.fill(sizeChunk)(resultValueSin))

  it should "return an AudioError if the distortion is out of bounds" in:
    val invalidDistortionMax = Config.DistortionConfig.max + 3.0f
    val resultChunkMax = DistortionEffect(invalidDistortionMax, isSoftClipping = true)(chunk)
    resultChunkMax.left.value shouldBe a [AudioError.AudioEffectsError]

    val invalidDistortionMin = Config.DistortionConfig.min - 3.0f
    val resultChunkMin = DistortionEffect(invalidDistortionMin, isSoftClipping = true)(chunk)
    resultChunkMin.left.value shouldBe a[AudioError.AudioEffectsError]

  "AutopanEffect" should "return a new StereoChunk where " +
    "for each sample is multiplied by a different factor" in:
    val frequency = Random().between(Config.AutopanConfig.min, Config.AutopanConfig.max)
    val resultChunk = AutopanEffect(frequency, sampleRateStandard)(chunk, chunkIndex = 0)
    resultChunk.value shouldBe a [StereoChunk]

  it should "return an AudioError if the frequency is out of bounds" in:
    val frequency = Config.AutopanConfig.min - 1
    val resultChunk = AutopanEffect(frequency, sampleRateStandard)(chunk, chunkIndex = 0)
    resultChunk.left.value shouldBe a [AudioError.AudioEffectsError]

  it should "return an AudioError if no chunk index is specified" in:
    val frequency = Random().between(Config.AutopanConfig.min, Config.AutopanConfig.max)
    val resultChunk = AutopanEffect(frequency, sampleRateStandard)(chunk)
    resultChunk.left.value shouldBe a [AudioError.AudioEffectsError]

  "EchoEffect" should "return an AudioError when the " +
    "input parameters are not in the admissible ranges" in:
    val alphaStandard = Config.AlphaConfig.default
    val delayStandard = Config.DelayConfig.default.toInt
    val alphaOut = Config.AlphaConfig.max * 2.0f
    val resultChunkAlpha = EchoEffect(alphaOut,
      delayStandard,
      sampleRateStandard)(chunk, chunkIndex = 0)
    resultChunkAlpha.left.value shouldBe a[AudioError.AudioEffectsError]

    val delayOut = Config.DelayConfig.max.toInt * 100
    val resultChunkDelay = EchoEffect(alphaStandard,
      delayOut,
      sampleRateStandard)(chunk, chunkIndex = 0)
    resultChunkDelay.left.value shouldBe a[AudioError.AudioEffectsError]

    val invalidSampleRate = 0
    val resultChunkSampleRate = EchoEffect(alphaStandard, delayStandard, invalidSampleRate)(chunk, chunkIndex = 0)
    resultChunkSampleRate.left.value shouldBe a[AudioError.AudioEffectsError]
    
  it should "apply the Infinite Impulse Response (IIR) in the correct way" in:
    // Since the size of the defaultChunk is 10, to guarantee a correct verification
    // of the IIR approach it used a sample rate of 1000Hz and a delay of 10ms.
    // So, for 10 samples is necessary a 10ms delay.
    val sampleRate = 1000 //Hz
    val delay = 10 //ms
    val alpha = Random().between(Config.AlphaConfig.min, Config.AlphaConfig.max)

    val echoEffect = EchoEffect(alpha, delay, sampleRate)
    val firstChunk = echoEffect(chunk, chunkIndex = 0)
    firstChunk.value.getVectors shouldBe chunk.getVectors

    val secondChunk = echoEffect(chunk, chunkIndex = 1)
    // Formula for the IIR application, the echo effect.
    // defaultValueChunk + (alpha * defaultValueChunk) because:
    // 1) The first defaultValueChunk refers to the values contained in the secondChunk,
    // the same for the default chunk;
    // 2) The alpha determines the strength of the echo;
    // 3) The second defaultValueChunk refers to the values contained in the firstChunk,
    // because it was instantiated in the same way as the default chunk.
    val expectedSampleValue = defaultValueChunk + (alpha * defaultValueChunk)
    secondChunk.value.getVectors.head shouldBe Vector.fill(sizeChunk)(expectedSampleValue)
