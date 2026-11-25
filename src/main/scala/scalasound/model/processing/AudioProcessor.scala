package scalasound.model.processing

import scalasound.model.domain.AudioChunk.{MonoChunk, StereoChunk}
import AudioError.AudioProcessorError
import scalasound.model.domain.{AudioChunk, AudioTrack, MonoTrack, SampleRate, StereoTrack}
import scalasound.model.processing.processors.{AudioProcessorWithRules, SimpleAudioProcessor}

/**
 * Defines the entity capable of managing the various effects
 * and applying them on the given chunk.
 */
trait AudioProcessor:
  /**
   * It defines the initial checks on the inputs and,
   * if these are successful, applies the effects.
   *
   * @param chunk      the chunk where to apply the effects.
   * @param effects    the list of all the effects that will be applied.
   * @param chunkIndex the index of the current chunk.
   * @return an [[AudioError]] when an error occurs or
   *         the type of input if everything is ok.
   */
  def apply(chunk: AudioChunk, effects: List[AudioEffect], chunkIndex: Int): Either[AudioError, AudioChunk] =
    if effects.isEmpty then Right(chunk)
    else if chunk.isEmpty then Left(AudioProcessorError(this, "Chunk is empty!"))
    else processChunk(chunk, effects, chunkIndex)

  /**
   * It defines the initial checks on the inputs and,
   * if these are successful, applies the effects.
   *
   * @param track   the track where to apply the effects
   * @param effects the list of all the effects that will be applied.
   * @return an [[AudioError]] when an error occurs or
   *         the type of input if everything is ok.
   */
  def apply(track: AudioTrack, effects: List[AudioEffect]): Either[AudioError, AudioTrack] =
    if effects.isEmpty then Right(track)
    else if track.isEmpty then Left(AudioProcessorError(this, "Track is empty!"))
    else processTrack(track, effects)

  /* Applies the effects on the given chunk. */
  protected def processChunk(chunk: AudioChunk,
                             effects: List[AudioEffect],
                             chunkIndex: Int): Either[AudioError, AudioChunk] =
    effects.foldLeft[Either[AudioError, AudioChunk]](Right(chunk)):
      (currentChunk, effect) =>
        currentChunk.flatMap:
          chunky =>
            effect match
              case timeEffect: TimeAudioEffect => timeEffect(chunky, chunkIndex)
              case standardEffect: StatelessAudioEffect => standardEffect(chunky)

  /* Applies the effects on the given track. */
  protected def processTrack(track: AudioTrack, effects: List[AudioEffect]): Either[AudioError, AudioTrack] =
    for
      (reversedChunks, _) <- processedAudioTrack(track, effects)
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
      isStereo = chunks.exists {
        case _ :MonoChunk => false
        case _ :StereoChunk => true
      }

      resultTrack <- trackMonoOrStereo(chunks, isStereo, track.sampleRate)
    yield resultTrack

  /* Applies the effects on all the chunks in the track. */
  private def processedAudioTrack(track: AudioTrack, effects: List[AudioEffect]): Either[AudioError, (List[AudioChunk], Int)] =
    track.chunksAsList.
      // It starts with an empty list and index 0.
      foldLeft[Either[AudioError, (List[AudioChunk], Int)]](Right((Nil, 0))):
        (accumulator, currentChunk) =>
          for
            (acc, idx) <- accumulator
            chunk <- processChunk(currentChunk, effects, idx)
          yield
            (chunk :: acc, idx + 1)

  /* Builds a new track Mono or Stereo by checking a flag that indicates how will be the result track. */
  private def trackMonoOrStereo(chunks: List[AudioChunk], isStereo: Boolean, sampleRate: SampleRate): Either[AudioError, AudioTrack] =
    if isStereo then
      val stereoChunks: Vector[StereoChunk] = chunks.map[StereoChunk] {
        case stereoChunk: StereoChunk => stereoChunk
        case MonoChunk(samples) => StereoChunk(samples, samples)
      }.toVector
      StereoTrack
        .fromValidated(sampleRate, stereoChunks)
        .left
        .map(err => AudioProcessorError(this, err.toString))
    else
      val monoChunks: Vector[MonoChunk] = chunks.collect {
        case monoChunk: MonoChunk => monoChunk
      }.toVector
      Right(MonoTrack.fromValidated(sampleRate, monoChunks))

object AudioProcessor:

  /**
   * @return a new AudioProcessor with no internal ordering
   *         for the effects.
   */
  def simple: AudioProcessor = SimpleAudioProcessor

  /**
   * @return a new AudioProcessor with a defined ordering logic
   *         for the effects.
   */
  def withRules: AudioProcessor = AudioProcessorWithRules