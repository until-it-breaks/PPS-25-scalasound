package scalasound.model.processing

import scalasound.model.domain.AudioChunk

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
    apply(chunk, 0)


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
   * Returns the Identity effect.
   */
  def identity: AudioEffect =
    IdentityEffect

  /**
   * Returns the Gain effect.
   * @param gainValue if the value is not present the Gain effect will be
   *                  applied with the default value.
   */
  def gain(gainValue: Option[Float]): AudioEffect =
    gainValue.fold(GainEffect())(GainEffect(_))

  /**
   * Returns the Distortion effect.
   * @param distortionValue if the value is not present the Distortion effect will be
   *                        applied with the default value.
   * @param isSoftClipping if [[true]] applies a tanh transformation else a sin-based effect.
   */
  def distortion(distortionValue: Option[Float], isSoftClipping: Option[Boolean]): AudioEffect =
    val distortion = DistortionEffect()
    DistortionEffect(
      distortion = distortionValue.getOrElse(distortion.distortion),
      isSoftClipping = isSoftClipping.getOrElse(distortion.isSoftClipping)
    )

  /**
   * Returns the Echo effect.
   * @param alpha is an attenuator for the echo effect.
   * @param delay expressed in milliseconds defines the delay of the echo effect.
   * @param sampleRate the sampling rate in Hz.
   */
  def echo(alpha: Option[Float], delay: Option[Int], sampleRate: Option[Int]): AudioEffect =
    val echo = EchoEffect()
    EchoEffect(
      alpha = alpha.getOrElse(echo.alpha),
      delay = delay.getOrElse(echo.delay),
      frequency = sampleRate.getOrElse(echo.frequency)
    )

  /**
   * Returns the Pan effect.
   * @param panValue if the value is not present the Pan effect will be
   *                 applied with the default value.
   */
  def pan(panValue: Option[Float]): AudioEffect =
    panValue.fold(PanEffect())(PanEffect(_))

  /**
   * Returns the AutoPan effect.
   * @param autopanValue if the value is not present the AutoPan effect will be
   *                     applied with the default value.
   *                     It's the frequency or the sampling rate in Hz.
   */
  def autopan(autopanValue: Option[Int]): AudioEffect =
    autopanValue.fold(AutopanEffect())(AutopanEffect(_))

  /**
   * Returns the Volume effect.
   * @param volumeValue if the value is not present the Volume effect will be
   *                    applied with the default value.
   */
  def volume(volumeValue: Option[Float]): AudioEffect =
    volumeValue.fold(VolumeEffect())(VolumeEffect(_))