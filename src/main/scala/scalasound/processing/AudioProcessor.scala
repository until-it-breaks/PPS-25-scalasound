package scalasound.processing

import scalasound.domain.AudioChunk

trait AudioProcessor:
  def process(chunk: AudioChunk, effects: List[AudioEffect]): AudioChunk =
    //if chunk.getVectors.isEmpty // TODO: Check the emptiness of the chunk
    if effects.isEmpty then chunk
    else apply(chunk, effects)

  protected def apply(chunk: AudioChunk, effects: List[AudioEffect]): AudioChunk

object IdentityAudioProcessor extends AudioProcessor:
  protected def apply(chunk: AudioChunk, effects: List[AudioEffect]): AudioChunk =
    effects.foldLeft(chunk)((chunky, effect) => effect(chunky))


object AudioProcessorWithRules extends AudioProcessor:

  private enum EffectPriority:
    case IdentityEffect
    case GainEffect
    // case DistortionEffect
    case PanEffect
    // case EchoEffect
    // case AutopanEffect
    case VolumeEffect

  private def orderingByPriority(effect: AudioEffect): Int = effect match
    case _: IdentityEffect => EffectPriority.IdentityEffect.ordinal
    case _: GainEffect => EffectPriority.GainEffect.ordinal
    case _: PanEffect => EffectPriority.PanEffect.ordinal
    case _: VolumeEffect => EffectPriority.VolumeEffect.ordinal
    case _ => EffectPriority.values.length

  given Ordering[AudioEffect] = Ordering.by(orderingByPriority)

  protected def apply(chunk: AudioChunk, effects: List[AudioEffect]): AudioChunk =
    effects.sorted.foldLeft(chunk)((chunky, effect) => effect(chunky))

end AudioProcessorWithRules