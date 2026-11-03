package scalasound.model.io

import java.nio.ByteBuffer
import java.nio.ByteOrder.LITTLE_ENDIAN
import java.nio.charset.StandardCharsets.US_ASCII
import java.nio.file.{Files, Path}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scalasound.model.domain.AudioChunk.{MonoChunk, StereoChunk}
import WavFixtures.*
import scalasound.model.domain.{AudioChunk, AudioTrack, MonoTrack, StereoTrack}
import scalasound.model.io.{AudioSaveError, AudioWriter, WavAudioWriter}

class WavAudioWriterSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach:

  private var tempDir: Path = scala.compiletime.uninitialized

  private val KnownSamples = Vector(0, 16384, -16384, 32767, -32768, 1, -1, 256)

  override def beforeEach(): Unit =
    tempDir = Files.createTempDirectory("scalasound-wav-writer-test")

  override def afterEach(): Unit =
    tempDir.toFile.listFiles().foreach(_.delete())
    Files.delete(tempDir)

  private def toFloats(ints: Seq[Int]): Vector[Float] = ints.map(_ / 32768f).toVector

  private def monoTrack(rate: Int, chunks: Seq[Int]*): MonoTrack =
    MonoTrack(rate, chunks.map[MonoChunk](c => MonoChunk(toFloats(c))).toVector)

  private def stereoTrack(rate: Int, chunks: (Seq[Int], Seq[Int])*): StereoTrack =
    StereoTrack(rate, chunks.map[StereoChunk] { case (l, r) => StereoChunk(toFloats(l), toFloats(r)) }.toVector)

  private def exported(track: AudioTrack, name: String = "out.wav"): Array[Byte] =
    val path = tempDir.resolve(name)
    WavAudioWriter.save(track, path) shouldBe Right(())
    Files.readAllBytes(path)

  private def interleave(left: Seq[Int], right: Seq[Int]): Seq[Int] =
    left.zip(right).flatMap { case (l, r) => Seq(l, r) }

  private def ramp(frames: Int): Vector[Int] =
    Vector.tabulate(frames)(i => ((i * 13) % 20000) - 10000)

  private case class Header(
      riff: String, riffSize: Long, wave: String, fmt: String, fmtSize: Long, audioFormat: Int,
      channels: Int, sampleRate: Long, byteRate: Long, blockAlign: Int, bits: Int, data: String, dataSize: Long
  )

  private def parseHeader(bytes: Array[Byte]): Header =
    val buffer = ByteBuffer.wrap(bytes).order(LITTLE_ENDIAN)
    def tag(): String =
      val raw = new Array[Byte](4)
      buffer.get(raw)
      new String(raw, US_ASCII)
    def u32(): Long = Integer.toUnsignedLong(buffer.getInt)
    def u16(): Int = java.lang.Short.toUnsignedInt(buffer.getShort)
    Header(tag(), u32(), tag(), tag(), u32(), u16(), u16(), u32(), u32(), u16(), u16(), tag(), u32())

  "Exporting a mono track" should "produce exactly the bytes of the equivalent 16 bit PCM WAV" in {
    val bytes = exported(monoTrack(44100, KnownSamples))

    bytes shouldBe pcm16Wav(44100, 1, KnownSamples)
  }

  it should "write a valid header with one channel, 16 bit PCM and consistent sizes" in {
    val frames = ramp(1000)
    val bytes = exported(monoTrack(22050, frames))
    val header = parseHeader(bytes)

    header shouldBe Header("RIFF", 36L + 2000, "WAVE", "fmt ", 16, 1, 1, 22050, 22050L * 2, 2, 16, "data", 2000)
    bytes.length shouldBe 44 + 2000
  }

  it should "concatenate the chunks preserving the sample order" in {
    val bytes = exported(monoTrack(44100, Seq(1, 2, 3), Seq(-4, -5), Seq(6)))

    bytes shouldBe pcm16Wav(44100, 1, Seq(1, 2, 3, -4, -5, 6))
  }

  it should "handle a number of frames that is not a multiple of 1024" in {
    val frames = ramp(2500)
    val chunks = frames.grouped(1024).toSeq
    val bytes = exported(monoTrack(44100, chunks*))

    bytes shouldBe pcm16Wav(44100, 1, frames)
  }

  it should "handle a track with a single sample" in {
    exported(monoTrack(44100, Seq(-1234))) shouldBe pcm16Wav(44100, 1, Seq(-1234))
  }

  it should "produce a header-only file for a track without chunks" in {
    val bytes = exported(MonoTrack(44100, Vector.empty))

    bytes shouldBe pcm16Wav(44100, 1, Seq.empty)
    bytes.length shouldBe 44
  }

  it should "produce a header-only file for a track with an empty chunk" in {
    exported(monoTrack(44100, Seq.empty)) shouldBe pcm16Wav(44100, 1, Seq.empty)
  }

  it should "preserve different sample rates and derive the byte rate from them" in {
    Seq(8000, 22050, 44100, 48000, 96000).foreach { rate =>
      val header = parseHeader(exported(monoTrack(rate, Seq(1, 2, 3)), s"rate-$rate.wav"))

      header.sampleRate shouldBe rate.toLong
      header.byteRate shouldBe rate.toLong * 2
    }
  }

  "Exporting a stereo track" should "interleave the channels as (L0,R0,L1,R1,...)" in {
    val left = Seq(1, 2, 3, 4)
    val right = Seq(-1, -2, -3, -4)

    exported(stereoTrack(44100, (left, right))) shouldBe pcm16Wav(44100, 2, Seq(1, -1, 2, -2, 3, -3, 4, -4))
  }

  it should "write a valid header with two channels and consistent sizes" in {
    val bytes = exported(stereoTrack(48000, (ramp(500), ramp(500).reverse)))
    val header = parseHeader(bytes)

    header shouldBe Header("RIFF", 36L + 2000, "WAVE", "fmt ", 16, 1, 2, 48000, 48000L * 4, 4, 16, "data", 2000)
    bytes.length shouldBe 44 + 2000
  }

  it should "not swap the left and right channels" in {
    val data = exported(stereoTrack(44100, (Seq(1000, 1000), Seq(-2000, -2000)))).drop(44)

    data shouldBe pcm16Data(Seq(1000, -2000, 1000, -2000))
  }

  it should "keep the frame order across chunks and count frames per channel" in {
    val left = ramp(2500)
    val right = left.map(-_)
    val chunks = left.grouped(1024).zip(right.grouped(1024)).toSeq
    val bytes = exported(stereoTrack(44100, chunks *))

    bytes shouldBe pcm16Wav(44100, 2, interleave(left, right))
    parseHeader(bytes).dataSize shouldBe 2500L * 4
  }

  it should "handle a single frame" in {
    exported(stereoTrack(44100, (Seq(7), Seq(-7)))) shouldBe pcm16Wav(44100, 2, Seq(7, -7))
  }

  it should "produce a header-only file for an empty track" in {
    exported(StereoTrack(44100, Vector.empty)) shouldBe pcm16Wav(44100, 2, Seq.empty)

    exported(stereoTrack(44100, (Seq.empty, Seq.empty)), "empty-chunk.wav") shouldBe pcm16Wav(44100, 2, Seq.empty)
  }

  it should "preserve different sample rates and derive the byte rate from them" in {
    Seq(8000, 44100, 96000).foreach { rate =>
      val header = parseHeader(exported(stereoTrack(rate, (Seq(1), Seq(2))), s"rate-$rate.wav"))

      header.sampleRate shouldBe rate.toLong
      header.byteRate shouldBe rate.toLong * 4
    }
  }

  "Quantisation" should "map the reference values to the expected PCM integers" in {
    val expected = Seq(
      0f -> 0,
      0.5f -> 16384,
      -0.5f -> -16384,
      -1f -> -32768,
      1f -> 32767, // +1.0 is not representable: clipped
      32767 / 32768f -> 32767,
      -32767 / 32768f -> -32767,
      1 / 32768f -> 1,
      -1 / 32768f -> -1
    )

    expected.foreach { case (sample, pcm) => WavAudioWriter.quantiseNormalisedSampleToPcm16(sample).toInt shouldBe pcm }
  }

  it should "round to the nearest integer, ties towards +infinity" in {
    WavAudioWriter.quantiseNormalisedSampleToPcm16(0.4f / 32768f).toInt shouldBe 0
    WavAudioWriter.quantiseNormalisedSampleToPcm16(0.6f / 32768f).toInt shouldBe 1
    WavAudioWriter.quantiseNormalisedSampleToPcm16(-0.6f / 32768f).toInt shouldBe -1
    WavAudioWriter.quantiseNormalisedSampleToPcm16(0.5f / 32768f).toInt shouldBe 1
    WavAudioWriter.quantiseNormalisedSampleToPcm16(-0.5f / 32768f).toInt shouldBe 0
  }

  it should "clip values close to the extremes without overflowing" in {
    WavAudioWriter.quantiseNormalisedSampleToPcm16(0.99999f).toInt shouldBe 32767
    WavAudioWriter.quantiseNormalisedSampleToPcm16(-0.99999f).toInt shouldBe -32768
  }

  it should "clip values outside the normalised range" in {
    WavAudioWriter.quantiseNormalisedSampleToPcm16(1.5f).toInt shouldBe 32767
    WavAudioWriter.quantiseNormalisedSampleToPcm16(-1.5f).toInt shouldBe -32768
    WavAudioWriter.quantiseNormalisedSampleToPcm16(1e30f).toInt shouldBe 32767
    WavAudioWriter.quantiseNormalisedSampleToPcm16(-1e30f).toInt shouldBe -32768
    WavAudioWriter.quantiseNormalisedSampleToPcm16(Float.MaxValue).toInt shouldBe 32767
    WavAudioWriter.quantiseNormalisedSampleToPcm16(Float.MinValue).toInt shouldBe -32768
  }

  it should "write clipped values to the file for out-of-range samples" in {
    val track = MonoTrack(44100, Vector(MonoChunk(Vector(2.0f, -2.0f, 1.0f, -1.0f))))

    exported(track) shouldBe pcm16Wav(44100, 1, Seq(32767, -32768, 32767, -32768))
  }

  it should "write samples in little-endian two's complement" in {
    // 0x0102 = 258 -> bytes 02 01 ; -2 = 0xFFFE -> bytes FE FF
    val data = exported(monoTrack(44100, Seq(258, -2))).drop(44)

    data shouldBe Array[Byte](0x02, 0x01, 0xfe.toByte, 0xff.toByte)
  }

  private def invalidSampleTrack(bad: Float): MonoTrack =
    MonoTrack(44100, Vector(MonoChunk(Vector(0.1f, bad, 0.2f))))

  "Exporting invalid samples" should "reject NaN with InvalidSample and create no file" in {
    val path = tempDir.resolve("nan.wav")

    WavAudioWriter.save(invalidSampleTrack(Float.NaN), path) should matchPattern { case Left(AudioSaveError.InvalidSample(_)) => }
    Files.exists(path) shouldBe false
  }

  it should "reject positive and negative infinity with InvalidSample" in {
    Seq(Float.PositiveInfinity, Float.NegativeInfinity).foreach { bad =>
      val path = tempDir.resolve(s"inf-$bad.wav")

      WavAudioWriter.save(invalidSampleTrack(bad), path) should matchPattern { case Left(AudioSaveError.InvalidSample(_)) => }
      Files.exists(path) shouldBe false
    }
  }

  it should "reject a non-finite sample in the right channel of a stereo track" in {
    val track = StereoTrack(44100, Vector(StereoChunk(Vector(0f, 0f), Vector(0f, Float.NaN))))

    WavAudioWriter.save(track, tempDir.resolve("stereo-nan.wav")) should matchPattern { case Left(AudioSaveError.InvalidSample(_)) => }
  }

  "Exporting to an unusable path" should "return WriteFailure when the parent directory does not exist" in {
    val path = tempDir.resolve("missing-dir").resolve("out.wav")

    WavAudioWriter.save(monoTrack(44100, Seq(1, 2)), path) should matchPattern { case Left(AudioSaveError.WriteFailure(`path`, _)) => }
  }

  it should "return WriteFailure when the path is a directory" in {
    val directory = tempDir

    WavAudioWriter.save(monoTrack(44100, Seq(1, 2)), directory) should matchPattern { case Left(AudioSaveError.WriteFailure(`directory`, _)) => }
  }

  it should "overwrite an existing file without leaving stale bytes" in {
    val path = tempDir.resolve("overwrite.wav")
    WavAudioWriter.save(monoTrack(44100, ramp(100)), path) shouldBe Right(())
    WavAudioWriter.save(monoTrack(44100, Seq(5, 6)), path) shouldBe Right(())

    Files.readAllBytes(path) shouldBe pcm16Wav(44100, 1, Seq(5, 6))
  }

  "The size limits" should "accept the largest data size Java Sound writes correctly (2^32 - 44 bytes)" in {
    WavAudioWriter.calculateDataSize(2147483626L, 1) shouldBe Right(4294967252L)
    WavAudioWriter.calculateDataSize(1073741813L, 2) shouldBe Right(4294967252L)
  }

  it should "return FileTooLarge just above the limit and for huge frame counts" in {
    WavAudioWriter.calculateDataSize(2147483627L, 1) should matchPattern { case Left(AudioSaveError.FileTooLarge(_)) => }
    WavAudioWriter.calculateDataSize(1073741814L, 2) should matchPattern { case Left(AudioSaveError.FileTooLarge(_)) => }
    WavAudioWriter.calculateDataSize(3000000000L, 2) should matchPattern { case Left(AudioSaveError.FileTooLarge(_)) => }
  }

  it should "return FileTooLarge instead of overflowing Long arithmetic" in {
    WavAudioWriter.calculateDataSize(Long.MaxValue, 1) should matchPattern { case Left(AudioSaveError.FileTooLarge(_)) => }
    WavAudioWriter.calculateDataSize(Long.MaxValue, 2) should matchPattern { case Left(AudioSaveError.FileTooLarge(_)) => }
  }

  it should "reject a sample rate a float cannot represent exactly, and accept 2^24 Hz" in {
    val veryFast = StereoTrack(Int.MaxValue, Vector.empty)
    val tooFast = StereoTrack((1 << 24) + 1, Vector.empty)

    WavAudioWriter.save(veryFast, tempDir.resolve("fast.wav")) should matchPattern { case Left(AudioSaveError.InvalidTrack(_)) => }
    WavAudioWriter.save(tooFast, tempDir.resolve("fast2.wav")) should matchPattern { case Left(AudioSaveError.InvalidTrack(_)) => }
    WavAudioWriter.save(StereoTrack(1 << 24, Vector.empty), tempDir.resolve("limit.wav")) shouldBe Right(())
  }

  "Exporting an unknown track type" should "return InvalidTrack" in {
    val custom = new AudioTrack:
      override def sampleRate: Int = 44100
      override def chunks: Vector[AudioChunk] = Vector.empty

    WavAudioWriter.save(custom, tempDir.resolve("custom.wav")) should matchPattern { case Left(AudioSaveError.InvalidTrack(_)) => }
  }

  "WavAudioWriter" should "be usable through the AudioWriter abstraction" in {
    val writer: AudioWriter = WavAudioWriter
    val path = tempDir.resolve("abstract.wav")

    writer.save(monoTrack(44100, Seq(1)), path) shouldBe Right(())
    Files.exists(path) shouldBe true
  }
