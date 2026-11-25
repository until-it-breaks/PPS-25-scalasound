package scalasound.model.io.wav

/** Conversions for 16-bit signed little-endian PCM, the sample format of the supported WAV subset.
  *
  * A normalised sample `x` becomes `clamp(round(x * 32768), -32768, 32767)`, with ties rounded
  * towards +infinity. Values outside [-1.0, 1.0) are therefore clipped to the extremes of the
  * 16-bit range. Non-finite samples have no PCM representation.
  *
  * The opposite direction maps a PCM value `s` to the normalised sample `s / 32768f`, so that
  * -32768 becomes -1.0 and 32767 becomes 32767/32768.
  */
private[wav] object Pcm16:

  final val BitsPerSample         = 16
  final val BytesPerSample        = 2
  private final val Min           = Short.MinValue.toLong
  private final val Max           = Short.MaxValue.toLong
  private final val Scale         = 32768.0
  private final val Normalisation = 32768f

  extension (sample: Float)

    /** Whether the sample has a PCM representation, that is whether it is neither `NaN` nor
      * infinite. Finite samples outside [-1.0, 1.0) are representable too: they are clipped.
      */
    def isRepresentable: Boolean =
      !sample.isNaN && !sample.isInfinite

    /** Quantises a normalised sample to a signed 16-bit PCM value, clipping it to the representable
      * range.
      */
    def toPcm16: Short =
      math.round(sample.toDouble * Scale).max(Min).min(Max).toShort

  extension (pcm: Short)

    /** Converts a signed 16-bit PCM value to a normalised sample in [-1.0, 1.0). */
    def toNormalised: Float =
      pcm / Normalisation

  /** Reads the PCM value at the given sample index from little-endian bytes (low byte first).
    *
    * @param bytes
    *   the encoded samples, two bytes each
    * @param sampleIndex
    *   the index of the sample, not of its first byte
    */
  def fromLittleEndianBytes(bytes: Array[Byte], sampleIndex: Int): Short =
    val offset = sampleIndex * BytesPerSample
    val low    = bytes(offset) & 0xff
    val high   = bytes(offset + 1)
    ((high << 8) | low).toShort
