package scalasound.model.processing.processors

import scalasound.model.domain.{AudioChunk, AudioTrack}
import scalasound.model.processing.effects.*
import scalasound.model.processing.{AudioEffect, AudioError, AudioProcessor}

/* Specifies the different effects and the corresponding order. */
private enum EffectPriority:
  case IdentityEffect
  case GainEffect
  case DistortionEffect
  case EchoEffect
  case PanEffect
  case AutopanEffect
  case VolumeEffect

/*
 * AudioProcessor with rules that define the order on how to apply effects.
 */
private[processing] object AudioProcessorWithRules extends AudioProcessor:

  /* Implicit ordering by using priority on the effects. */
  given Ordering[AudioEffect] = Ordering.by(orderingByPriority)

  /* It defines the mechanism for ordering the various effects based on priority. */
  private def orderingByPriority(effect: AudioEffect): Int = effect match
    case IdentityEffect => EffectPriority.IdentityEffect.ordinal
    case _: GainEffect => EffectPriority.GainEffect.ordinal
    case _: DistortionEffect => EffectPriority.DistortionEffect.ordinal
    case _: EchoEffect => EffectPriority.EchoEffect.ordinal
    case _: PanEffect => EffectPriority.PanEffect.ordinal
    case _: AutopanEffect => EffectPriority.AutopanEffect.ordinal
    case _: VolumeEffect => EffectPriority.VolumeEffect.ordinal
    case _ => EffectPriority.values.length

  private def sortEffects(effects: List[AudioEffect]): List[AudioEffect] =
    if effects.length <= 1 then effects else effects.sorted

  override protected def processChunk(chunk: AudioChunk,
                                      effects: List[AudioEffect],
                                      chunkIndex: Int): Either[AudioError, AudioChunk] =
    super.processChunk(chunk, sortEffects(effects), chunkIndex)

  override protected def processTrack(track: AudioTrack,
                                      effects: List[AudioEffect]): Either[AudioError, AudioTrack] =
    super.processTrack(track, sortEffects(effects))