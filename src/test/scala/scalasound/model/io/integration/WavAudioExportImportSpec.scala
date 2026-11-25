package scalasound.model.io.integration

import cats.effect.unsafe.implicits.global
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scalasound.model.domain.AudioChunk.{MonoChunk, StereoChunk}
import scalasound.model.domain.{AudioTrack, MonoTrack, StereoTrack}
import scalasound.model.io.wav.{WavAudioReader, WavAudioWriter}

import java.nio.file.{Files, Path, StandardCopyOption}
import scala.reflect.ClassTag
import scala.util.Using

class WavAudioExportImportSpec extends AnyFlatSpec with Matchers with EitherValues:

  /** Half a quantisation step, plus room for float noise. */
  private val Tolerance = 0.5f / 32768f + 1e-6f

  private def exportToImportSameTrack(
      track: AudioTrack,
      name: String = "export-import.wav"
  ): AudioTrack =
    withTempDir: dir =>
      val path = dir.resolve(name)
      WavAudioWriter.save(track, path).unsafeRunSync() shouldBe Right(())
      WavAudioReader.load(path).unsafeRunSync() match
        case Right(loaded) => loaded
        case Left(error)   => fail(s"the exported file could not be imported: $error")

  private def withTempDir[A](test: Path => A): A =
    val dir = Files.createTempDirectory("scalasound-export-import-test-")
    try test(dir)
    finally
      Option(dir.toFile.listFiles()).foreach(_.foreach(_.delete()))
      Files.deleteIfExists(dir)

  /** Narrows an [[AudioTrack]] to a concrete subtype `T` at runtime. [[ClassTag]] is required to
    * preserve `T`'s class metadata across JVM type erasure, enabling safe runtime pattern matching
    * (`case t: T`) and descriptive failure messages.
    */
  private def expectTrack[T <: AudioTrack: ClassTag](track: AudioTrack): T =
    track match
      case t: T  => t
      case other =>
        fail(s"expected ${summon[ClassTag[T]].runtimeClass.getSimpleName} but got $other")

  private def sine(frames: Int, step: Double, amplitude: Float): Vector[Float] =
    Vector.tabulate(frames)(i => math.sin(i * step).toFloat * amplitude)

  private def assertClose(actual: Vector[Float], expected: Vector[Float]): Unit =
    actual.length shouldBe expected.length
    actual.zip(expected).foreach { case (a, e) => a shouldBe (e +- Tolerance) }

  "A mono track" should "preserve sample rate, frame count, duration and sample values within PCM 16-bit quantisation tolerance" in:
    val samples  = sine(3000, 0.01, 0.9f)
    val original =
      MonoTrack(44100, samples.grouped(1000).map[MonoChunk](MonoChunk(_)).toVector).value
    val loaded = expectTrack[MonoTrack](exportToImportSameTrack(original))

    loaded.sampleRate.value shouldBe 44100
    loaded.frameCount shouldBe original.frameCount
    loaded.duration shouldBe original.duration
    assertClose(loaded.chunks.flatMap(_.samples), samples)

  it should "re-chunk samples into standard 1024-frame blocks upon import regardless of original chunk layout" in:
    val original = MonoTrack(
      44100,
      sine(3000, 0.01, 0.5f)
        .grouped(1000)
        .map[MonoChunk](MonoChunk(_))
        .toVector
    ).value

    exportToImportSameTrack(original).chunks.map(_.frameCount) shouldBe Vector(1024, 1024, 952)

  it should "preserve every PCM 16-bit value" in:
    val samples  = Vector.tabulate(65536)(i => (i - 32768) / 32768f)
    val original =
      MonoTrack(44100, samples.grouped(1024).map[MonoChunk](MonoChunk(_)).toVector).value
    val loaded = expectTrack[MonoTrack](exportToImportSameTrack(original))

    loaded.chunks.flatMap(_.samples) shouldBe samples

  it should "export and re-import empty tracks without errors" in:
    exportToImportSameTrack(MonoTrack(22050, Vector.empty).value) shouldBe MonoTrack(
      22050,
      Vector.empty
    ).value

  it should "preserve standard and non-standard sample rates in the WAV header" in:
    Seq(8000, 22050, 48000, 96000).foreach: rate =>
      exportToImportSameTrack(
        MonoTrack(rate, Vector(MonoChunk(Vector(0.1f, 0.2f)))).value,
        s"mono-$rate.wav"
      ).sampleRate.value shouldBe rate

  "A stereo track" should "preserve independently the left and right channels without crosstalk, channel mixing, or phase inversion" in:
    val left     = sine(3000, 0.01, 0.9f)
    val right    = sine(3000, 0.03, 0.4f)
    val original = StereoTrack(
      48000,
      left
        .grouped(700)
        .zip(right.grouped(700))
        .map[StereoChunk] { case (l, r) => StereoChunk(l, r) }
        .toVector
    ).value
    val loaded = expectTrack[StereoTrack](exportToImportSameTrack(original))

    loaded.sampleRate.value shouldBe 48000
    loaded.frameCount shouldBe original.frameCount
    loaded.duration shouldBe original.duration
    assertClose(loaded.chunks.flatMap(_.left), left)
    assertClose(loaded.chunks.flatMap(_.right), right)

  it should "preserve the original values exactly when values are already PCM-aligned, without swapping channels" in:
    val left     = Vector.tabulate(2000)(i => ((i * 7) % 30000) / 32768f)
    val right    = left.map(-_)
    val original = StereoTrack(44100, Vector(StereoChunk(left, right))).value
    val loaded   = expectTrack[StereoTrack](exportToImportSameTrack(original))

    loaded.chunks.flatMap(_.left) shouldBe left
    loaded.chunks.flatMap(_.right) shouldBe right

  it should "handle empty stereo tracks without errors" in:
    exportToImportSameTrack(StereoTrack(44100, Vector.empty).value) shouldBe StereoTrack(
      44100,
      Vector.empty
    ).value

  "A real WAV file" should "maintain equal sample rate, frame count and duration when exporting and re-importing a real PCM WAV file" in withTempDir:
    dir =>
      val source = dir.resolve("source.wav")
      Using.resource(getClass.getResourceAsStream("/file_example_WAV_2MG.wav"))(in =>
        Files.copy(in, source, StandardCopyOption.REPLACE_EXISTING)
      )
      val rawImported = WavAudioReader
        .load(source)
        .unsafeRunSync()
        .getOrElse(fail("the test resource could not be imported"))
      val before = expectTrack[StereoTrack](rawImported)
      val after  = expectTrack[StereoTrack](exportToImportSameTrack(before))

      after.sampleRate shouldBe before.sampleRate
      after.chunks.flatMap(_.left) shouldBe before.chunks.flatMap(_.left)
      after.chunks.flatMap(_.right) shouldBe before.chunks.flatMap(_.right)
