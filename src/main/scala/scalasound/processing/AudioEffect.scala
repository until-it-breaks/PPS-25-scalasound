package scalasound.processing

import scalasound.domain.AudioChunk
import scalasound.domain.AudioChunk.*

//TODO: Add DOCUMENTATION

/**
 * Defines a contract that applies a transformation on an audio block.
 */
trait AudioEffect:
  def apply(chunk: AudioChunk): Either[AudioError, AudioChunk]

  /* Check whether the constraints for a given effect are met. */
  protected def checkConstraints(isValid: Boolean, msg: String): Option[AudioError] =
    if isValid then Option.empty
    else Some(AudioError.AudioEffectsError(this, msg))

object AudioEffect:

  /**
   * Defines the list of available effects.
   */
  def effects: List[String] =
    this.getClass.
      getMethods.
      filter(_.getReturnType == classOf[AudioEffect]).
      map(_.getName).
      toList

  /**
   * Returns the Identity effect.
   */
  def identity: AudioEffect =
    IdentityEffect()

  /**
   * Returns the Gain effect.
   * @param gainValue if the value is not present the Gain effect will be
   *                  applied with the default value.
   */
  def gain(gainValue: Option[Float]): AudioEffect =
    gainValue.fold(GainEffect())(GainEffect(_))

  /**
   * Returns the Pan effect.
   * @param panValue if the value is not present the Pan effect will be
   *                 applied with the default value.
   */
  def pan(panValue: Option[Float]): AudioEffect =
    panValue.fold(PanEffect())(PanEffect(_))

  /**
   * Returns the Volume effect.
   * @param volumeValue if the value is not present the Volume effect will be
   *                    applied with the default value.
   */
  def volume(volumeValue: Option[Float]): AudioEffect =
    volumeValue.fold(VolumeEffect())(VolumeEffect(_))

// TODO: aggiungere anche distorsione

// TODO: Autopan: θ una funzione del tempo θ(t)= A * sin(2 * pi * f * t)), dove f è la frequenza dell'oscillazione.
