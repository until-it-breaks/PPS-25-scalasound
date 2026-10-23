package scalasound.io

import java.nio.file.{Files, Path}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scalasound.domain.MonoTrack
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
