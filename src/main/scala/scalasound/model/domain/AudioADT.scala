package scalasound.model.domain

import AudioChunk.{MonoChunk, StereoChunk}

/** Represents domain validation errors that can occur when constructing audio tracks. */
enum AudioDomainError:

  /** The provided sample rate is invalid (non-positive).
   *
   *  @param rate the invalid sample rate value
   */
  case InvalidSampleRate(rate: Int)

  /** The stereo channels, in a chunk, have different lengths.
   *
   *  @param chunkIndex the index of the chunk with channels of different lengths
   *  @param leftLength the length of the left channel
   *  @param rightLength the length of the right channel
   */
  case DifferentLengthsStereoChannels(chunkIndex: Int, leftLength: Int, rightLength: Int)

opaque type SampleRate = Int

object SampleRate:

  /**
   * Creates a [[SampleRate]] if the given value is strictly positive.
   *
   * @param value the sampling rate in hertz
   * @return `Right(SampleRate)` if positive, or `Left(AudioDomainError.InvalidSampleRate)` otherwise
   */
  def from(value: Int): Either[AudioDomainError, SampleRate] =
    Either.cond(value > 0, value, AudioDomainError.InvalidSampleRate(value))

  extension (sampleRate: SampleRate)
    def value: Int = sampleRate

/**
 * Represents a block of audio samples for either a mono or a stereo signal.
 *
 * A mono chunk contains one sample per frame, while a stereo chunk contains
 * one sample for the left channel and one sample for the right channel
 * per frame.
 */
enum AudioChunk:

  /**
   * Represents a block of mono audio samples.
   * @param samples the sequence of audio samples, one per frame
   */
  case MonoChunk(samples: Vector[Float])

  /**
   * Represents a block of stereo audio samples.
   *
   * The samples of the left and right channels are stored separately.
   * Both channels must contain the same number of samples.
   * @param left  the sequence of samples for the left channel
   * @param right the sequence of samples for the right channel
   */
  case StereoChunk(left: Vector[Float], right: Vector[Float])

  /**
   * Returns the number of audio frames in this chunk.
   *
   * A frame contains one sample per channel. Therefore, a mono frame
   * corresponds to one sample, while a stereo frame corresponds to one
   * sample from each channel.
   *
   * For stereo chunks, this method returns the length of one channel,
   * assuming both channels have the same length.
   * @return the number of audio frames in this chunk
   */
  def frameCount: Int = this match
    case MonoChunk(samples)  => samples.length
    case StereoChunk(left, _) => left.length

  /**
   * Checks the emptiness of the vectors contained in a chunk.
   * @return [[true]] if the chunk is empty false otherwise.
   */
  def isEmpty: Boolean = this match
    case MonoChunk(samples) => samples.isEmpty
    case StereoChunk(left, right) => left.isEmpty && right.isEmpty

  /**
   * Return the content of a chunk in a list.
   * @return the list of float.
   */
  def channels: List[Vector[Float]] = this match
    case MonoChunk(samples) => List(samples)
    case StereoChunk(left, right) => List(left, right)


/**
 * Represents an audio track.
 *
 * The duration is calculated from the total number of audio frames
 * and the sampling rate, rather than from the number of chunks.
 *
 * An audio track may contain one or more chunks of audio samples.
 */
trait AudioTrack:

  /**
   * The number of audio samples per second for each channel.
   * The sampling rate is represented in hertz (Hz)
   */
  def sampleRate: SampleRate

  /**
   * The vector of chunks that composing the corresponding track.
   */
  def chunks: Vector[AudioChunk]

  /**
   * Returns the total number of audio frames across all chunks.
   *
   * For mono audio, each sample corresponds to one frame.
   * For stereo audio, each frame contains one sample from
   * the left channel and one sample from the right channel.
   * Since both channels have equal lengths, the frame count
   * is obtained from either channel.
   * @return the total number of frames in the track
   */
  def frameCount: Long =
    chunks.map(_.frameCount.toLong).sum

  /**
   * Returns the total duration of the track in seconds.
   * The duration is calculated by dividing
   * the total number of frames by the sampling rate.
   *
   * @return the duration in seconds
   */
  def durationInSeconds: Double =
    frameCount.toDouble / sampleRate.value

  /**
   * Checks the emptiness of the track.
   * @return [[true]] if the track is empty and false otherwise.
   */
  def isEmpty: Boolean =
    chunks.isEmpty && chunks.forall(_.isEmpty)

  /**
   * Return the content of a track in a list.
   * @return the list of float.
   */
  def chunksAsList: List[AudioChunk] = this.chunks.toList


/**
 * Represents a mono audio track composed of mono chunks.
 *
 * The duration is calculated from the total number of audio frames
 * and the sampling rate, rather than from the number of chunks.
 *
 * @param sampleRate the validated positive sampling rate in hertz
 * @param chunks     the ordered sequence of mono audio chunks
 */
final case class MonoTrack private (sampleRate: SampleRate, chunks: Vector[MonoChunk]) extends AudioTrack

object MonoTrack:
  /**
   * Constructs a [[MonoTrack]], validating that the sample rate is positive.
   *
   * @param sampleRate the sampling rate in hertz; must be positive
   * @param chunks     the ordered sequence of mono audio chunks
   * @return `Right(MonoTrack)` if valid, or `Left(AudioDomainError)` otherwise
   */
  def apply(sampleRate: Int, chunks: Vector[MonoChunk]): Either[AudioDomainError, MonoTrack] =
    SampleRate.from(sampleRate).map(validRate => new MonoTrack(validRate, chunks))

  /**
   * Constructs a [[MonoTrack]] from an already validated [[SampleRate]].
   */
  def fromValidated(sampleRate: SampleRate, chunks: Vector[MonoChunk]): MonoTrack =
    new MonoTrack(sampleRate, chunks)


/**
 * Represents a stereo audio track composed of stereo chunks.
 *
 * Each stereo frame contains one left-channel sample and one
 * right-channel sample. Consequently, the frame count is the length
 * of either channel, not the sum of both channel lengths.
 *
 * @param sampleRate the validated positive sampling rate in hertz
 * @param chunks     the ordered sequence of stereo audio chunks
 */
final case class StereoTrack private (sampleRate: SampleRate, chunks: Vector[StereoChunk]) extends AudioTrack

object StereoTrack:
  /**
   * Constructs a [[StereoTrack]], validating that the sample rate is positive
   * and that all stereo chunks have balanced left and right channel lengths.
   *
   * @param sampleRate the sampling rate in hertz; must be positive
   * @param chunks     the ordered sequence of stereo audio chunks
   * @return `Right(StereoTrack)` if valid, or `Left(AudioDomainError)` otherwise
   */
  def apply(sampleRate: Int, chunks: Vector[StereoChunk]): Either[AudioDomainError, StereoTrack] =
    for
      validRate <- SampleRate.from(sampleRate)
      _         <- validateChunks(chunks)
    yield new StereoTrack(validRate, chunks)

  /**
   * Constructs a [[StereoTrack]] from an already validated [[SampleRate]],
   * still ensuring channel length consistency across chunks.
   */
  def fromValidated(sampleRate: SampleRate, chunks: Vector[StereoChunk]): Either[AudioDomainError, StereoTrack] =
    validateChunks(chunks).map(_ => new StereoTrack(sampleRate, chunks))

  private def validateChunks(chunks: Vector[StereoChunk]): Either[AudioDomainError, Unit] =
    chunks.zipWithIndex
      .find { case (chunk, _) => chunk.left.length != chunk.right.length }
      .fold[Either[AudioDomainError, Unit]](Right(())) { case (badChunk, idx) =>
        Left(AudioDomainError.DifferentLengthsStereoChannels(idx, badChunk.left.length, badChunk.right.length))
      }
