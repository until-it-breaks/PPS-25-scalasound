package scalasound.model.io.wav

import scalasound.model.domain.SampleRate
import scalasound.model.io.error.AudioLoadError
import scalasound.model.io.error.AudioLoadError.*

import javax.sound.sampled.AudioFormat

/** The WAV format accepted by [[WavAudioReader]]: 16-bit signed little-endian PCM, mono or stereo.
  *
  * @param layout
  *   the channel layout
  * @param sampleRate
  *   the validated sample rate
  */
private[wav] final case class WavFormat(layout: ChannelLayout, sampleRate: SampleRate)

private[wav] object WavFormat:

  /** Validates a Java Sound format with the supported subset.
    *
    * @return
    *   the supported format, or [[AudioLoadError.UnsupportedFormat]] for a well-formed but
    *   unsupported format and [[AudioLoadError.CorruptedData]] for inconsistent header values
    */
  def from(format: AudioFormat): Either[AudioLoadError, WavFormat] =
    for
      _          <- ensurePcm16Encoding(format)
      _          <- ensureLittleEndian(format)
      layout     <- determineLayout(format)
      _          <- ensureConsistentFrameSize(format, layout)
      sampleRate <- extractSampleRate(format)
    yield WavFormat(layout, sampleRate)

  private def ensurePcm16Encoding(format: AudioFormat): Either[AudioLoadError, Unit] =
    (format.getEncoding, format.getSampleSizeInBits) match
      case (AudioFormat.Encoding.PCM_SIGNED, Pcm16.BitsPerSample) => Right(())
      case (encoding, bits)                                       =>
        Left(UnsupportedFormat(s"only 16 bit signed PCM is supported, found $encoding $bits bit"))

  private def ensureLittleEndian(format: AudioFormat): Either[AudioLoadError, Unit] =
    Either.cond(!format.isBigEndian, (), UnsupportedFormat("big-endian samples are not supported"))

  private def determineLayout(format: AudioFormat): Either[AudioLoadError, ChannelLayout] =
    ChannelLayout
      .fromChannelCount(format.getChannels)
      .toRight(UnsupportedFormat(s"unsupported channel count: ${format.getChannels}"))

  private def ensureConsistentFrameSize(
      format: AudioFormat,
      layout: ChannelLayout
  ): Either[AudioLoadError, Unit] =
    Either.cond(
      format.getFrameSize == layout.frameSize,
      (),
      CorruptedData(s"inconsistent frame size: ${format.getFrameSize}")
    )

  private def extractSampleRate(format: AudioFormat): Either[AudioLoadError, SampleRate] =
    SampleRate
      .from(format.getSampleRate.toInt)
      .left
      .map(_ => CorruptedData(s"invalid sample rate: ${format.getSampleRate}"))
