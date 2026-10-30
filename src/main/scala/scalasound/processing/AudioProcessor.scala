package scalasound.processing

import scalasound.domain.AudioChunk.{MonoChunk, StereoChunk}
import scalasound.domain.{AudioChunk, AudioTrack, MonoTrack, StereoTrack}
import scalasound.processing.AudioError.AudioProcessorError

/**
 * Defines the entity capable of managing the various effects
 * and applying them on the given chunk.
 */
trait AudioProcessor:
  /**
   * It defines the initial checks on the inputs and,
   * if these are successful, applies the effects.
   * @param chunk the chunk where to apply the effects.
   * @param effects the list of all the effects that will be applied.
   * @return an [[AudioError]] when an error occurs or
   *         the type of input if everything is ok.
   */
  def apply(chunk: AudioChunk, effects: List[AudioEffect]): Either[AudioError, AudioChunk] =
    if effects.isEmpty then Right(chunk)
    else if chunk.isEmpty then Left(AudioProcessorError(this, "Chunk is empty!"))
    else processChunk(chunk, effects)

  /**
   * It defines the initial checks on the inputs and,
   * if these are successful, applies the effects.
   * @param track the track where to apply the effects
   * @param effects the list of all the effects that will be applied.
   * @return an [[AudioError]] when an error occurs or
   *         the type of input if everything is ok.
   */
  def apply(track: AudioTrack, effects: List[AudioEffect]): Either[AudioError, AudioTrack] =
    if effects.isEmpty then Right(track)
    else if track.isEmpty then Left(AudioProcessorError(this, "Track is empty!"))
    else processTrack(track, effects)

  /* Applies the effects on the given chunk. */
  protected def processChunk(chunk: AudioChunk, effects: List[AudioEffect]): Either[AudioError, AudioChunk] =
    effects.foldLeft[Either[AudioError, AudioChunk]](Right(chunk)) {
      (currentChunk, effect) =>
        for
          chunky <- currentChunk  /* Extracts the chunk if is Right. */
          result <- effect(chunky) /* Applies the effect on the given chunk. */
        yield result
    }

  /* Applies the effects on the given track. */
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

      /*
       * If the track is [C1, C2, C3], the foldLeft returns [C3, C2, C1].
       * FoldLeft chosen for performance because it starts at the beginning
       * and add element at the front of the list.
       */
      chunks = reversedChunks.reverse

      /*
       * Checks if there is at least one StereoChunk in the track.
       * If the outcome is positive then the result track is an StereoTrack
       * otherwise a MonoTrack.
       */
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
    case DistortionEffect
    case EchoEffect
    case PanEffect
    case AutopanEffect
    case VolumeEffect

  /* It defines the mechanism for ordering the various effects based on priority. */
  private def orderingByPriority(effect: AudioEffect): Int = effect match
    case _: IdentityEffect => EffectPriority.IdentityEffect.ordinal
    case _: GainEffect => EffectPriority.GainEffect.ordinal
    case _: DistortionEffect => EffectPriority.DistortionEffect.ordinal
    case _: EchoEffect => EffectPriority.EchoEffect.ordinal
    case _: PanEffect => EffectPriority.PanEffect.ordinal
    case _: AutopanEffect => EffectPriority.AutopanEffect.ordinal
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