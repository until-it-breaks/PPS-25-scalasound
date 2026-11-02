package scalasound.io

import java.nio.file.{Files, Path, StandardCopyOption}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scalasound.domain.AudioChunk.{MonoChunk, StereoChunk}
import scalasound.domain.{AudioTrack, MonoTrack, StereoTrack}
import scala.util.Using

class WavAudioExportImportSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach:

  private var tempDir: Path = scala.compiletime.uninitialized

  /** Half a quantisation step, plus room for float noise. */
  private val Tolerance = 0.5f / 32768f + 1e-6f

  override def beforeEach(): Unit =
    tempDir = Files.createTempDirectory("scalasound-export-import-test")

  override def afterEach(): Unit =
    tempDir.toFile.listFiles().foreach(_.delete())
    Files.delete(tempDir)

  private def exportToImportSameTrack(track: AudioTrack, name: String = "export-import.wav"): AudioTrack =
    val path = tempDir.resolve(name)
    WavAudioWriter.save(track, path) shouldBe Right(())
    WavAudioReader.load(path.toString) match
      case Right(loaded) => loaded
      case Left(error) => fail(s"the exported file could not be imported: $error")

  private def sine(frames: Int, step: Double, amplitude: Float): Vector[Float] =
    Vector.tabulate(frames)(i => math.sin(i * step).toFloat * amplitude)

  private def assertClose(actual: Vector[Float], expected: Vector[Float]): Unit =
    actual.length shouldBe expected.length
    actual.zip(expected).foreach { case (a, e) => a shouldBe (e +- Tolerance) }

  "A mono track" should "preserve sample rate, frame count, duration and sample values within PCM 16-bit quantisation tolerance" in {
    val samples = sine(3000, 0.01, 0.9f)
    val original = MonoTrack(44100, samples.grouped(1000).map[MonoChunk](MonoChunk(_)).toVector)

    exportToImportSameTrack(original) match
      case loaded: MonoTrack =>
        loaded.sampleRate shouldBe 44100
        loaded.frameCount shouldBe original.frameCount
        loaded.durationInSeconds shouldBe original.durationInSeconds
        assertClose(loaded.chunks.flatMap(_.samples), samples)
      case other => fail(s"expected a MonoTrack but got $other")
  }

  it should "re-chunk samples into standard 1024-frame blocks upon import regardless of original chunk layout" in {
    val original = MonoTrack(44100, sine(3000, 0.01, 0.5f)
      .grouped(1000).map[MonoChunk](MonoChunk(_)).toVector)

    exportToImportSameTrack(original).chunks.map(_.frameCount) shouldBe Vector(1024, 1024, 952)
  }

  it should "preserve every PCM 16-bit value" in {
    val samples = Vector.tabulate(65536)(i => (i - 32768) / 32768f)
    val original = MonoTrack(44100, samples.grouped(1024).map[MonoChunk](MonoChunk(_)).toVector)

    exportToImportSameTrack(original) match
      case loaded: MonoTrack => loaded.chunks.flatMap(_.samples) shouldBe samples
      case other => fail(s"expected a MonoTrack but got $other")
  }

  it should "export and re-import empty tracks without errors" in {
    exportToImportSameTrack(MonoTrack(22050, Vector.empty)) shouldBe MonoTrack(22050, Vector.empty)
  }

  it should "preserve standard and non-standard sample rates in the WAV header" in {
    Seq(8000, 22050, 48000, 96000).foreach { rate =>
      exportToImportSameTrack(MonoTrack(rate, Vector(MonoChunk(Vector(0.1f, 0.2f)))), s"mono-$rate.wav").sampleRate shouldBe rate
    }
  }

  "A stereo track" should "preserve independently the left and right channels without crosstalk, channel mixing, or phase inversion" in {
    val left = sine(3000, 0.01, 0.9f)
    val right = sine(3000, 0.03, 0.4f)
    val original = StereoTrack(
      48000,
      left.grouped(700).zip(right.grouped(700)).map[StereoChunk] { case (l, r) => StereoChunk(l, r) }.toVector
    )

    exportToImportSameTrack(original) match
      case loaded: StereoTrack =>
        loaded.sampleRate shouldBe 48000
        loaded.frameCount shouldBe original.frameCount
        loaded.durationInSeconds shouldBe original.durationInSeconds
        assertClose(loaded.chunks.flatMap(_.left), left)
        assertClose(loaded.chunks.flatMap(_.right), right)
      case other => fail(s"expected a StereoTrack but got $other")
  }

  it should "preserve the original values exactly when values are already PCM-aligned, without swapping channels" in {
    val left = Vector.tabulate(2000)(i => ((i * 7) % 30000) / 32768f)
    val right = left.map(-_)
    val original = StereoTrack(44100, Vector(StereoChunk(left, right)))

    exportToImportSameTrack(original) match
      case loaded: StereoTrack =>
        loaded.chunks.flatMap(_.left) shouldBe left
        loaded.chunks.flatMap(_.right) shouldBe right
      case other => fail(s"expected a StereoTrack but got $other")
  }

  it should "handle empty stereo tracks without errors" in {
    exportToImportSameTrack(StereoTrack(44100, Vector.empty)) shouldBe StereoTrack(44100, Vector.empty)
  }

  "A real WAV file" should "maintain equal sample rate, frame count and duration when exporting and re-importing a real PCM WAV file" in {
    val source = tempDir.resolve("source.wav")
    Using.resource(getClass.getResourceAsStream("/file_example_WAV_2MG.wav"))(in =>
      Files.copy(in, source, StandardCopyOption.REPLACE_EXISTING)
    )
    val imported = WavAudioReader.load(source.toString).getOrElse(fail("the test resource could not be imported"))

    (imported, exportToImportSameTrack(imported)) match
      case (before: StereoTrack, after: StereoTrack) =>
        after.sampleRate shouldBe before.sampleRate
        after.chunks.flatMap(_.left) shouldBe before.chunks.flatMap(_.left)
        after.chunks.flatMap(_.right) shouldBe before.chunks.flatMap(_.right)
      case other => fail(s"expected two stereo tracks but got $other")
  }
