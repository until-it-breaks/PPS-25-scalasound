package scalasound.processing

import scalasound.domain.AudioChunk

/**
 * Defines the entity capable of managing the various effects
 * and applying them on the given chunk.
 */
trait AudioProcessor:
  /**
   * It defines the initial checks on the inputs and,
   * if these are successful, applies the effects.
   * @param chunk the chunk audio to be processed.
   * @param effects the list of all requested effects.
   */
  def process(chunk: AudioChunk, effects: List[AudioEffect]): Either[AudioError, AudioChunk] =
    if chunk.getVectors.isEmpty then Left(AudioError.AudioProcessorError(this, "Chunk is empty!"))
    else
      if effects.isEmpty then Right(chunk)
      else apply(chunk, effects)

  protected def apply(chunk: AudioChunk, effects: List[AudioEffect]): Either[AudioError, AudioChunk]

/**
 * AudioProcessor with no rules on how to apply effects.
 */
object IdentityAudioProcessor extends AudioProcessor:
  protected def apply(chunk: AudioChunk, effects: List[AudioEffect]): Either[AudioError, AudioChunk] =
    effects.foldLeft[Either[AudioError, AudioChunk]](Right(chunk)){
      (currentChunk, effect) => currentChunk.flatMap(chunky => effect(chunky))
    }

/**
 * AudioProcessor with rules that define the order on how to apply effects.
 */
object AudioProcessorWithRules extends AudioProcessor:

  /* Specifies the different effects and the corresponding order. */
  private enum EffectPriority:
    case IdentityEffect
    case GainEffect
    // case DistortionEffect
    case PanEffect
    // case EchoEffect
    // case AutopanEffect
    case VolumeEffect

  /* It defines the mechanism for ordering the various effects based on priority. */
  private def orderingByPriority(effect: AudioEffect): Int = effect match
    case _: IdentityEffect => EffectPriority.IdentityEffect.ordinal
    case _: GainEffect => EffectPriority.GainEffect.ordinal
    case _: PanEffect => EffectPriority.PanEffect.ordinal
    case _: VolumeEffect => EffectPriority.VolumeEffect.ordinal
    case _ => EffectPriority.values.length

  /* Implicit ordering. */
  given Ordering[AudioEffect] = Ordering.by(orderingByPriority)

  protected def apply(chunk: AudioChunk, effects: List[AudioEffect]): Either[AudioError, AudioChunk] =
    effects.sorted.foldLeft[Either[AudioError, AudioChunk]](Right(chunk)){
      (currentChunk, effect) => currentChunk.flatMap(chunky => effect(chunky))
    }

end AudioProcessorWithRules