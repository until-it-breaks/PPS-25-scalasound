package scalasound.model.io

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.Paths
import java.util.Locale

class FileExtensionsSpec extends AnyFlatSpec with Matchers:

  private def extensionOf(path: String): Option[String] = FileExtensions.of(Paths.get(path))

  "The extension of a path" should "be the text after the last dot of the file name" in:
    extensionOf("song.wav") shouldBe Some("wav")
    extensionOf("a.b.c.wav") shouldBe Some("wav")

  it should "be normalised to lower case" in:
    Seq("song.wav", "song.WAV", "song.Wav", "SONG.wAv").foreach(extensionOf(_) shouldBe Some("wav"))

  it should "ignore the directories of the path, even when they contain dots" in:
    extensionOf("music/song.wav") shouldBe Some("wav")
    extensionOf("/home/user.name/song.au") shouldBe Some("au")
    extensionOf("album.v2/song") shouldBe None

  it should "be missing for a file name without a dot" in:
    extensionOf("song") shouldBe None
    extensionOf("dir/song") shouldBe None

  it should "be missing when the dot is the last character of the name" in:
    extensionOf("song.") shouldBe None

  it should "not consider the leading dots of a hidden file as an extension" in:
    extensionOf(".wav") shouldBe None
    extensionOf(".au") shouldBe None
    extensionOf("..wav") shouldBe None
    extensionOf("music/.wav") shouldBe None

  it should "find the extension of a hidden file that has one" in:
    extensionOf(".hidden.wav") shouldBe Some("wav")
    extensionOf("..double.mp3") shouldBe Some("mp3")

  it should "be missing for a path without a file name" in:
    extensionOf("/") shouldBe None
    extensionOf("") shouldBe None

  it should "not depend on the default locale" in:
    val original = Locale.getDefault
    try
      Locale.setDefault(Locale.forLanguageTag("tr-TR"))
      // in a Turkish locale, the lowercase of 'I' is not 'i'
      extensionOf("tune.MIDI") shouldBe Some("midi")
    finally Locale.setDefault(original)

  "Normalising an extension" should "lower the case only" in:
    FileExtensions.normalise("WAV") shouldBe "wav"
    FileExtensions.normalise("Au") shouldBe "au"
    FileExtensions.normalise("mp3") shouldBe "mp3"

  "A well formed extension" should "be non empty and free of dots, whitespace and separators" in:
    Seq("wav", "WAV", "mp3", "au", "aiff").foreach(FileExtensions.isWellFormed(_) shouldBe true)
    Seq("", ".wav", "wav.", "w.av", "w av", " wav", "wav ", "a/b", "\t")
      .foreach(FileExtensions.isWellFormed(_) shouldBe false)
