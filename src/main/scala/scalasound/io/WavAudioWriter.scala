package scalasound.io

import java.io.{ByteArrayInputStream, SequenceInputStream}
import java.nio.file.Path
import javax.sound.sampled.{AudioFileFormat, AudioFormat, AudioInputStream, AudioSystem}
import scala.jdk.CollectionConverters.*
import scala.util.{Try, Using}
import scalasound.domain.AudioChunk.{MonoChunk, StereoChunk}
import scalasound.domain.{AudioChunk, AudioTrack, MonoTrack}
import scalasound.io.AudioSaveError.*

/** Saves tracks as WAV files in the file system, using the Java Sound API.
  *
  * Supported subset: PCM signed, 16 bit per sample, little-endian,
  * mono. It is the same subset accepted by [[WavAudioReader]]
  * (stereo export is added in a following change).
  *
  * A normalised sample `x` becomes `clamp(round(x * 32768), -32768, 32767)`,
  * that is the inverse of the reader's `s / 32768f`.
  * So, `0.0 -> 0`, `-1.0 -> -32768` and `+1.0 -> 32767` (clipped). Values outside
  * [-1.0, 1.0) (e.g. produced by effects) are clipped to the extremes.
  * Samples that are NaN or infinite are rejected with [[AudioSaveError.InvalidSample]]
  * before any byte is written.
  *
  * A file, that already exists in the destination path, will be overwritten.
  */
object WavAudioWriter extends AudioWriter:

  private final val BitsPerSample = 16
  private final val BytesPerSample = 2
  private final val PcmMin = -32768L
  private final val PcmMax = 32767L
  private final val Scale = 32768.0
  private[io] final val MaxSampleRateRepresentedByFloat = 1 << 24 // (2^24 Hz)
  private[io] final val MaxDataSizeHandledByPCM = 0xffffffffL - 43 // (2^32 - 44) bytes

  /** Saves a track as a WAV file in the file system.
    *
    * @param track the audio track to save
    * @param path the destination path of the WAV file; if it exists, it will be overwritten
    * @return [[Right]] on success or [[Left]] with an error description on failure
    */
  override def save(track: AudioTrack, path: Path): Either[AudioSaveError, Unit] =
    for
      channels <- determineChannelCount(track)
      format <- buildAudioFormat(channels, track.sampleRate)
      _ <- calculateDataSize(track.frameCount, channels)
      _ <- validateFiniteSamples(track)
      _ <- writeAudioToFile(path, format, track.frameCount, track.chunks)
    yield ()

  private def determineChannelCount(track: AudioTrack): Either[AudioSaveError, Int] = track match
    case _: MonoTrack => Right(1)
    case other        => Left(InvalidTrack(s"unsupported track type: ${other.getClass.getSimpleName}"))

  private def buildAudioFormat(channels: Int, sampleRate: Int): Either[AudioSaveError, AudioFormat] =
    Either.cond(
      sampleRate <= MaxSampleRateRepresentedByFloat,
      AudioFormat(
        AudioFormat.Encoding.PCM_SIGNED,
        sampleRate.toFloat,
        BitsPerSample,
        channels,
        channels * BytesPerSample,
        sampleRate.toFloat,
        false
      ),
      InvalidTrack(s"sample rate $sampleRate exceeds the supported maximum of $MaxSampleRateRepresentedByFloat Hz")
    )

  private[io] def calculateDataSize(frameCount: Long, channels: Int): Either[AudioSaveError, Long] =
    Try(Math.multiplyExact(frameCount, (channels * BytesPerSample).toLong)).toOption
      .filter(_ <= MaxDataSizeHandledByPCM)
      .toRight(FileTooLarge(s"$frameCount frames on $channels channel(s) exceed the limit of $MaxDataSizeHandledByPCM data bytes"))

  private def validateFiniteSamples(track: AudioTrack): Either[AudioSaveError, Unit] =
    track.chunks.iterator
      .flatMap(_.getVectors.iterator.flatten)
      .find(sample => !java.lang.Float.isFinite(sample))
      .fold[Either[AudioSaveError, Unit]](Right(()))(bad => Left(InvalidSample(s"non-finite sample value: $bad")))

  private[io] def quantiseNormalisedSampleToPcm16(sample: Float): Short =
    math.round(sample.toDouble * Scale).max(PcmMin).min(PcmMax).toShort

  private def extractSamplesInFileOrder(chunk: AudioChunk): Vector[Float] = chunk match
    case MonoChunk(samples) => samples
    case _: StereoChunk     => Vector.empty

  private[io] def encodeChunkToPcmBytes(chunk: AudioChunk): Array[Byte] =
    extractSamplesInFileOrder(chunk).flatMap { sample =>
      val pcm = quantiseNormalisedSampleToPcm16(sample)
      List(pcm.toByte, (pcm >> 8).toByte)
    }.toArray

  private def createPcmInputStream(chunks: Vector[AudioChunk]): SequenceInputStream =
    SequenceInputStream(chunks.iterator.map(chunk => ByteArrayInputStream(encodeChunkToPcmBytes(chunk))).asJavaEnumeration)

  private def writeAudioToFile(path: Path, format: AudioFormat, frameCount: Long, chunks: Vector[AudioChunk]): Either[AudioSaveError, Unit] =
    Using(AudioInputStream(createPcmInputStream(chunks), format, frameCount)) { stream =>
      AudioSystem.write(stream, AudioFileFormat.Type.WAVE, path.toFile)
    }.toEither.map(_ => ()).left.map(e => WriteFailure(path, Option(e.getMessage).getOrElse(e.getClass.getSimpleName)))
