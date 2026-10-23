package scalasound.io

import java.io.{EOFException, File, IOException}
import javax.sound.sampled.{AudioFormat, AudioInputStream, AudioSystem, UnsupportedAudioFileException}
import scala.util.{Failure, Success, Using}
import scalasound.domain.AudioChunk.{MonoChunk, StereoChunk}
import scalasound.domain.{AudioTrack, MonoTrack, StereoTrack}
import scalasound.io.AudioLoadError.*

/** Loads WAV files from the file system, using the standard Java Sound API.
  *
  * Supported subset: PCM signed, 16 bit per sample, little-endian, mono or stereo.
  *
  * Samples are normalised to [-1.0, 1.0): a 16 bit sample `s` becomes
  * `s / 32768f`, so -32768 maps to -1.0 and 32767 to 32767/32768.
  *
  * The last chunk holds the remaining frames, without padding.
  * A valid file without frames is accepted and produces a track with no chunks (duration 0).
  */
object WavAudioReader extends AudioReader:

  private final val FramesPerChunk: Int = 1024
  private final val BytesPerSample = 2
  private final val Normalization = 32768f

  /** Loads a WAV file from the given path.
   *
   *  @param filePath the path to the WAV file
   *  @return an `Either` containing the loaded `AudioTrack`
   *          or an `AudioLoadError` if loading failed
   */
  override def load(filePath: String): Either[AudioLoadError, AudioTrack] =
    val file = File(filePath)
    if !file.isFile then Left(FileNotFound(filePath))
    else
      Using(AudioSystem.getAudioInputStream(file))(readTrack) match
        case Success(result) => 
          result
        case Failure(e: UnsupportedAudioFileException) => 
          Left(UnsupportedFormat(messageOf(e)))
        case Failure(e: EOFException) => 
          Left(CorruptedData(s"unexpected end of file: ${messageOf(e)}"))
        case Failure(e: IOException) => 
          Left(ReadFailure(messageOf(e)))
        case Failure(e) => 
          throw e

  private def messageOf(e: Exception): String =
    Option(e.getMessage).getOrElse(e.getClass.getSimpleName)

  private def readTrack(stream: AudioInputStream): Either[AudioLoadError, AudioTrack] =
    val format = stream.getFormat
    for
      sampleRate <- validateFormat(format)
      bytes = stream.readAllBytes()
      frameCount <- validateData(bytes, stream.getFrameLength, format.getFrameSize)
    yield
      if format.getChannels == 1 then buildMonoTrack(sampleRate, bytes, frameCount)
      else buildStereoTrack(sampleRate, bytes, frameCount)

  private def validateFormat(format: AudioFormat): Either[AudioLoadError, Int] =
    if format.getEncoding != AudioFormat.Encoding.PCM_SIGNED || format.getSampleSizeInBits != 16 then
      Left(UnsupportedFormat(
        s"only 16 bit signed PCM is supported, found ${format.getEncoding} ${format.getSampleSizeInBits} bit"
      ))
    else if format.isBigEndian then Left(UnsupportedFormat("big-endian samples are not supported"))
    else if format.getChannels != 1 && format.getChannels != 2 then
      Left(UnsupportedFormat(s"unsupported channel count: ${format.getChannels}"))
    else if format.getFrameSize != format.getChannels * BytesPerSample then
      Left(CorruptedData(s"inconsistent frame size: ${format.getFrameSize}"))
    else if format.getSampleRate <= 0 then Left(CorruptedData(s"invalid sample rate: ${format.getSampleRate}"))
    else Right(format.getSampleRate.toInt)

  private def validateData(bytes: Array[Byte], declaredFrames: Long, frameSize: Int): Either[AudioLoadError, Int] =
    val frames = bytes.length / frameSize
    if bytes.length % frameSize != 0 then
      Left(CorruptedData(s"data length ${bytes.length} is not a multiple of the frame size $frameSize"))
    else if declaredFrames != AudioSystem.NOT_SPECIFIED && declaredFrames != frames then
      Left(CorruptedData(s"header declares $declaredFrames frames but the file contains $frames"))
    else Right(frames)

  /** Little-endian signed 16 bit sample at the given sample index, normalised Float. */
  private def sampleAt(bytes: Array[Byte], index: Int): Float =
    val low = bytes(index * BytesPerSample) & 0xff
    val high = bytes(index * BytesPerSample + 1) // keeps the sign
    ((high << 8) | low) / Normalization

  private def chunkStarts(frameCount: Int): Vector[Int] =
    Vector.range(0, frameCount, FramesPerChunk)

  private def buildMonoTrack(sampleRate: Int, bytes: Array[Byte], frameCount: Int): MonoTrack =
    val samples = Vector.tabulate(frameCount)(sampleAt(bytes, _))
    val chunks = chunkStarts(frameCount).map[MonoChunk](start => MonoChunk(samples.slice(start, start + FramesPerChunk)))
    MonoTrack(sampleRate, chunks)

  private def buildStereoTrack(sampleRate: Int, bytes: Array[Byte], frameCount: Int): StereoTrack =
    val left = Vector.tabulate(frameCount)(frame => sampleAt(bytes, 2 * frame))
    val right = Vector.tabulate(frameCount)(frame => sampleAt(bytes, 2 * frame + 1))
    val chunks = chunkStarts(frameCount).map[StereoChunk] { start =>
      StereoChunk(left.slice(start, start + FramesPerChunk), right.slice(start, start + FramesPerChunk))
    }
    StereoTrack(sampleRate, chunks)
