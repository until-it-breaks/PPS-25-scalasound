package scalasound.model.io

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scalasound.model.domain.AudioChunk.MonoChunk
import scalasound.model.domain.{AudioTrack, MonoTrack}
import scalasound.model.io.FormatResolutionError.*
import scalasound.model.io.WavFixtures.*

import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.atomic.AtomicInteger

class WavAudioFormatSpec extends AnyFlatSpec with Matchers with EitherValues:

  private val registry: AudioFormatRegistry = AudioFormatRegistry.of(WavAudioFormat.fileType).value

  private def withTempDir[A](test: Path => A): A =
    val dir = Files.createTempDirectory("scalasound-wav-format-test-")
    try test(dir)
    finally
      Option(dir.toFile.listFiles()).foreach(_.foreach(_.delete()))
      Files.deleteIfExists(dir)

  private def track(samples: Seq[Int]): AudioTrack =
    MonoTrack(44100, Vector(MonoChunk(samples.map(_ / 32768f).toVector))).value

  "The WAV format" should "select the WAV reader and the WAV writer for a .wav path" in {
    Seq("song.wav", "song.WAV", "dir/song.Wav").foreach: name =>
      registry.readerFor(Paths.get(name)).value shouldBe WavAudioReader
      registry.writerFor(Paths.get(name)).value.writer shouldBe WavAudioWriter
  }

  it should "be the default format of a destination without an extension" in {
    registry.writerFor(Paths.get("song")).value.path shouldBe Paths
      .get("song.wav")
  }

  "A file chosen by its extension" should "still be validated" in withTempDir { dir =>
    val notAudio =
      Files.write(dir.resolve("song.wav"), "this is text, not an audio file".getBytes("UTF-8"))
    val reader = registry.readerFor(notAudio).value

    reader.load(notAudio.toString) should matchPattern {
      case Left(AudioLoadError.UnsupportedFormat(_)) =>
    }
  }

  it should "not use another reader, when the content does not match the extension" in withTempDir {
    dir =>
      val calls = AtomicInteger(0)
      val other = new AudioReader:
        override def load(filePath: String): Either[AudioLoadError, AudioTrack] =
          calls.incrementAndGet()
          Left(AudioLoadError.ReadFailure("must not be called"))
      val both = AudioFormatRegistry
        .of(WavAudioFormat.fileType, AudioFileType("Other", "other", reader = Some(other)))
        .value
      val notAudio =
        Files.write(dir.resolve("song.wav"), "this is text, not an audio file".getBytes("UTF-8"))

      both.readerFor(notAudio).value.load(notAudio.toString).isLeft shouldBe true
      calls.get shouldBe 0
  }

  it should "not be read when the extension is not registered, even if the content is valid" in withTempDir {
    dir =>
      val wavContent = Files.write(dir.resolve("song.bin"), pcm16Wav(44100, 1, Seq(1, 2, 3)))

      registry.readerFor(wavContent) shouldBe Left(UnsupportedExtension("bin"))
  }

  "The writer and reader chosen by the registry" should "save and load a track, adding the extension to the destination" in withTempDir {
    dir =>
      val original = track(Seq(0, 16384, -16384, 32767, -32768, 1, -1, 256))
      val resolved = registry.writerFor(dir.resolve("export")).value

      resolved.path shouldBe dir.resolve("export.wav")
      resolved.writer.save(original, resolved.path) shouldBe Right(())
      Files.exists(dir.resolve("export.wav")) shouldBe true

      val loaded = registry.readerFor(resolved.path).value.load(resolved.path.toString)
      loaded shouldBe Right(original)
  }
