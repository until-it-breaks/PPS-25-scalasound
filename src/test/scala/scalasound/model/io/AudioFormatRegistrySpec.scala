package scalasound.model.io

import cats.effect.IO
import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scalasound.model.domain.AudioTrack
import scalasound.model.io.error.FormatResolutionError.*
import scalasound.model.io.error.{AudioLoadError, AudioSaveError}

import java.nio.file.{Path, Paths}
import java.util.concurrent.atomic.AtomicInteger

class AudioFormatRegistrySpec extends AnyFlatSpec with Matchers with EitherValues:

  private val wavReader  = FakeReader()
  private val wavWriter  = FakeWriter()
  private val fakeReader = FakeReader()
  private val fakeWriter = FakeWriter()
  private val wav  = AudioFileType("WAV", "wav", reader = Some(wavReader), writer = Some(wavWriter))
  private val fake =
    AudioFileType("Fake", "fake", reader = Some(fakeReader), writer = Some(fakeWriter))

  private def registry(fileTypes: AudioFileType*): AudioFormatRegistry =
    AudioFormatRegistry.of(fileTypes*).value

  private def path(text: String): Path = Paths.get(text)

  private final class FakeReader extends AudioReader:
    val calls                                                                   = AtomicInteger(0)
    override def load(path: Path): IO[Either[AudioLoadError, AudioTrack]] =
      IO:
        calls.incrementAndGet()
        Left(AudioLoadError.ReadFailure("fake"))

  private final class FakeWriter extends AudioWriter:
    val calls = AtomicInteger(0)
    override def save(track: AudioTrack, path: Path): IO[Either[AudioSaveError, Unit]] =
      IO:
        calls.incrementAndGet()
        Right(())

  "Selecting a reader" should "return the reader of the format of the extension" in:
    registry(wav).readerFor(path("song.wav")).value shouldBe wavReader

  it should "ignore the case of the extension" in:
    Seq("song.wav", "song.WAV", "song.Wav").foreach: name =>
      registry(wav).readerFor(path(name)).value shouldBe wavReader

  it should "select the right reader when several formats are registered" in:
    val formats = registry(wav, fake)

    formats.readerFor(path("a.wav")).value shouldBe wavReader
    formats.readerFor(path("dir/b.fake")).value shouldBe fakeReader

  it should "select the reader by an alias of the format" in:
    val flexible = wav.copy(aliases = Set("wave", "WAVX"))

    Seq("a.wave", "a.WAVX", "a.wav").foreach: name =>
      registry(flexible).readerFor(path(name)).value shouldBe wavReader

  it should "report a missing extension" in:
    registry(wav).readerFor(path("song")) shouldBe Left(MissingExtension(path("song")))
    registry(wav).readerFor(path("song.")) shouldBe Left(MissingExtension(path("song.")))
    registry(wav).readerFor(path(".wav")) shouldBe Left(MissingExtension(path(".wav")))

  it should "report an unsupported extension" in:
    registry(wav).readerFor(path("song.mp3")) shouldBe Left(UnsupportedExtension("mp3"))
    registry(wav).readerFor(path("song.MP3")) shouldBe Left(UnsupportedExtension("mp3"))
    registry(wav).readerFor(path("song.wav.bak")) shouldBe Left(UnsupportedExtension("bak"))
    registry().readerFor(path("song.wav")) shouldBe Left(UnsupportedExtension("wav"))

  it should "report a format that cannot be read when it only has a writer" in:
    val writeOnly = AudioFileType("Export only", "out", writer = Some(fakeWriter))

    registry(wav, writeOnly).readerFor(path("a.out")) shouldBe Left(
      ReadingNotSupported("Export only")
    )

  "Selecting a writer" should "return the writer of the format of the extension, for the same path" in:
    val resolved = registry(wav).writerFor(path("out/song.WAV")).value

    resolved.writer shouldBe wavWriter
    resolved.path shouldBe path("out/song.WAV")

  it should "select the right writer when several formats are registered" in:
    val formats = registry(wav, fake)

    formats.writerFor(path("a.wav")).value.writer shouldBe wavWriter
    formats.writerFor(path("b.fake")).value.writer shouldBe fakeWriter

  it should "add the extension of the default format to a destination without one" in:
    registry(wav).writerFor(path("song")).value.path shouldBe path("song.wav")
    registry(wav).writerFor(path("dir/song")).value.path shouldBe path("dir/song.wav")
    registry(wav).writerFor(path("dir.v2/song")).value.path shouldBe path("dir.v2/song.wav")
    registry(wav).writerFor(path(".hidden")).value.path shouldBe path(".hidden.wav")

  it should "use as default the first registered format that has a writer" in:
    val readOnly = AudioFileType("Import only", "in", reader = Some(fakeReader))
    val resolved = registry(readOnly, fake, wav).writerFor(path("song")).value

    resolved.writer shouldBe fakeWriter
    resolved.path shouldBe path("song.fake")

  it should "use the primary extension of the default format" in:
    registry(wav.copy(primaryExtension = "WAV")).writerFor(path("song")).value.path shouldBe path(
      "song.wav"
    )

  it should "report an unsupported extension instead of changing it" in:
    registry(wav).writerFor(path("track.mp3")) shouldBe Left(UnsupportedExtension("mp3"))

  it should "report a format that cannot be written when it only has a reader" in:
    val readOnly = AudioFileType("Import only", "in", reader = Some(fakeReader))

    registry(wav, readOnly).writerFor(path("a.in")) shouldBe Left(
      WritingNotSupported("Import only")
    )

  it should "report a missing extension when no format can be written" in:
    val readOnly = AudioFileType("Import only", "in", reader = Some(fakeReader))

    registry(readOnly).writerFor(path("song")) shouldBe Left(MissingExtension(path("song")))
    registry().writerFor(path("song")) shouldBe Left(MissingExtension(path("song")))

  it should "report a missing extension for a path without a file name" in:
    registry(wav).writerFor(path("/")) shouldBe Left(MissingExtension(path("/")))

  "Selecting a reader or writer" should "never invoke load or save methods of readers or writers" in:
    val formats = registry(wav, fake)
    Seq("a.wav", "b.fake", "c.xyz", "d").foreach: name =>
      formats.readerFor(path(name))
      formats.writerFor(path(name))

    Seq(wavReader, wavWriter, fakeReader, fakeWriter).map {
      case reader: FakeReader => reader.calls.get
      case writer: FakeWriter => writer.calls.get
    } shouldBe Seq(0, 0, 0, 0)

  "A new format" should "be usable by registering it, leaving the other formats unchanged" in:
    val before = registry(wav)
    val after  = registry(wav, fake)

    (after.readerFor(path("x.fake")).value eq fakeReader) shouldBe true
    (after.writerFor(path("x.fake")).value.writer eq fakeWriter) shouldBe true
    (after.readerFor(path("x.wav")).value eq wavReader) shouldBe true
    before.readerFor(path("x.fake")) shouldBe Left(UnsupportedExtension("fake"))

  "Building a registry" should "fail when two formats claim the same extension" in:
    val other = AudioFileType("Other WAV", "wav", reader = Some(fakeReader))

    AudioFormatRegistry.of(wav, other) shouldBe
      Left(AudioFormatRegistryError.DuplicateExtension("wav", List("WAV", "Other WAV")))

  it should "compare extensions ignoring the case" in:
    val other = AudioFileType("Other", "WAV", reader = Some(fakeReader))

    AudioFormatRegistry.of(wav, other).left.value shouldBe
      AudioFormatRegistryError.DuplicateExtension("wav", List("WAV", "Other"))

  it should "fail when an alias is claimed by another format" in:
    val other = AudioFileType("Other", "oth", aliases = Set("Wav"), reader = Some(fakeReader))

    AudioFormatRegistry.of(wav, other).left.value shouldBe
      AudioFormatRegistryError.DuplicateExtension("wav", List("WAV", "Other"))

  it should "accept the same extension repeated inside one format" in:
    val repeated = wav.copy(aliases = Set("WAV", "wav"))

    AudioFormatRegistry.of(repeated).isRight shouldBe true

  it should "fail on a malformed extension" in:
    Seq("", ".wav", "w av", "a/b").foreach: bad =>
      AudioFormatRegistry.of(wav.copy(primaryExtension = bad)) shouldBe
        Left(AudioFormatRegistryError.MalformedExtension("WAV", bad))
    AudioFormatRegistry.of(wav.copy(aliases = Set(".wave"))) shouldBe
      Left(AudioFormatRegistryError.MalformedExtension("WAV", ".wave"))

  it should "accept an empty configuration" in:
    AudioFormatRegistry.of().isRight shouldBe true
