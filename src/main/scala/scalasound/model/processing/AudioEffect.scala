package scalasound.model.processing

import scalasound.model.domain.AudioChunk
import scalasound.model.domain.AudioChunk.{MonoChunk, StereoChunk}
import scalasound.model.processing.effects.*

/*
 * It is used the covariance so if IdentityEffect <: AudioConfiguration then
 * AudioEffectType[IdentityEffect.type] <: AudioEffectType[AudioConfiguration]
 * It maintains information about the original type and defines the available effects.
 * The name variable defines the name of the effect, while the instance parameter
 * specifies the correlated object containing the desired configuration.
 */
enum AudioEffectType[+T <: AudioConfiguration](
    val name: String,
    private[processing] val instance: T
):
  case Identity   extends AudioEffectType("Identity", IdentityEffect)
  case Gain       extends AudioEffectType("Gain", GainEffect)
  case Distortion extends AudioEffectType("Distortion", DistortionEffect)
  case Echo       extends AudioEffectType("Echo", EchoEffect)
  case Pan        extends AudioEffectType("Pan", PanEffect)
  case Autopan    extends AudioEffectType("Autopan", AutopanEffect)
  case Volume     extends AudioEffectType("Volume", VolumeEffect)

/** Defines a contract that applies a transformation on an audio block.
  */
sealed trait AudioEffect:
  /* Check whether the constraints for a given effect are met. */
  protected def checkConstraints(isValid: Boolean, msg: String): Option[AudioError] =
    if isValid then Option.empty
    else Some(AudioError.AudioEffectsError(this, msg))

/** Defines a contract that applies a transformation on an audio block with no relation to time.
  */
trait StatelessAudioEffect extends AudioEffect:
  def apply(chunk: AudioChunk): Either[AudioError, AudioChunk]

  protected def applyTransformation(chunk: AudioChunk, transformation: Float => Float): AudioChunk =
    chunk match
      case MonoChunk(samples) =>
        MonoChunk(samples.map(transformation))
      case StereoChunk(left, right) =>
        StereoChunk(left.map(transformation), right.map(transformation))

/** Defines a contract that applies a transformation on an audio block, but keeping trace of the
  * passing of time by using the chunk index.
  */
trait TimeAudioEffect extends AudioEffect:
  def apply(chunk: AudioChunk, chunkIndex: Int): Either[AudioError, AudioChunk]

object AudioEffect:

  private val DefaultSampleRate = 44100.0f

  /** Defines the list of available effects.
    *
    * @return
    *   the names of the available effects.
    */
  def effectsNames: List[String] =
    AudioEffectType.values.map(_.name).toList

  extension (effect: AudioEffect)
    /** @return
      *   the expected type for the specific effect.
      */
    def effectType: Option[AudioEffectType[?]] =
      PartialFunction.condOpt(effect):
        case IdentityEffect      => AudioEffectType.Identity
        case _: GainEffect       => AudioEffectType.Gain
        case _: DistortionEffect => AudioEffectType.Distortion
        case _: EchoEffect       => AudioEffectType.Echo
        case _: PanEffect        => AudioEffectType.Pan
        case _: AutopanEffect    => AudioEffectType.Autopan
        case _: VolumeEffect     => AudioEffectType.Volume

    /** Checks when two effects share the same effectType.
      *
      * @param effect2
      *   the second effect.
      * @return
      *   true if the two effects have the same type and false otherwise.
      */
    def hasSameEffectTypeOf(effect2: AudioEffect): Boolean =
      (effect.effectType, effect2.effectType) match
        case (Some(ef1), Some(ef2)) => ef1 == ef2
        case _                      => false

  /** Returns the Identity effect.
    */
  def identity: StatelessAudioEffect =
    IdentityEffect

  /** Returns the Gain effect.
    *
    * @param gainValue
    *   the value of the gain.
    */
  def gain(gainValue: Float): StatelessAudioEffect =
    GainEffect(gainValue)

  /** Returns the Distortion effect.
    *
    * @param distortionValue
    *   the strength of the distortion.
    * @param isSoftClipping
    *   if [[true]] applies a tanh transformation else a sin-based effect.
    */
  def distortion(distortionValue: Float, isSoftClipping: Boolean = true): StatelessAudioEffect =
    DistortionEffect(distortionValue, isSoftClipping)

  /** Returns the Echo effect.
    *
    * @param alpha
    *   is an attenuator for the echo effect.
    * @param delay
    *   expressed in milliseconds defines the delay of the echo effect.
    * @param sampleRate
    *   the sampling rate in Hz.
    */
  def echo(alpha: Float, delay: Int, sampleRate: Float = DefaultSampleRate): TimeAudioEffect =
    EchoEffect(alpha, delay, sampleRate)

  /** Returns the Pan effect.
    *
    * @param panValue
    *   the strength of the pan effect.
    */
  def pan(panValue: Float): StatelessAudioEffect =
    PanEffect(panValue)

  /** Returns the AutoPan effect.
    *
    * @param autopanValue
    *   it's the frequency in Hz from switching left to right.
    * @param sampleRate
    *   it's the frequency of sampling the chunks.
    */
  def autopan(autopanValue: Float, sampleRate: Float = DefaultSampleRate): TimeAudioEffect =
    AutopanEffect(autopanValue, sampleRate)

  /** Returns the Volume effect.
    *
    * @param volumeValue
    *   the strength of the volume.
    */
  def volume(volumeValue: Float): StatelessAudioEffect =
    VolumeEffect(volumeValue)
