package scalasound.model.io

import scalasound.model.domain.AudioChunk.{MonoChunk, StereoChunk}
import AudioLoadError.*
import Pcm16.{fromLittleEndianBytes, toNormalised}
import scalasound.model.domain.{AudioTrack, MonoTrack, StereoTrack}

import java.io.{EOFException, File, IOException}
import javax.sound.sampled.{AudioFormat, AudioInputStream, AudioSystem, UnsupportedAudioFileException}
import scala.util.Using

/** Loads WAV files from the file system, using Java Sound API.
 *
 * Supported subset: PCM signed, 16 bit per sample, little-endian, mono or stereo.
 *
 * Samples are normalised to [-1.0, 1.0): a 16 bit sample `s` becomes
 * `s / 32768f`, so -32768 maps to -1.0 and 32767 to 32767/32768 (see [[Pcm16]]).
 *
 * The last chunk holds the remaining frames, without padding.
 * A valid file without frames is accepted and produces a track with no chunks (duration 0).
 */
object WavAudioReader extends AudioReader:

  private final val FramesPerChunk: Int = 1024

  /** Loads a WAV file from the given path.
   *
   * @param filePath the path to the WAV file
   * @return an `Either` containing the loaded `AudioTrack`
   *         or an `AudioLoadError` if loading failed
   */
  override def load(filePath: String): Either[AudioLoadError, AudioTrack] =
    for
      file <- ensureFileExists(filePath)
      raw <- readRawAudio(file)
      track <- decode(raw)
    yield track

  private def ensureFileExists(filePath: String): Either[AudioLoadError, File] =
    val file = File(filePath)
    Either.cond(file.isFile, file, FileNotFound(filePath))

  private def readRawAudio(file: File): Either[AudioLoadError, RawAudio] =
    Using(AudioSystem.getAudioInputStream(file))(readFromStream).toEither
      .left.map(mapThrowableToAudioLoadError)
      .flatMap(result => result)

  private def readFromStream(stream: AudioInputStream): Either[AudioLoadError, RawAudio] =
    for
      format <- validateAudioFormat(stream.getFormat)
      bytes = stream.readAllBytes()
    yield RawAudio(format, stream.getFrameLength, bytes)

  private def decode(raw: RawAudio): Either[AudioLoadError, AudioTrack] =
    for
      frameCount <- validateAudioData(raw.bytes, raw.declaredFrames, raw.format.layout.frameSize)
    yield buildAudioTrack(raw.format, raw.bytes, frameCount)

  private def validateAudioFormat(format: AudioFormat): Either[AudioLoadError, ValidFormat] =
    for
      _ <- ensurePcm16Encoding(format)
      _ <- ensureLittleEndianByteOrder(format)
      layout <- determineChannelLayout(format)
      _ <- ensureConsistentFrameSize(format, layout)
      sampleRate <- extractPositiveSampleRate(format)
    yield ValidFormat(layout, sampleRate)

  private def ensurePcm16Encoding(format: AudioFormat): Either[AudioLoadError, Unit] =
    (format.getEncoding, format.getSampleSizeInBits) match
      case (AudioFormat.Encoding.PCM_SIGNED, 16) => Right(())
      case (encoding, bits) =>
        Left(UnsupportedFormat(s"only 16 bit signed PCM is supported, found $encoding $bits bit"))

  private def ensureLittleEndianByteOrder(format: AudioFormat): Either[AudioLoadError, Unit] =
    Either.cond(!format.isBigEndian, (), UnsupportedFormat("big-endian samples are not supported"))

  private def determineChannelLayout(format: AudioFormat): Either[AudioLoadError, ChannelLayout] =
    format.getChannels match
      case 1 => Right(ChannelLayout.Mono)
      case 2 => Right(ChannelLayout.Stereo)
      case n => Left(UnsupportedFormat(s"unsupported channel count: $n"))

  private def ensureConsistentFrameSize(format: AudioFormat, layout: ChannelLayout): Either[AudioLoadError, Unit] =
    Either.cond(
      format.getFrameSize == layout.frameSize,
      (),
      CorruptedData(s"inconsistent frame size: ${format.getFrameSize}")
    )

  private def extractPositiveSampleRate(format: AudioFormat): Either[AudioLoadError, Int] =
    Either.cond(
      format.getSampleRate > 0,
      format.getSampleRate.toInt,
      CorruptedData(s"invalid sample rate: ${format.getSampleRate}")
    )

  private def validateAudioData(bytes: Array[Byte], declaredFrames: Long, frameSize: Int): Either[AudioLoadError, Int] =
    val frames = bytes.length / frameSize
    for
      _ <- Either.cond(
        bytes.length % frameSize == 0,
        (),
        CorruptedData(s"data length ${bytes.length} is not a multiple of the frame size $frameSize")
      )
      _ <- Either.cond(
        declaredFrames == AudioSystem.NOT_SPECIFIED || declaredFrames == frames,
        (),
        CorruptedData(s"header declares $declaredFrames frames but the file contains $frames")
      )
    yield frames

  private def buildAudioTrack(format: ValidFormat, bytes: Array[Byte], frameCount: Int): AudioTrack =
    def extractChannel(offset: Int): Vector[Float] =
      extractChannelSamples(bytes, frameCount, format.layout, offset)

    format.layout match
      case ChannelLayout.Mono =>
        MonoTrack(format.sampleRate, partitionIntoChunkBlocks(extractChannel(0)).map[MonoChunk](MonoChunk(_)).toVector)
      case ChannelLayout.Stereo =>
        val chunks = partitionIntoChunkBlocks(extractChannel(0)).zip(partitionIntoChunkBlocks(extractChannel(1))).map[StereoChunk]:
          case (left, right) => StereoChunk(left, right)
        StereoTrack(format.sampleRate, chunks.toVector)

  private def extractChannelSamples(bytes: Array[Byte], frameCount: Int, layout: ChannelLayout, offset: Int): Vector[Float] =
    Vector.tabulate(frameCount)(frame => fromLittleEndianBytes(bytes, frame * layout.channels + offset).toNormalised)

  private def partitionIntoChunkBlocks(samples: Vector[Float]): Iterator[Vector[Float]] =
    samples.grouped(FramesPerChunk)

  private def mapThrowableToAudioLoadError(e: Throwable): AudioLoadError = e match
    case _: UnsupportedAudioFileException => UnsupportedFormat(extractErrorMessage(e))
    case _: EOFException => CorruptedData(s"unexpected end of file: ${extractErrorMessage(e)}")
    case _: IOException => ReadFailure(extractErrorMessage(e))
    case _ => ReadFailure(s"unexpected error: ${extractErrorMessage(e)}")

  private def extractErrorMessage(e: Throwable): String =
    Option(e.getMessage).getOrElse(e.getClass.getSimpleName)

  private final case class ValidFormat(
                                        layout: ChannelLayout,
                                        sampleRate: Int
                                      )
  private final case class RawAudio(format: ValidFormat, declaredFrames: Long, bytes: Array[Byte])
