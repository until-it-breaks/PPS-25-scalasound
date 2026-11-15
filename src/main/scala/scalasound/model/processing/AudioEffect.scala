package scalasound.model.processing

import scalasound.model.domain.AudioChunk
import scalasound.model.processing.AudioError.AudioEffectsError

/**
 * Defines the available effects.
 * @param name the name of the effect in String.
 * @param instance the correlated object containing the configuration.
 */
/*
 * It is used the covariance so if IdentityEffect <: AudioEffect then
 * AudioEffectType[IdentityEffect.type] <: AudioEffectType[AudioConfiguration]
 * It maintains information about the original type.
 */
enum AudioEffectType[+T <: AudioConfiguration](val name: String, val instance: T):
  case Identity   extends AudioEffectType("Identity",   IdentityEffect)
  case Gain       extends AudioEffectType("Gain",       GainEffect)
  case Distortion extends AudioEffectType("Distortion", DistortionEffect)
  case Echo       extends AudioEffectType("Echo",       EchoEffect)
  case Pan        extends AudioEffectType("Pan",        PanEffect)
  case Autopan    extends AudioEffectType("Autopan",    AutopanEffect)
  case Volume     extends AudioEffectType("Volume",     VolumeEffect)

/**
 * Defines a contract that applies a transformation on an audio block.
 */
trait AudioEffect:
  def apply(chunk: AudioChunk): Either[AudioError, AudioChunk]
  
  /* Check whether the constraints for a given effect are met. */
  protected def checkConstraints(isValid: Boolean, msg: String): Option[AudioError] =
    if isValid then Option.empty
    else Some(AudioError.AudioEffectsError(this, msg))

/**
 * Defines a contract that applies a transformation on an audio block,
 * but keeping trace of the passing of time by using the chunk index.
 */
trait TimeAudioEffect extends AudioEffect:
  def apply(chunk: AudioChunk, chunkIndex: Int): Either[AudioError, AudioChunk]

  /* If the index is not present then every chunk is the first one. */
  final override def apply(chunk: AudioChunk): Either[AudioError, AudioChunk] =
    Left(AudioEffectsError(this, "An index is needed!"))

object AudioEffect:

  /**
   * Defines the list of available effects.
   * @return the names of the available effects.
   */
  def effectsNames: List[String] =
    AudioEffectType.values.map(_.name).toList
    /*
      this.getClass.
        getMethods.
        filter(_.getReturnType == classOf[AudioEffect]).
        map(_.getName).
        toList
     */

  /**
   * Returns the expected type for the specific effect.
   */
  extension (effect: AudioEffect)
    /*
     * Generalizes the output because it doesn't matter
     * what type effect is now. So the wildcard ? is used.
     */
    def effectType: AudioEffectType[?] = effect match
      case IdentityEffect => AudioEffectType.Identity
      case _: GainEffect => AudioEffectType.Gain
      case _: DistortionEffect => AudioEffectType.Distortion
      case _: EchoEffect => AudioEffectType.Echo
      case _: PanEffect => AudioEffectType.Pan
      case _: AutopanEffect => AudioEffectType.Autopan
      case _: VolumeEffect => AudioEffectType.Volume

  /**
   * Checks when two effects share the same effectType.
   * @param effect1 the first effect.
   * @param effect2 the second effect.
   * @return true if the two effects have the same type and false otherwise.
   */
  def checkEqualEffectType(effect1: AudioEffect, effect2: AudioEffect): Boolean =
    effect1.effectType == effect2.effectType
    
  /**
   * Returns the Identity effect.
   */
  def identity: AudioEffect =
    IdentityEffect

  /**
   * Returns the Gain effect.
   * @param gainValue the value of the gain.
   */
  def gain(gainValue: Float): AudioEffect =
    GainEffect(gainValue)

  /**
   * Returns the Distortion effect.
   * @param distortionValue the strength of the distortion.
   * @param isSoftClipping if [[true]] applies a tanh transformation else a sin-based effect.
   */
  def distortion(distortionValue: Float, isSoftClipping: Boolean = true): AudioEffect =
    DistortionEffect(distortionValue, isSoftClipping)

  /**
   * Returns the Echo effect.
   * @param alpha is an attenuator for the echo effect.
   * @param delay expressed in milliseconds defines the delay of the echo effect.
   * @param sampleRate the sampling rate in Hz.
   */
  def echo(alpha: Float, delay: Int, sampleRate: Float): AudioEffect =
    EchoEffect(alpha, delay, sampleRate)

  /**
   * Returns the Pan effect.
   * @param panValue the strength of the pan effect.
   */
  def pan(panValue: Float): AudioEffect =
    PanEffect(panValue)

  /**
   * Returns the AutoPan effect.
   * @param autopanValue it's the frequency in Hz from switching left to right.
   * @param sampleRate it's the frequency of sampling the chunks.
   */
  def autopan(autopanValue: Float, sampleRate: Float): AudioEffect =
    AutopanEffect(autopanValue, sampleRate)

  /**
   * Returns the Volume effect.
   * @param volumeValue the strength of the volume.
   */
  def volume(volumeValue: Float): AudioEffect =
    VolumeEffect(volumeValue)