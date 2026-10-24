package scalasound.io

import java.nio.file.{Files, Path}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scalasound.domain.{MonoTrack, StereoTrack}
import scalasound.io.WavFixtures.*

class WavAudioReaderSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach:

  private var tempDir: Path = scala.compiletime.uninitialized

  override def beforeEach(): Unit =
    tempDir = Files.createTempDirectory("scalasound-wav-test")

  override def afterEach(): Unit =
    tempDir.toFile.listFiles().foreach(_.delete())
    Files.delete(tempDir)

  private def writeFile(name: String, bytes: Array[Byte]): String =
    Files.write(tempDir.resolve(name), bytes).toString

  private def loadMono(path: String): MonoTrack =
    WavAudioReader.load(path) match
      case Right(track: MonoTrack) =>
        track
      case other =>
        fail(s"expected a MonoTrack but got $other")

  private def loadStereo(path: String): StereoTrack =
    WavAudioReader.load(path) match
      case Right(track: StereoTrack) =>
        track
      case other =>
        fail(s"expected a StereoTrack but got $other")

  // 256 = 0x0100 detects swapped bytes;
  // negative values detect a missing sign;
  // +/-16384 and the extremes check scaling.
  private final val KnownSamples =
    Vector(0, 16384, -16384, 32767, -32768, 1, -1, 256)
  private final val KnownValues =
    Vector(0f, 0.5f, -0.5f, 32767 / 32768f, -1.0f, 1 / 32768f, -1 / 32768f, 256 / 32768f)

  private def ramp(frames: Int): Vector[Int] =
    Vector.tabulate(frames)(i => ((i * 13) % 20000) - 10000)

  "A mono PCM 16 bit WAV" should "be decoded with sample rate, signed values and byte order preserved" in {
    val path = writeFile("mono.wav", pcm16Wav(44100, 1, KnownSamples))

    val track = loadMono(path)

    track.sampleRate shouldBe 44100
    track.frameCount shouldBe 8L
    track.chunks.flatMap(_.samples) shouldBe KnownValues
  }

  it should "keep the sample rate declared in the file" in {
    loadMono(writeFile("mono8k.wav", pcm16Wav(8000, 1, KnownSamples))).sampleRate shouldBe 8000
  }

  it should "compute the duration from the number of frames" in {
    val track = loadMono(writeFile("one-second.wav", pcm16Wav(8000, 1, ramp(8000))))

    track.durationInSeconds shouldBe 1.0
  }

  it should "be split in chunks of 1024 frames with a shorter final chunk and no padding" in {
    val samples = ramp(2500)
    val track = loadMono(writeFile("long.wav", pcm16Wav(44100, 1, samples)))

    track.chunks.map(_.samples.length) shouldBe Vector(1024, 1024, 452)
    track.chunks.flatMap(_.samples) shouldBe samples.map(_ / 32768f)
  }

  it should "produce a single chunk when it has exactly 1024 frames" in {
    val track = loadMono(writeFile("exact.wav", pcm16Wav(44100, 1, ramp(1024))))

    track.chunks.map(_.samples.length) shouldBe Vector(1024)
  }

  it should "be accepted when it contains no frames, with duration zero" in {
    val track = loadMono(writeFile("empty.wav", pcm16Wav(44100, 1, Vector.empty)))

    track.chunks shouldBe Vector.empty
    track.durationInSeconds shouldBe 0.0
  }

  // Interleaved as (L0,R0,L1,R1,...) so that swapped channels or a wrong interleaving change the result.
  private final val StereoFrames = Vector((16384, -16384), (0, 32767), (-32768, 256), (1, -1))

  private def interleave(frames: Seq[(Int, Int)]): Vector[Int] =
    frames.flatMap((l, r) => Vector(l, r)).toVector

  "A stereo PCM 16 bit WAV" should "be decoded into separate left and right channels" in {
    val track = loadStereo(writeFile("stereo.wav", pcm16Wav(48000, 2, interleave(StereoFrames))))

    track.sampleRate shouldBe 48000
    track.chunks.flatMap(_.left) shouldBe Vector(0.5f, 0f, -1.0f, 1 / 32768f)
    track.chunks.flatMap(_.right) shouldBe Vector(-0.5f, 32767 / 32768f, 256 / 32768f, -1 / 32768f)
  }

  it should "count one frame per left-right pair and derive the duration from it" in {
    val frames = Vector.tabulate(4800)(i => (i % 100, -(i % 100)))
    val track = loadStereo(writeFile("stereo-duration.wav", pcm16Wav(48000, 2, interleave(frames))))

    track.frameCount shouldBe 4800L
    track.durationInSeconds shouldBe 0.1
  }

  it should "be split in chunks of 1024 frames keeping both channels aligned" in {
    val frames = Vector.tabulate(2500)(i => (i, -i))
    val track = loadStereo(writeFile("stereo-long.wav", pcm16Wav(44100, 2, interleave(frames))))

    track.chunks.map(_.left.length) shouldBe Vector(1024, 1024, 452)
    track.chunks.map(_.right.length) shouldBe Vector(1024, 1024, 452)
    track.chunks.flatMap(_.left) shouldBe frames.map(_._1 / 32768f)
    track.chunks.flatMap(_.right) shouldBe frames.map(_._2 / 32768f)
  }

  it should "be accepted when it contains no frames, with duration zero" in {
    val track = loadStereo(writeFile("stereo-empty.wav", pcm16Wav(44100, 2, Vector.empty)))

    track.chunks shouldBe Vector.empty
    track.durationInSeconds shouldBe 0.0
  }

  "Loading a file" should "return FileNotFound when the path does not exist" in {
    val path = tempDir.resolve("missing.wav").toString

    WavAudioReader.load(path) shouldBe Left(AudioLoadError.FileNotFound(path))
  }

  it should "return FileNotFound when the path is a directory" in {
    val path = tempDir.toString

    WavAudioReader.load(path) shouldBe Left(AudioLoadError.FileNotFound(path))
  }

  private def isUnsupported(result: Either[AudioLoadError, ?]): Boolean = result match
    case Left(_: AudioLoadError.UnsupportedFormat) => true
    case _                                         => false

  private def isCorrupted(result: Either[AudioLoadError, ?]): Boolean = result match
    case Left(_: AudioLoadError.CorruptedData) => true
    case _                                     => false

  it should "reject 8 bit PCM as an unsupported format" in {
    val bytes = wav(PcmFormatTag, 44100, 1, 8, Array.fill[Byte](16)(120))

    isUnsupported(WavAudioReader.load(writeFile("pcm8.wav", bytes))) shouldBe true
  }

  it should "reject 24 bit PCM as an unsupported format" in {
    val bytes = wav(PcmFormatTag, 44100, 1, 24, Array.fill[Byte](24)(1))

    isUnsupported(WavAudioReader.load(writeFile("pcm24.wav", bytes))) shouldBe true
  }

  it should "reject 32 bit float WAV as an unsupported format" in {
    val bytes = wav(FloatFormatTag, 44100, 1, 32, Array.fill[Byte](16)(0))

    isUnsupported(WavAudioReader.load(writeFile("float32.wav", bytes))) shouldBe true
  }

  it should "reject WAV files with more than two channels" in {
    val bytes = pcm16Wav(44100, 3, Vector.fill(30)(100))

    isUnsupported(WavAudioReader.load(writeFile("three-channels.wav", bytes))) shouldBe true
  }

  it should "reject a file that is not a WAV at all" in {
    val bytes = "this is plain text, not audio".getBytes("UTF-8")

    val result = WavAudioReader.load(writeFile("notes.wav", bytes))

    isUnsupported(result) shouldBe true
  }

  it should "reject an empty file without throwing" in {
    val result = WavAudioReader.load(writeFile("zero-bytes.wav", Array.emptyByteArray))

    result.isLeft shouldBe true
  }

  it should "report CorruptedData when the data is shorter than the header declares" in {
    val data = pcm16Data(Vector.tabulate(100)(_ * 10))
    val truncated = wav(PcmFormatTag, 44100, 1, 16, data.take(100), declaredDataSize = Some(data.length))

    isCorrupted(WavAudioReader.load(writeFile("truncated.wav", truncated))) shouldBe true
  }

  it should "discard an incomplete trailing frame, as Java Sound does, keeping all whole frames" in {
    val data = pcm16Data(Vector(100, -100, 200, -200, 300, -300)).dropRight(3)
    val track = loadStereo(writeFile("partial-frame.wav", wav(PcmFormatTag, 44100, 2, 16, data)))

    track.frameCount shouldBe 2L
    track.chunks.flatMap(_.left) shouldBe Vector(100 / 32768f, 200 / 32768f)
    track.chunks.flatMap(_.right) shouldBe Vector(-100 / 32768f, -200 / 32768f)
  }

  it should "report CorruptedData for a zero sample rate" in {
    val bytes = wav(PcmFormatTag, 0, 1, 16, pcm16Data(Vector(1, 2, 3)))

    WavAudioReader.load(writeFile("zero-rate.wav", bytes)).isLeft shouldBe true
  }
