package scalasound.model.io

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scalasound.model.domain.*
import scalasound.model.domain.AudioChunk.{MonoChunk, StereoChunk}
import scalasound.model.io.WavFixtures.*
import scalasound.model.io.{AudioSaveError, WavAudioWriter}

import java.nio.ByteBuffer
import java.nio.ByteOrder.LITTLE_ENDIAN
import java.nio.charset.StandardCharsets.US_ASCII
import java.nio.file.{Files, Path}

class WavAudioWriterSpec extends AnyFlatSpec with Matchers with EitherValues:

  private val KnownSamples = Vector(0, 16384, -16384, 32767, -32768, 1, -1, 256)

  private def monoTrack(rate: Int, chunks: Seq[Int]*): MonoTrack =
    MonoTrack(rate, chunks.map[MonoChunk](c => MonoChunk(toFloats(c))).toVector).value

  private def toFloats(ints: Seq[Int]): Vector[Float] = ints.map(_ / 32768f).toVector

  private def stereoTrack(rate: Int, chunks: (Seq[Int], Seq[Int])*): StereoTrack =
    StereoTrack(
      rate,
      chunks.map[StereoChunk] { case (l, r) => StereoChunk(toFloats(l), toFloats(r)) }.toVector
    ).value

  private def exported(track: AudioTrack, name: String = "out.wav"): Array[Byte] =
    withTempDir { dir =>
      val path = dir.resolve(name)
      WavAudioWriter.save(track, path) shouldBe Right(())
      Files.readAllBytes(path)
    }

  private def withTempDir[A](test: Path => A): A =
    val dir = Files.createTempDirectory("scalasound-wav-writer-test-")
    try test(dir)
    finally
      Option(dir.toFile.listFiles()).foreach(_.foreach(_.delete()))
      Files.deleteIfExists(dir)

  private def pcmSampleSequence(frames: Int): Vector[Int] =
    Vector.tabulate(frames)(i => ((i * 13) % 20000) - 10000)

  private def parseHeader(bytes: Array[Byte]): Header =
    val buffer        = ByteBuffer.wrap(bytes).order(LITTLE_ENDIAN)
    def tag(): String =
      val raw = new Array[Byte](4)
      buffer.get(raw)
      new String(raw, US_ASCII)
    def u32(): Long = Integer.toUnsignedLong(buffer.getInt)
    def u16(): Int  = java.lang.Short.toUnsignedInt(buffer.getShort)
    Header(
      tag(),
      u32(),
      tag(),
      tag(),
      u32(),
      u16(),
      u16(),
      u32(),
      u32(),
      u16(),
      u16(),
      tag(),
      u32()
    )

  private def invalidSampleTrack(bad: Float): MonoTrack =
    MonoTrack(44100, Vector(MonoChunk(Vector(0.1f, bad, 0.2f)))).value

  "Exporting a mono track" should "produce exactly the bytes of the equivalent 16 bit PCM WAV" in {
    exported(monoTrack(44100, KnownSamples)) shouldBe pcm16Wav(44100, 1, KnownSamples)
  }

  it should "write a valid header with one channel, 16 bit PCM and consistent sizes" in {
    val frames = pcmSampleSequence(1000)
    val bytes  = exported(monoTrack(22050, frames))
    val header = parseHeader(bytes)

    header shouldBe Header(
      "RIFF",
      36L + 2000,
      "WAVE",
      "fmt ",
      16,
      1,
      1,
      22050,
      22050L * 2,
      2,
      16,
      "data",
      2000
    )
    bytes.length shouldBe 44 + 2000
  }

  it should "concatenate multiple chunks preserving the sample order" in {
    val bytes = exported(monoTrack(44100, Seq(1, 2, 3), Seq(-4, -5), Seq(6)))

    bytes shouldBe pcm16Wav(44100, 1, Seq(1, 2, 3, -4, -5, 6))
  }

  it should "produce a header-only file for an empty track or empty chunks" in {
    val emptyTrackBytes = exported(MonoTrack(44100, Vector.empty).value)
    emptyTrackBytes shouldBe pcm16Wav(44100, 1, Seq.empty)
    emptyTrackBytes.length shouldBe 44

    exported(monoTrack(44100, Seq.empty)) shouldBe pcm16Wav(44100, 1, Seq.empty)
  }

  it should "preserve different sample rates and derive the byte rate from them" in {
    Seq(8000, 44100, 96000).foreach { rate =>
      val header = parseHeader(exported(monoTrack(rate, Seq(1, 2, 3)), s"rate-$rate.wav"))

      header.sampleRate shouldBe rate.toLong
      header.byteRate shouldBe rate.toLong * 2
    }
  }

  "Exporting a stereo track" should "interleave the channels as (L0,R0,L1,R1,...) across chunks" in {
    val bytes = exported(stereoTrack(44100, (Seq(1, 2), Seq(-1, -2)), (Seq(3), Seq(-3))))

    bytes shouldBe pcm16Wav(44100, 2, Seq(1, -1, 2, -2, 3, -3))
  }

  it should "write a valid header with two channels and consistent sizes" in {
    val bytes =
      exported(stereoTrack(48000, (pcmSampleSequence(500), pcmSampleSequence(500).reverse)))
    val header = parseHeader(bytes)

    header shouldBe Header(
      "RIFF",
      36L + 2000,
      "WAVE",
      "fmt ",
      16,
      1,
      2,
      48000,
      48000L * 4,
      4,
      16,
      "data",
      2000
    )
    bytes.length shouldBe 44 + 2000
  }

  it should "produce a header-only file for an empty stereo track" in {
    exported(StereoTrack(44100, Vector.empty).value) shouldBe pcm16Wav(44100, 2, Seq.empty)
    exported(stereoTrack(44100, (Seq.empty, Seq.empty)), "empty-chunk.wav") shouldBe pcm16Wav(
      44100,
      2,
      Seq.empty
    )
  }

  it should "preserve different sample rates and derive the byte rate from them" in {
    Seq(8000, 44100, 96000).foreach { rate =>
      val header = parseHeader(exported(stereoTrack(rate, (Seq(1), Seq(2))), s"rate-$rate.wav"))

      header.sampleRate shouldBe rate.toLong
      header.byteRate shouldBe rate.toLong * 4
    }
  }

  "Validation" should "reject an invalid sample in the last chunk without creating a file" in withTempDir {
    tempDir =>
      val chunks: Vector[MonoChunk] =
        Vector(
          MonoChunk(toFloats(pcmSampleSequence(1024))),
          MonoChunk(toFloats(pcmSampleSequence(1024))),
          MonoChunk(Vector(0.1f, Float.NaN))
        )

      val path = tempDir.resolve("late-nan.wav")

      WavAudioWriter.save(MonoTrack(44100, chunks).value, path) should matchPattern {
        case Left(AudioSaveError.InvalidSample(_)) =>
      }
      Files.exists(path) shouldBe false

  }

  it should "leave an existing file untouched when the track holds an invalid sample" in withTempDir {
    tempDir =>
      val path = tempDir.resolve("keep.wav")
      WavAudioWriter.save(monoTrack(44100, Seq(1, 2, 3)), path) shouldBe Right(())

      val before = Files.readAllBytes(path)

      WavAudioWriter.save(invalidSampleTrack(Float.PositiveInfinity), path) should matchPattern {
        case Left(AudioSaveError.InvalidSample(_)) =>
      }
      Files.readAllBytes(path) shouldBe before

  }

  "The data of an exported file" should "be exactly the concatenation of the encoded chunks, in mono" in {

    val chunks: Vector[MonoChunk] =
      Vector(
        MonoChunk(toFloats(pcmSampleSequence(1024))),
        MonoChunk(toFloats(pcmSampleSequence(1024))),
        MonoChunk(toFloats(pcmSampleSequence(300)))
      )

    val bytes = exported(MonoTrack(44100, chunks).value)
    bytes.drop(44).toSeq shouldBe chunks.flatMap(chunk => PcmChunks.encode(chunk).toSeq)
  }

  it should "be exactly the concatenation of the encoded chunks, in stereo" in {

    val chunks: Vector[StereoChunk] = Vector(
      StereoChunk(toFloats(pcmSampleSequence(1024)), toFloats(pcmSampleSequence(1024).reverse)),
      StereoChunk(toFloats(pcmSampleSequence(300)), toFloats(pcmSampleSequence(300).reverse))
    )

    val bytes = exported(StereoTrack(44100, chunks).value)

    bytes.drop(44).toSeq shouldBe chunks.flatMap(chunk => PcmChunks.encode(chunk).toSeq)

  }

  private case class Header(
      riff: String,
      riffSize: Long,
      wave: String,
      fmt: String,
      fmtSize: Long,
      audioFormat: Int,
      channels: Int,
      sampleRate: Long,
      byteRate: Long,
      blockAlign: Int,
      bits: Int,
      data: String,
      dataSize: Long
  )

  "Exporting invalid samples" should "reject NaN with InvalidSample and create no file" in withTempDir {
    tempDir =>
      val path = tempDir.resolve("nan.wav")

      WavAudioWriter.save(invalidSampleTrack(Float.NaN), path) should matchPattern {
        case Left(AudioSaveError.InvalidSample(_)) =>
      }
      Files.exists(path) shouldBe false
  }

  it should "reject positive and negative infinity with InvalidSample" in withTempDir { tempDir =>
    Seq(Float.PositiveInfinity, Float.NegativeInfinity).foreach { bad =>
      val path = tempDir.resolve(s"inf-$bad.wav")

      WavAudioWriter.save(invalidSampleTrack(bad), path) should matchPattern {
        case Left(AudioSaveError.InvalidSample(_)) =>
      }
      Files.exists(path) shouldBe false
    }
  }

  it should "reject a non-finite sample in the right channel of a stereo track" in withTempDir {
    tempDir =>
      val track =
        StereoTrack(44100, Vector(StereoChunk(Vector(0f, 0f), Vector(0f, Float.NaN)))).value

      WavAudioWriter.save(track, tempDir.resolve("stereo-nan.wav")) should matchPattern {
        case Left(AudioSaveError.InvalidSample(_)) =>
      }
  }

  "Exporting to an unusable path" should "return WriteFailure when the parent directory does not exist" in withTempDir {
    tempDir =>
      val path = tempDir.resolve("missing-dir").resolve("out.wav")

      WavAudioWriter.save(monoTrack(44100, Seq(1, 2)), path) should matchPattern {
        case Left(AudioSaveError.WriteFailure(`path`, _)) =>
      }
  }

  it should "return WriteFailure when the path is a directory" in withTempDir { tempDir =>
    val directory = tempDir

    WavAudioWriter.save(monoTrack(44100, Seq(1, 2)), directory) should matchPattern {
      case Left(AudioSaveError.WriteFailure(`directory`, _)) =>
    }
  }

  it should "overwrite an existing file without leaving stale bytes" in withTempDir { tempDir =>
    val path = tempDir.resolve("overwrite.wav")
    WavAudioWriter.save(monoTrack(44100, pcmSampleSequence(100)), path) shouldBe Right(())
    WavAudioWriter.save(monoTrack(44100, Seq(5, 6)), path) shouldBe Right(())

    Files.readAllBytes(path) shouldBe pcm16Wav(44100, 1, Seq(5, 6))
  }

  "The size limits" should "accept the largest data size Java Sound writes correctly (2^32 - 44 bytes)" in {
    WavAudioWriter.calculateDataSize(2147483626L, 1) shouldBe Right(4294967252L)
    WavAudioWriter.calculateDataSize(1073741813L, 2) shouldBe Right(4294967252L)
  }

  it should "return FileTooLarge just above the limit and for huge frame counts" in {
    WavAudioWriter.calculateDataSize(2147483627L, 1) should matchPattern {
      case Left(AudioSaveError.FileTooLarge(_)) =>
    }
    WavAudioWriter.calculateDataSize(1073741814L, 2) should matchPattern {
      case Left(AudioSaveError.FileTooLarge(_)) =>
    }
    WavAudioWriter.calculateDataSize(3000000000L, 2) should matchPattern {
      case Left(AudioSaveError.FileTooLarge(_)) =>
    }
  }

  it should "return FileTooLarge instead of overflowing Long arithmetic" in {
    WavAudioWriter.calculateDataSize(Long.MaxValue, 1) should matchPattern {
      case Left(AudioSaveError.FileTooLarge(_)) =>
    }
    WavAudioWriter.calculateDataSize(Long.MaxValue, 2) should matchPattern {
      case Left(AudioSaveError.FileTooLarge(_)) =>
    }
  }

  it should "reject a sample rate a float cannot represent exactly, and accept 2^24 Hz" in withTempDir {
    tempDir =>
      val veryFast = StereoTrack(Int.MaxValue, Vector.empty).value
      val tooFast  = StereoTrack((1 << 24) + 1, Vector.empty).value

      WavAudioWriter.save(veryFast, tempDir.resolve("fast.wav")) should matchPattern {
        case Left(AudioSaveError.InvalidTrack(_)) =>
      }
      WavAudioWriter.save(tooFast, tempDir.resolve("fast2.wav")) should matchPattern {
        case Left(AudioSaveError.InvalidTrack(_)) =>
      }
      WavAudioWriter.save(
        StereoTrack(1 << 24, Vector.empty).value,
        tempDir.resolve("limit.wav")
      ) shouldBe Right(())
  }

  "Exporting an unknown track type" should "return InvalidTrack" in withTempDir { tempDir =>
    val custom = new AudioTrack:
      override def sampleRate: SampleRate     = SampleRate.from(44100).value
      override def chunks: Vector[AudioChunk] = Vector.empty

    WavAudioWriter.save(custom, tempDir.resolve("custom.wav")) should matchPattern {
      case Left(AudioSaveError.InvalidTrack(_)) =>
    }
  }
