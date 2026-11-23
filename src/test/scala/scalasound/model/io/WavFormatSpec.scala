package scalasound.model.io

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scalasound.model.domain.SampleRate

import javax.sound.sampled.{AudioFormat, AudioSystem}

class WavFormatSpec extends AnyFlatSpec with Matchers with EitherValues:

  private def format(
      encoding: AudioFormat.Encoding = AudioFormat.Encoding.PCM_SIGNED,
      sampleRate: Float = 44100f,
      bits: Int = 16,
      channels: Int = 1,
      frameSize: Int = -1,
      bigEndian: Boolean = false
  ): AudioFormat =
    val size = if frameSize >= 0 then frameSize else channels * bits / 8
    AudioFormat(encoding, sampleRate, bits, channels, size, sampleRate, bigEndian)

  private def sampleRate(hertz: Int): SampleRate = SampleRate.from(hertz).value

  "A supported format" should "be accepted as mono with its sample rate" in {
    WavFormat.from(format(sampleRate = 22050f)) shouldBe
      Right(WavFormat(ChannelLayout.Mono, sampleRate(22050)))
  }

  it should "be accepted as stereo with its sample rate" in {
    WavFormat.from(format(channels = 2)) shouldBe Right(
      WavFormat(ChannelLayout.Stereo, sampleRate(44100))
    )
  }

  "An unsupported encoding" should "be rejected for 8 bit, 24 bit, unsigned and float PCM" in {
    val unsupported = Seq(
      format(bits = 8),
      format(bits = 24),
      format(encoding = AudioFormat.Encoding.PCM_UNSIGNED),
      format(encoding = AudioFormat.Encoding.PCM_FLOAT, bits = 32),
      format(encoding = AudioFormat.Encoding.ULAW, bits = 8)
    )
    unsupported.foreach: candidate =>
      WavFormat.from(candidate) should matchPattern {
        case Left(AudioLoadError.UnsupportedFormat(_)) =>
      }
  }

  "Big-endian samples" should "be rejected as unsupported" in {
    WavFormat.from(format(bigEndian = true)) should matchPattern {
      case Left(AudioLoadError.UnsupportedFormat(_)) =>
    }
  }

  "An unsupported channel count" should "be rejected for zero, three and negative values" in {
    Seq(0, 3, 6, -1).foreach: channels =>
      WavFormat.from(format(channels = channels, frameSize = 2)) should matchPattern {
        case Left(AudioLoadError.UnsupportedFormat(_)) =>
      }
  }

  "A frame size that does not match the channels" should "be reported as corrupted data" in {
    WavFormat.from(format(channels = 2, frameSize = 2)) should matchPattern {
      case Left(AudioLoadError.CorruptedData(_)) =>
    }
    WavFormat.from(format(frameSize = 4)) should matchPattern {
      case Left(AudioLoadError.CorruptedData(_)) =>
    }
  }

  "An invalid sample rate" should "be reported as corrupted data for zero, negative and unspecified values" in {
    val invalid = Seq(0f, -44100f, AudioSystem.NOT_SPECIFIED.toFloat, Float.NaN, 0.5f)
    invalid.foreach: sampleRate =>
      WavFormat.from(format(sampleRate = sampleRate)) should matchPattern {
        case Left(AudioLoadError.CorruptedData(_)) =>
      }
  }

  it should "report the first problem found: encoding before byte order before channels" in {
    WavFormat.from(format(bits = 8, bigEndian = true, channels = 3)) should matchPattern {
      case Left(AudioLoadError.UnsupportedFormat(reason)) if reason.contains("16 bit") =>
    }
    WavFormat.from(format(bigEndian = true, channels = 3)) should matchPattern {
      case Left(AudioLoadError.UnsupportedFormat(reason)) if reason.contains("big-endian") =>
    }
  }

  "The channel layouts" should "be found from the number of channels" in {
    ChannelLayout.fromChannelCount(1) shouldBe Some(ChannelLayout.Mono)
    ChannelLayout.fromChannelCount(2) shouldBe Some(ChannelLayout.Stereo)
    Seq(0, 3, -1).foreach(ChannelLayout.fromChannelCount(_) shouldBe None)
  }
