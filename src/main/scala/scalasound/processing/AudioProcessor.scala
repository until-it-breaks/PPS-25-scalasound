package scalasound.processing

import scalasound.domain.AudioChunk.{MonoChunk, StereoChunk}
import scalasound.domain.{AudioChunk, AudioTrack, MonoTrack, StereoTrack}
import scalasound.processing.AudioError.AudioProcessorError

// TODO: MODIFICARE DOCUMENTAZIONE

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
  def apply[T](input: T, effects: List[AudioEffect]): Either[AudioError, T] =
    if effects.isEmpty then Right(input)
    else input match
      case chunk: AudioChunk =>
        if chunk.isEmpty then Left(AudioError.AudioProcessorError(this, "Chunk is empty!"))
        else processChunk(chunk, effects).asInstanceOf[Either[AudioError, T]]
      case track: AudioTrack =>
        if track.isEmpty then Left(AudioError.AudioProcessorError(this, "Track is empty!"))
        else processTrack(track, effects).asInstanceOf[Either[AudioError, T]]
      case _ => Left(AudioError.AudioProcessorError(this, "Input is not valid!"))

    // TODO: Change the if else with?:
    /*
     * (chunk.isEmpty, effects.isEmpty) match
     *  case (true, _) => Left(AudioError.AudioProcessorError(this, "Chunk is empty!"))
     *  case (_, true) => Right(chunk)
     *  case _ => process(chunk, effects)
     */

  /* Applies the effect on the given chunk. */
  protected def processChunk(chunk: AudioChunk, effects: List[AudioEffect]): Either[AudioError, AudioChunk] =
    effects.foldLeft[Either[AudioError, AudioChunk]](Right(chunk)) {
      (currentChunk, effect) =>
        for
          chunky <- currentChunk
          result <- effect(chunky)
        yield result
    }

  protected def processTrack(track: AudioTrack, effects: List[AudioEffect]): Either[AudioError, AudioTrack] =
    for
      reversedChunks <- track.getVectors.
        //map(x => processChunk(x, effects)).
        foldLeft[Either[AudioError, List[AudioChunk]]](Right(Nil)) { (accumulator, currentChunk) =>
          for
            acc <- accumulator /* Extracts the accumulator as a list (initially is Nil). */
            chunk <- processChunk(currentChunk, effects) /* Extracts the chunk if is Right. */
          yield chunk :: acc /* Adds the chunk in front of the list. */
        }

      chunks = reversedChunks.reverse
      isStereo = chunks.exists(_.isInstanceOf[StereoChunk])

      resultTrack <- if isStereo then
        val stereoChunks: Vector[StereoChunk] = chunks.map {
          case stereoChunk: StereoChunk => stereoChunk
          case MonoChunk(samples) => StereoChunk(samples, samples)
        }.toVector.asInstanceOf[Vector[StereoChunk]]
        Right(StereoTrack(track.sampleRate, stereoChunks))
      else
        val monoChunks: Vector[MonoChunk] = chunks.toVector.asInstanceOf[Vector[MonoChunk]]
        Right(MonoTrack(track.sampleRate, monoChunks))
    yield resultTrack

/**
 * AudioProcessor with no rules on how to apply effects.
 */
object SimpleAudioProcessor extends AudioProcessor

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

  /* Implicit ordering by using priority on the effects. */
  given Ordering[AudioEffect] = Ordering.by(orderingByPriority)

  override protected def processChunk(chunk: AudioChunk, effects: List[AudioEffect]): Either[AudioError, AudioChunk] =
    /*effects.sorted.foldLeft[Either[AudioError, AudioChunk]](Right(chunk)){
      (currentChunk, effect) => currentChunk.flatMap(chunky => effect(chunky))
    }*/
    super.processChunk(chunk, effects.sorted)

  override protected def processTrack(track: AudioTrack, effects: List[AudioEffect]): Either[AudioError, AudioTrack] =
    super.processTrack(track, effects.sorted)

end AudioProcessorWithRules