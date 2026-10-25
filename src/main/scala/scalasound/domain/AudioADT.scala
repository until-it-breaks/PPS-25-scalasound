package scalasound.domain

import scalasound.domain.AudioChunk.{MonoChunk, StereoChunk}

/** Represents a block of audio samples for either a mono or a stereo signal.
 *
 *  A mono chunk contains one sample per frame, while a stereo chunk contains
 *  one sample for the left channel and one sample for the right channel
 *  per frame.
 */
enum AudioChunk:

  /** Represents a block of mono audio samples.
   *
   *  @param samples the sequence of audio samples, one per frame
   */
  case MonoChunk(samples: Vector[Float])

  /** Represents a block of stereo audio samples.
   *
   *  The samples of the left and right channels are stored separately.
   *  Both channels must contain the same number of samples.
   *
   *  @param left  the sequence of samples for the left channel
   *  @param right the sequence of samples for the right channel
   */
  case StereoChunk(left: Vector[Float], right: Vector[Float])

  /** Returns the number of audio frames in this chunk.
   *
   *  A frame contains one sample per channel. Therefore, a mono frame
   *  corresponds to one sample, while a stereo frame corresponds to one
   *  sample from each channel.
   *
   *  For stereo chunks, this method returns the length of one channel,
   *  assuming both channels have the same length.
   *
   *  @return the number of audio frames in this chunk
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
  def getVectors: List[Vector[Float]] = this match
    case MonoChunk(samples) => List(samples)
    case StereoChunk(left, right) => List(left, right)


/** Represents an audio track.
 *
 *  The duration is calculated from the total number of audio frames
 *  and the sampling rate, rather than from the number of chunks.
 *
 *  An audio track may contain one or more chunks of audio samples.
 */
// TODO: RIVEDERE LA DEFINIZIONE DI AUDIOTRACK VISTO CHE E' MOLTO RIDONDANTE
trait AudioTrack:

  /** Returns the number of audio samples per second for each channel.
   *
   *  @return the sampling rate in hertz (Hz)
   */
  def sampleRate: Int

  /** Returns the total duration of the track in seconds.
   *
   *  @return the duration in seconds
   */
  def durationInSeconds: Double

  /**
   * Checks the emptiness of the track.
   * @return [[true]] if the track is empty and false otherwise.
   */
  def isEmpty: Boolean

  /**
   * Return the content of a track in a list.
   * @return the list of float.
   */
  def getVectors: List[AudioChunk]


/** Represents a mono audio track composed of mono chunks.
 *
 *  The duration is calculated from the total number of audio frames
 *  and the sampling rate, rather than from the number of chunks.
 *
 *  @param sampleRate the sampling rate in hertz; must be positive
 *  @param chunks     the ordered sequence of mono audio chunks
 */
case class MonoTrack(sampleRate: Int, chunks: Vector[MonoChunk]) extends AudioTrack:

  // TODO: MODIFICARE I REQUIRE E MODIFICARE LA DOC PERCHE' RIDONDANTE.
  require(
    sampleRate > 0,
    s"sampleRate must be positive, got $sampleRate"
  )

  /** Returns the total number of audio frames across all chunks.
   *
   *  For mono audio, each sample corresponds to one frame.
   *
   *  @return the total number of frames in the track
   */
  def frameCount: Long =
    chunks.map(_.frameCount.toLong).sum

  /** Returns the duration of the track in seconds.
   *
   *  The duration is calculated by dividing
   *  the total number of frames by the sampling rate.
   *
   *  @return the track duration in seconds
   */
  def durationInSeconds: Double =
    frameCount.toDouble / sampleRate
    
  def isEmpty: Boolean = chunks.isEmpty && chunks.forall(_.isEmpty)

  def getVectors: List[AudioChunk] = this.chunks.toList

end MonoTrack

/** Represents a stereo audio track composed of stereo chunks.
 *
 *  Each stereo frame contains one left-channel sample and one
 *  right-channel sample. Consequently, the frame count is the length
 *  of either channel, not the sum of both channel lengths.
 *
 *  @param sampleRate the sampling rate in hertz; must be positive
 *  @param chunks     the ordered sequence of stereo audio chunks
 */
case class StereoTrack(sampleRate: Int, chunks: Vector[StereoChunk]) extends AudioTrack:

  // TODO: MODIFICARE I REQUIRE E MODIFICARE LA DOC PERCHE' RIDONDANTE
  require(
    sampleRate > 0,
    s"sampleRate must be positive, got $sampleRate"
  )

  require(
    chunks.forall(c => c.left.length == c.right.length),
    "left and right channels of every stereo chunk must have the same length"
  )

  /** Returns the total number of audio frames across all chunks.
   *
   *  Each frame contains one sample from the left channel and one
   *  sample from the right channel. Since both channels have equal
   *  lengths, the frame count is obtained from either channel.
   *
   *  @return the total number of frames in the track
   */
  def frameCount: Long =
    chunks.map(_.frameCount.toLong).sum

  /** Returns the duration of the track in seconds.
   *
   *  The duration is calculated by dividing the total number of frames
   *  by the sampling rate.
   *
   *  @return the track duration in seconds
   */
  def durationInSeconds: Double =
    frameCount.toDouble / sampleRate
    
  def isEmpty: Boolean = chunks.isEmpty && chunks.forall(_.isEmpty)

  def getVectors: List[AudioChunk] = this.chunks.toList

end StereoTrack

@main def main(): Unit =
  val monoTrack = MonoTrack(44100, Vector(MonoChunk(Vector.fill(10)(0f))))
  println(monoTrack.chunks)

  val stereoTrack = StereoTrack(44100, Vector(StereoChunk(Vector.fill(10)(0f), Vector.fill(10)(0f))))
  println(stereoTrack.chunks)