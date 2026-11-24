package scalasound.model.processing

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scalasound.model.domain.AudioChunk.{MonoChunk, StereoChunk}
import scalasound.model.domain.AudioChunk
import scalasound.model.processing.AudioEffectType.{Autopan, Distortion, Echo, Gain, Pan, Volume}
import scalasound.model.processing.effects.{AutopanEffect, EchoEffect}

/**
 * Test class containing all the checks for the effects applied on an audio chunk.
 */
final class AudioEffectsTest extends AnyFlatSpec with Matchers with EitherValues:

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
  private val defaultValueChunk: Float = 0.5f
  private val sampleRateStandard = 44100.0f
  private val chunk: AudioChunk = MonoChunk(Vector.fill(sizeChunk)(defaultValueChunk))

  private object newStatelessEffect extends StatelessAudioEffect:
    override def apply(chunk: AudioChunk): Either[AudioError, AudioChunk] = Right(chunk)

  private object newTimeEffect extends TimeAudioEffect:
    override def apply(chunk: AudioChunk, chunkIndex: Int): Either[AudioError, AudioChunk] = Right(chunk)

  "Available effects" should "be the ones contained in the AudioEffectType enum" in:
    AudioEffect.effectsNames.length shouldBe AudioEffectType.values.length

  "Chunk" should "not be empty" in:
    chunk.channels should not be empty
    chunk.channels.head.size shouldEqual sizeChunk

  it should "be a MonoChunk and not a StereoChunk" in:
    chunk shouldBe a [MonoChunk]
    chunk should not be a [StereoChunk]

  "Effects type" should "return the expected AudioEffectType for known effects" in:
    AudioEffect.identity.effectType shouldBe Some(AudioEffectType.Identity)
    AudioEffect.gain(Config.GainConfig.default).effectType shouldBe Some(AudioEffectType.Gain)
    AudioEffect.volume(Config.VolumeConfig.default).effectType shouldBe Some(AudioEffectType.Volume)
    AudioEffect.pan(Config.PanConfig.default).effectType shouldBe Some(AudioEffectType.Pan)
    AudioEffect.autopan(Config.AutopanConfig.default).effectType shouldBe Some(AudioEffectType.Autopan)
    AudioEffect.echo(Config.AlphaConfig.default, Config.DelayConfig.default.toInt).effectType shouldBe Some(AudioEffectType.Echo)
    AudioEffect.distortion(Config.DistortionConfig.default).effectType shouldBe Some(AudioEffectType.Distortion)

  it should "return no type for unregistered effects" in:
    newStatelessEffect.effectType shouldBe Option.empty
    newTimeEffect.effectType shouldBe Option.empty

  it should "return true for effects of the same type even if they have different parameters" +
    "otherwise it should return false because the two effects doesn't have the same type" in:
    val gain1 = AudioEffect.gain(Config.GainConfig.default)
    val gain2 = AudioEffect.gain(Config.GainConfig.max)
    gain1.hasSameEffectTypeOf(gain2) shouldBe true
    val pan = AudioEffect.pan(Config.PanConfig.default)
    gain1.hasSameEffectTypeOf(pan) shouldBe false
    gain1.hasSameEffectTypeOf(newStatelessEffect) shouldBe false
    gain1.hasSameEffectTypeOf(newTimeEffect) shouldBe false

  "IdentityEffect" should "return the same initial chunk" in:
    val resultChunk = AudioEffect.identity(chunk)
    resultChunk.value shouldBe chunk

  "GainEffect" should "return a new chunk where values are clamped in [-1.0,1.0] interval" in:
    val gain = (Config.GainConfig.min + Config.GainConfig.max) / 2
    val resultChunk = AudioEffect.gain(gain)(chunk)
    resultChunk.value shouldBe MonoChunk(Vector.fill(sizeChunk)(defaultValueChunk * gain))

  it should "process on both channel of a StereoChunk" in:
    val gain = (Config.GainConfig.min + Config.GainConfig.max) / 2
    val channel = Vector.fill(sizeChunk)(defaultValueChunk)
    val stereoChunk = StereoChunk(channel, channel)
    val resultGain = math.max(-1.0f, math.min(1.0f, defaultValueChunk * gain))
    val resultChunk = AudioEffect.gain(gain)(stereoChunk)
    val left = resultChunk.value.channels.head
    val right = resultChunk.value.channels(1)
    left shouldBe right
    left shouldBe Vector.fill(sizeChunk)(resultGain)

  it should "return an AudioError if the gain out of bounds" in:
    val invalidGainMax = Config.GainConfig.max + 1.0f
    val resultChunkMax = AudioEffect.gain(invalidGainMax)(chunk)
    resultChunkMax.left.value shouldBe a [AudioError.AudioEffectsError]
    val invalidGainMin = Config.GainConfig.min - 1.0f
    val resultChunkMin = AudioEffect.gain(invalidGainMin)(chunk)
    resultChunkMin.left.value shouldBe a[AudioError.AudioEffectsError]

  "PanEffect" should "return a new StereoChunk" in:
    val pan = (Config.PanConfig.min + Config.PanConfig.max) / 2
    val resultChunk = AudioEffect.pan(pan)(chunk)
    resultChunk.value shouldBe a [StereoChunk]

    val stereoChunk = StereoChunk(
      Vector.fill(sizeChunk)(defaultValueChunk),
      Vector.fill(sizeChunk)(defaultValueChunk)
    )
    val resultStereoChunk = AudioEffect.pan(pan)(stereoChunk)
    resultStereoChunk.value shouldBe a [StereoChunk]

  it should "disable right channel when the min pan value is inserted" in :
    val pan = Config.PanConfig.min
    val resultChunk = AudioEffect.pan(pan)(chunk)
    resultChunk.value.channels(1) shouldBe Vector.fill(sizeChunk)(0.0f)

  it should "return an AudioError if the pan is out of bounds" in:
    val invalidMaxPan = Config.PanConfig.max + 1.0f
    val resultChunkMax = AudioEffect.pan(invalidMaxPan)(chunk)
    resultChunkMax.left.value shouldBe a [AudioError.AudioEffectsError]
    val invalidMinPan = Config.PanConfig.min - 1.0f
    val resultChunkMin = AudioEffect.pan(invalidMinPan)(chunk)
    resultChunkMin.left.value shouldBe a[AudioError.AudioEffectsError]

  "VolumeEffect" should "return the same chunk if the volume is set to its default value" in:
    val resultChunk = AudioEffect.volume(Config.VolumeConfig.default)(chunk)
    resultChunk.value shouldBe chunk
    resultChunk.value shouldBe a [MonoChunk]

  it should "process both channel of a StereoChunk" in:
    val leftValue = defaultValueChunk / 2.0f
    val leftChunk = Vector.fill(sizeChunk)(leftValue)
    val rightValue = -defaultValueChunk / 2.0f
    val rightChunk = Vector.fill(sizeChunk)(rightValue)
    val stereoChunk = StereoChunk(leftChunk, rightChunk)
    val volume = (Config.VolumeConfig.min + Config.VolumeConfig.max) / 2
    val resultChunk = AudioEffect.volume(volume)(stereoChunk)
    resultChunk.value shouldBe StereoChunk(
      Vector.fill(sizeChunk)(leftValue * volume), 
      Vector.fill(sizeChunk)(rightValue * volume))

  it should "return an AudioError if the volume is out of bounds" in:
    val invalidVolumeMax = Config.VolumeConfig.max + 1.0f
    val resultChunkMax = AudioEffect.volume(invalidVolumeMax)(chunk)
    resultChunkMax.left.value shouldBe a [AudioError.AudioEffectsError]
    val invalidVolumeMin = Config.VolumeConfig.min - 1.0f
    val resultChunkMin = AudioEffect.volume(invalidVolumeMin)(chunk)
    resultChunkMin.left.value shouldBe a[AudioError.AudioEffectsError]

  "DistortionEffect" should "return a new AudioChunk with " +
    "the desired effect applied: tanh or sin" in:
    val distortion = (Config.DistortionConfig.min + Config.DistortionConfig.max) / 2

    // Test with Soft Clipping (Tanh)
    val resultChunkTanh = AudioEffect.distortion(distortion)(chunk)
    val resultValueTanh = math.tanh(distortion * defaultValueChunk).toFloat
    resultChunkTanh.value shouldBe MonoChunk(Vector.fill(sizeChunk)(resultValueTanh))

    // Test with Wave Folding (Sin)
    val resultChunkSin = AudioEffect.distortion(distortion, isSoftClipping = false)(chunk)
    val resultValueSin = math.sin(distortion * defaultValueChunk).toFloat
    resultChunkSin.value shouldBe MonoChunk(Vector.fill(sizeChunk)(resultValueSin))

  it should "apply the distortion in both channel on a StereoChunk" in :
    val distortion = (Config.DistortionConfig.min + Config.DistortionConfig.max) / 2
    val channel = Vector.fill(sizeChunk)(defaultValueChunk)
    val stereoChunk = StereoChunk(channel, channel)
    val resultValueTanh = math.tanh(distortion * defaultValueChunk).toFloat
    val resultChunk = AudioEffect.distortion(distortion)(stereoChunk)
    val left = resultChunk.value.channels.head
    val right = resultChunk.value.channels(1)
    left shouldBe right
    left shouldBe Vector.fill(sizeChunk)(resultValueTanh)

  it should "return an AudioError if the distortion is out of bounds" in:
    val invalidDistortionMax = Config.DistortionConfig.max + 3.0f
    val resultChunkMax = AudioEffect.distortion(invalidDistortionMax)(chunk)
    resultChunkMax.left.value shouldBe a [AudioError.AudioEffectsError]

    val invalidDistortionMin = Config.DistortionConfig.min - 3.0f
    val resultChunkMin = AudioEffect.distortion(invalidDistortionMin)(chunk)
    resultChunkMin.left.value shouldBe a[AudioError.AudioEffectsError]

  "AutopanEffect" should "return a new StereoChunk where " +
    "for each sample is multiplied by a different factor" in:
    val frequency = (Config.AutopanConfig.min + Config.AutopanConfig.max) / 2
    val resultChunk = AutopanEffect(frequency, sampleRateStandard)(chunk, chunkIndex = 0)
    resultChunk.value shouldBe a [StereoChunk]

  it should "return an AudioError if the frequency is out of bounds" in:
    val frequency = Config.AutopanConfig.min - 1
    val resultChunk = AutopanEffect(frequency, sampleRateStandard)(chunk, chunkIndex = 0)
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

    val invalidSampleRate = 0.0f
    val resultChunkSampleRate = EchoEffect(alphaStandard, delayStandard, invalidSampleRate)(chunk, chunkIndex = 0)
    resultChunkSampleRate.left.value shouldBe a[AudioError.AudioEffectsError]
    
  it should "apply the Infinite Impulse Response (IIR) in the correct way" in:
    // Since the size of the defaultChunk is 10, to guarantee a correct verification
    // of the IIR approach it used a sample rate of 1000Hz and a delay of 10ms.
    // So, for 10 samples is necessary a 10ms delay.
    // Nsamples delayed = (delay / 1000ms) * sampleRate
    val sampleRate = 1000.0f //Hz
    val delay = 10 //ms
    val alpha = (Config.AlphaConfig.min + Config.AlphaConfig.max) / 2

    val echoEffect = EchoEffect(alpha, delay, sampleRate)
    val firstChunk = echoEffect(chunk, chunkIndex = 0)
    firstChunk.value.channels shouldBe chunk.channels

    val secondChunk = echoEffect(chunk, chunkIndex = 1)
    // Formula for the IIR application, the echo effect.
    // defaultValueChunk + (alpha * defaultValueChunk) because:
    // 1) The first defaultValueChunk refers to the values contained in the secondChunk,
    // the same for the default chunk;
    // 2) The alpha determines the strength of the echo;
    // 3) The second defaultValueChunk refers to the values contained in the firstChunk,
    // because it was instantiated in the same way as the default chunk.
    val expectedSampleValue = defaultValueChunk + (alpha * defaultValueChunk)
    secondChunk.value.channels.head shouldBe Vector.fill(sizeChunk)(expectedSampleValue)

  it should "apply the Infinite Impulse Response (IIR) to a StereoChunk" in:
    val sampleRate = 100.0f //Hz
    val delay = 20 //ms
    val alpha = (Config.AlphaConfig.min + Config.AlphaConfig.max) / 2
    val nSamples = ((delay / 1000.0f) * sampleRate).toInt
    val leftValue = defaultValueChunk
    val rightValue = defaultValueChunk / 2
    val stereoChunk = StereoChunk(
      Vector.fill(nSamples)(leftValue),
      Vector.fill(nSamples)(rightValue)
    )

    val echoEffect = EchoEffect(alpha, delay, sampleRate)
    val firstChunk = echoEffect(stereoChunk, chunkIndex = 0)
    firstChunk.value.channels shouldBe stereoChunk.channels

    val secondChunk = echoEffect(stereoChunk, chunkIndex = 1)
    val expectedLeftValue = leftValue + (alpha * leftValue)
    val expectedRightValue = rightValue + (alpha * rightValue)
    secondChunk.value.channels shouldBe Vector(
      Vector.fill(nSamples)(expectedLeftValue),
      Vector.fill(nSamples)(expectedRightValue)
    )