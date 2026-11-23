package scalasound.model.io

import scalasound.model.domain.{AudioDomainError, AudioTrack, MonoTrack, StereoTrack}
import scalasound.model.io.AudioLoadError.*

import java.io.{EOFException, File, IOException}
import javax.sound.sampled.{AudioInputStream, AudioSystem, UnsupportedAudioFileException}
import scala.util.{Failure, Success, Using}

/** Loads WAV files from the file system, using Java Sound API.
  *
  * Supported subset: PCM signed, 16 bit per sample, little-endian, mono or stereo (see
  * [[WavFormat]]).
  *
  * Samples are normalised to [-1.0, 1.0): a 16 bit sample `s` becomes `s / 32768f`, so -32768 maps
  * to -1.0 and 32767 to 32767/32768 (see [[Pcm16]]).
  *
  * The last chunk holds the remaining frames, without padding. A valid file without frames is
  * accepted and produces a track with no chunks (duration 0).
  */
object WavAudioReader extends AudioReader:

  private final val FramesPerChunk: Int = 1024

  /** Loads a WAV file from the given path.
    *
    * @param filePath
    *   the path to the WAV file
    * @return
    *   an `Either` containing the loaded `AudioTrack` or an `AudioLoadError` if loading failed
    */
  override def load(filePath: String): Either[AudioLoadError, AudioTrack] =
    for
      file  <- ensureFileExists(filePath)
      raw   <- readRawAudio(file)
      track <- decode(raw)
    yield track

  private def ensureFileExists(filePath: String): Either[AudioLoadError, File] =
    val file = File(filePath)
    Either.cond(file.isFile, file, FileNotFound(filePath))

  private def readRawAudio(file: File): Either[AudioLoadError, RawAudio] =
    Using(AudioSystem.getAudioInputStream(file))(readFromStream) match
      case Success(result)    => result
      case Failure(exception) => Left(fromThrowable(exception))

  private def readFromStream(stream: AudioInputStream): Either[AudioLoadError, RawAudio] =
    WavFormat
      .from(stream.getFormat)
      .map(format => RawAudio(format, stream.getFrameLength, stream.readAllBytes()))

  private def fromThrowable(e: Throwable): AudioLoadError = e match
    case _: UnsupportedAudioFileException => UnsupportedFormat(describe(e))
    case _: EOFException => CorruptedData(s"unexpected end of file: ${describe(e)}")
    case _: IOException  => ReadFailure(describe(e))
    case _               => ReadFailure(s"unexpected error: ${describe(e)}")

  private def describe(e: Throwable): String =
    Option(e.getMessage).getOrElse(e.getClass.getSimpleName)

  private def decode(raw: RawAudio): Either[AudioLoadError, AudioTrack] =
    for
      frameCount <- validateAudioData(raw.bytes, raw.declaredFrames, raw.format.layout.frameSize)
      track      <- buildAudioTrack(raw.format, raw.bytes, frameCount)
    yield track

  private def validateAudioData(
      bytes: Array[Byte],
      declaredFrames: Long,
      frameSize: Int
  ): Either[AudioLoadError, Int] =
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

  private def buildAudioTrack(
      format: WavFormat,
      bytes: Array[Byte],
      frameCount: Int
  ): Either[AudioLoadError, AudioTrack] =
    format.layout match
      case ChannelLayout.Mono =>
        val chunks = PcmChunks.decodeMono(bytes, frameCount, FramesPerChunk)
        Right(MonoTrack.fromValidated(format.sampleRate, chunks))
      case ChannelLayout.Stereo =>
        val chunks = PcmChunks.decodeStereo(bytes, frameCount, FramesPerChunk)
        StereoTrack.fromValidated(format.sampleRate, chunks).left.map(fromDomainError)

  private def fromDomainError(error: AudioDomainError): AudioLoadError = error match
    case AudioDomainError.InvalidSampleRate(rate) =>
      CorruptedData(s"invalid sample rate: $rate")
    case AudioDomainError.DifferentLengthsStereoChannels(idx, leftLen, rightLen) =>
      CorruptedData(s"unbalanced stereo channels in chunk $idx: left=$leftLen, right=$rightLen")

  private final case class RawAudio(format: WavFormat, declaredFrames: Long, bytes: Array[Byte])
