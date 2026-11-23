package scalasound.model.io

import scalasound.model.domain.AudioChunk
import scalasound.model.domain.AudioChunk.{MonoChunk, StereoChunk}
import scalasound.model.io.Pcm16.{fromLittleEndianBytes, toNormalised, toPcm16}

import java.nio.{ByteBuffer, ByteOrder}

/** Conversions between [[AudioChunk]]s and interleaved 16-bit little-endian PCM bytes. */
private[io] object PcmChunks:

  /** Encodes a chunk as interleaved little-endian PCM bytes, two bytes per sample.
    *
    * Samples must be representable (see [[Pcm16.isRepresentable]]): the caller validates them
    * first. The channels of a stereo chunk have the same length, an invariant guaranteed by
    * [[StereoTrack]]: in case they are different, only the common frames would be encoded.
    *
    * @return
    *   `frameCount * channels * 2` bytes: (left sample, right sample, left sample, ...) for stereo
    */
  def encode(chunk: AudioChunk): Array[Byte] = chunk match
    case MonoChunk(samples) =>
      val buffer = littleEndianBuffer(samples.length * Pcm16.BytesPerSample)
      samples.foreach(sample => buffer.putShort(sample.toPcm16))
      buffer.array()
    case StereoChunk(left, right) =>
      val frames = math.min(left.length, right.length)
      val buffer = littleEndianBuffer(frames * 2 * Pcm16.BytesPerSample)
      left.iterator
        .zip(right.iterator)
        .foreach: (l, r) =>
          buffer.putShort(l.toPcm16)
          buffer.putShort(r.toPcm16)
      buffer.array()

  private def littleEndianBuffer(size: Int): ByteBuffer =
    ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)

  /** Decodes mono PCM bytes into chunks of at most `maxFramesPerChunk` frames; the last chunk holds
    * the remaining frames, without padding. No frames means no chunks.
    *
    * @param bytes
    *   at least `totalFrames * 2` bytes of little-endian PCM
    * @param totalFrames
    *   the total number of frames to decode
    * @param maxFramesPerChunk
    *   the maximum number of frames per chunk, strictly positive
    */
  def decodeMono(bytes: Array[Byte], totalFrames: Int, maxFramesPerChunk: Int): Vector[MonoChunk] =
    partitionIntoRanges(totalFrames, maxFramesPerChunk).map: range =>
      MonoChunk(decodeChannel(bytes, range.startFrame, range.frameCount, channels = 1, channel = 0))

  private def decodeChannel(
      bytes: Array[Byte],
      firstFrame: Int,
      frames: Int,
      channels: Int,
      channel: Int
  ): Vector[Float] =
    Vector.tabulate(frames): frame =>
      fromLittleEndianBytes(bytes, (firstFrame + frame) * channels + channel).toNormalised

  private def partitionIntoRanges(totalFrames: Int, maxFramesPerChunk: Int): Vector[FrameRange] =
    Vector
      .range(0, totalFrames, maxFramesPerChunk)
      .map: startFrame =>
        val remainingFrames = totalFrames - startFrame
        val chunkFrameCount = math.min(maxFramesPerChunk, remainingFrames)
        FrameRange(startFrame, chunkFrameCount)

  /** Decodes interleaved stereo PCM bytes into chunks of at most `maxFramesPerChunk` frames, with
    * the two channels of every chunk aligned; the last chunk holds the remaining frames, without
    * padding. No frames means no chunks.
    *
    * @param bytes
    *   at least `totalFrames * 4` bytes of little-endian PCM
    * @param totalFrames
    *   the total number of frames to decode
    * @param maxFramesPerChunk
    *   the maximum number of frames per chunk, strictly positive
    */
  def decodeStereo(
      bytes: Array[Byte],
      totalFrames: Int,
      maxFramesPerChunk: Int
  ): Vector[StereoChunk] =
    partitionIntoRanges(totalFrames, maxFramesPerChunk).map: range =>
      StereoChunk(
        decodeChannel(bytes, range.startFrame, range.frameCount, channels = 2, channel = 0),
        decodeChannel(bytes, range.startFrame, range.frameCount, channels = 2, channel = 1)
      )

  private final case class FrameRange(startFrame: Int, frameCount: Int)
