package scalasound.model.io

import cats.effect.IO
import scalasound.model.domain.{AudioChunk, AudioTrack, MonoTrack, StereoTrack}
import scalasound.model.io.AudioSaveError.*
import scalasound.model.io.Pcm16.{BitsPerSample, BytesPerSample, isRepresentable}

import java.io.{ByteArrayInputStream, SequenceInputStream}
import java.nio.file.Path
import javax.sound.sampled.{AudioFileFormat, AudioFormat, AudioInputStream, AudioSystem}
import scala.jdk.CollectionConverters.*
import scala.util.{Try, Using}

/** Saves tracks as WAV files in the file system, using the Java Sound API.
  *
  * Supported subset: PCM signed, 16 bit per sample, little-endian, mono or stereo. It is the same
  * subset accepted by [[WavAudioReader]]
  *
  * A normalised sample `x` becomes `clamp(round(x * 32768), -32768, 32767)` (see [[Pcm16]]), that
  * is the inverse of the reader's `s / 32768f`. So, `0.0 -> 0`, `-1.0 -> -32768` and
  * `+1.0 -> 32767` (clipped). Values outside [-1.0, 1.0) (e.g. produced by effects) are clipped to
  * the extremes. Samples that are NaN or infinite are rejected with
  * [[AudioSaveError.InvalidSample]] before any byte is written.
  *
  * A file, that already exists in the destination path, will be overwritten.
  */
object WavAudioWriter extends AudioWriter:

  private final val MaxSampleRateRepresentedByFloat = 1 << 24          // (2^24 Hz)
  private final val MaxDataSizeHandledByPCM         = 0xffffffffL - 43 // (2^32 - 44) bytes

  override def save(track: AudioTrack, path: Path): IO[Either[AudioSaveError, Unit]] =
    IO.delay(prepare(track)).flatMap:
      case Right(format) => writeAudioToFile(path, format, track.frameCount, track.chunks)
      case Left(error)   => IO.pure(Left(error))

  private def prepare(track: AudioTrack): Either[AudioSaveError, AudioFormat] =
    for
      layout <- determineChannelLayout(track)
      format <- buildAudioFormat(layout, track.sampleRate.value)
      _      <- calculateDataSize(track.frameCount, layout.channels)
      _      <- validateFiniteSamples(track)
    yield format

  private def determineChannelLayout(track: AudioTrack): Either[AudioSaveError, ChannelLayout] =
    track match
      case _: MonoTrack   => Right(ChannelLayout.Mono)
      case _: StereoTrack => Right(ChannelLayout.Stereo)
      case other => Left(InvalidTrack(s"unsupported track type: ${other.getClass.getSimpleName}"))

  private def buildAudioFormat(
      layout: ChannelLayout,
      sampleRate: Int
  ): Either[AudioSaveError, AudioFormat] =
    Either.cond(
      sampleRate <= MaxSampleRateRepresentedByFloat,
      AudioFormat(
        AudioFormat.Encoding.PCM_SIGNED,
        sampleRate.toFloat,
        BitsPerSample,
        layout.channels,
        layout.frameSize,
        sampleRate.toFloat,
        false
      ),
      InvalidTrack(
        s"sample rate $sampleRate exceeds the supported maximum of $MaxSampleRateRepresentedByFloat Hz"
      )
    )

  private[io] def calculateDataSize(frameCount: Long, channels: Int): Either[AudioSaveError, Long] =
    (for
      size <- Try(Math.multiplyExact(frameCount, (channels * BytesPerSample).toLong)).toOption
      if size <= MaxDataSizeHandledByPCM
    yield size).toRight(
      FileTooLarge(
        s"$frameCount frames on $channels channel(s) exceed the limit of $MaxDataSizeHandledByPCM data bytes"
      )
    )

  private def validateFiniteSamples(track: AudioTrack): Either[AudioSaveError, Unit] =
    val firstInvalid = (for
      chunk   <- track.chunks.iterator
      channel <- chunk.channels.iterator
      sample  <- channel.iterator
      if !sample.isRepresentable
    yield sample).nextOption()

    firstInvalid.toLeft(()).left.map(bad => InvalidSample(s"non-finite sample value: $bad"))

  private def writeAudioToFile(
      path: Path,
      format: AudioFormat,
      frameCount: Long,
      chunks: Vector[AudioChunk]
  ): IO[Either[AudioSaveError, Unit]] =
    IO.blocking(writeBlocking(path, format, frameCount, chunks)).attempt.map:
      case Right(_)        => Right(())
      case Left(exception) =>
        Left(
          WriteFailure(
            path,
            Option(exception.getMessage).getOrElse(exception.getClass.getSimpleName)
          )
        )

  private def writeBlocking(
      path: Path,
      format: AudioFormat,
      frameCount: Long,
      chunks: Vector[AudioChunk]
  ): Unit =
    Using.resource(AudioInputStream(createPcmInputStream(chunks), format, frameCount)): stream =>
      AudioSystem.write(stream, AudioFileFormat.Type.WAVE, path.toFile)
      ()

  private def createPcmInputStream(chunks: Vector[AudioChunk]): SequenceInputStream =
    SequenceInputStream(
      chunks.iterator.map(chunk => ByteArrayInputStream(PcmChunks.encode(chunk))).asJavaEnumeration
    )
