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
  def identity: AudioEffect =
    IdentityEffect()

  def gain(gainValue: Option[Float]): AudioEffect =
    gainValue.fold(GainEffect())(GainEffect(_))

  def pan(panValue: Option[Float]): AudioEffect =
    panValue.fold(PanEffect())(PanEffect(_))

  def volume(volumeValue: Option[Float]): AudioEffect =
    volumeValue.fold(VolumeEffect())(VolumeEffect(_))

// TODO: aggiungere anche distorsione??

// TODO: Autopan: θ una funzione del tempo θ(t)= A * sin(2 * pi * f * t)), dove f è la frequenza dell'oscillazione.