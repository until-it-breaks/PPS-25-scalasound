package scalasound.model.domain

/** Represents domain validation errors that can occur when constructing audio tracks. */
enum AudioDomainError:

  /** The provided sample rate is invalid (non-positive).
    *
    * @param rate
    *   the invalid sample rate value
    */
  case InvalidSampleRate(rate: Int)

  /** The stereo channels, in a chunk, have different lengths.
    *
    * @param chunkIndex
    *   the index of the chunk with channels of different lengths
    * @param leftLength
    *   the length of the left channel
    * @param rightLength
    *   the length of the right channel
    */
  case DifferentLengthsStereoChannels(chunkIndex: Int, leftLength: Int, rightLength: Int)
