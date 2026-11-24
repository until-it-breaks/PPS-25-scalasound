package scalasound.model.io

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scalasound.model.domain.{AudioTrack, MonoTrack, StereoTrack}
import scalasound.model.io.WavFixtures.*
import scalasound.model.io.{AudioLoadError, WavAudioReader}

import java.nio.file.{Files, Path}
import scala.concurrent.duration.*
import scala.reflect.ClassTag

class WavAudioReaderSpec extends AnyFlatSpec with Matchers:

  // 256 = 0x0100 detects swapped bytes (little-endian vs big-endian);
  // negative values detect a missing sign;
  // 16384 = 0x4000 detects swapped channels (left vs right);
  private final val KnownSamples =
    Vector(0, 16384, -16384, 32767, -32768, 1, -1, 256)
  private final val KnownValues =
    Vector(0f, 0.5f, -0.5f, 32767 / 32768f, -1.0f, 1 / 32768f, -1 / 32768f, 256 / 32768f)
  // Interleaved as (L0,R0,L1,R1,...) so that swapped channels or a wrong interleaving change the result.
  private final val StereoFrames = Vector((16384, -16384), (0, 32767), (-32768, 256), (1, -1))

  private def withTempFile[A](name: String, bytes: Array[Byte])(test: String => A): A =
    withTempDir: dir =>
      val path = Files.write(dir.resolve(name), bytes).toString
      test(path)

  private def withTempDir[A](test: Path => A): A =
    val dir = Files.createTempDirectory("scalasound-wav-test-")
    try test(dir)
    finally
      Option(dir.toFile.listFiles()).foreach(_.foreach(_.delete()))
      Files.deleteIfExists(dir)

  private def loadTrack[T <: AudioTrack: ClassTag](path: String): T =
    WavAudioReader.load(path) match
      case Right(track: T) =>
        track
      case other =>
        fail(s"expected a ${summon[ClassTag[T]].runtimeClass.getSimpleName} but got $other")

  "A mono PCM 16 bit WAV" should "be decoded with sample rate, signed values and byte order preserved" in
    withTempFile("mono.wav", pcm16Wav(44100, 1, KnownSamples)): path =>
      val track = loadTrack[MonoTrack](path)

      track.sampleRate.value shouldBe 44100
      track.frameCount shouldBe 8L
      track.chunks.flatMap(_.samples) shouldBe KnownValues

  it should "keep the sample rate declared in the file and compute the duration from the number of frames" in
    withTempFile("one-second.wav", pcm16Wav(8000, 1, pcmSampleSequence(8000))): path =>
      val track = loadTrack[MonoTrack](path)

      track.sampleRate.value shouldBe 8000
      track.duration shouldBe 1.second

  it should "be split in chunks of 1024 frames with a shorter final chunk and no padding" in:
    val samples = pcmSampleSequence(2500)
    withTempFile("long.wav", pcm16Wav(44100, 1, samples)): path =>
      val track = loadTrack[MonoTrack](path)

      track.chunks.map(_.samples.length) shouldBe Vector(1024, 1024, 452)
      track.chunks.flatMap(_.samples) shouldBe samples.map(_ / 32768f)

  it should "be accepted when it contains no frames, with duration zero" in
    withTempFile("empty.wav", pcm16Wav(44100, 1, Vector.empty)): path =>
      val track = loadTrack[MonoTrack](path)

      track.chunks shouldBe Vector.empty
      track.duration shouldBe Duration.Zero

  private def pcmSampleSequence(frames: Int): Vector[Int] =
    Vector.tabulate(frames)(i => ((i * 13) % 20000) - 10000)

  private def interleaveChannels(frames: Seq[(Int, Int)]): Vector[Int] =
    frames.flatMap((l, r) => Vector(l, r)).toVector

  "A stereo PCM 16 bit WAV" should "be decoded into separate left and right channels" in
    withTempFile("stereo.wav", pcm16Wav(48000, 2, interleaveChannels(StereoFrames))): path =>
      val track = loadTrack[StereoTrack](path)

      track.sampleRate.value shouldBe 48000
      track.chunks.flatMap(_.left) shouldBe Vector(0.5f, 0f, -1.0f, 1 / 32768f)
      track.chunks
        .flatMap(_.right) shouldBe Vector(-0.5f, 32767 / 32768f, 256 / 32768f, -1 / 32768f)

  it should "count one frame per left-right pair and derive the duration from it" in:
    val frames = Vector.tabulate(4800)(i => (i % 100, -(i % 100)))
    withTempFile("stereo-duration.wav", pcm16Wav(48000, 2, interleaveChannels(frames))): path =>
      val track = loadTrack[StereoTrack](path)

      track.frameCount shouldBe 4800L
      track.duration shouldBe 100.millis

  it should "be accepted when it contains no frames, with duration zero" in
    withTempFile("stereo-empty.wav", pcm16Wav(44100, 2, Vector.empty)): path =>
      val track = loadTrack[StereoTrack](path)

      track.chunks shouldBe Vector.empty
      track.duration shouldBe Duration.Zero

  "Loading a file" should "return FileNotFound when the path does not exist" in withTempDir: dir =>
    val path = dir.resolve("missing.wav").toString

    WavAudioReader.load(path) shouldBe Left(AudioLoadError.FileNotFound(path))

  it should "return FileNotFound when the path is a directory" in withTempDir: dir =>
    val path = dir.toString

    WavAudioReader.load(path) shouldBe Left(AudioLoadError.FileNotFound(path))

  private def isUnsupported(result: Either[AudioLoadError, ?]): Boolean = result match
    case Left(_: AudioLoadError.UnsupportedFormat) => true
    case _                                         => false

  private def isCorrupted(result: Either[AudioLoadError, ?]): Boolean = result match
    case Left(_: AudioLoadError.CorruptedData) => true
    case _                                     => false

  it should "reject 8 bit PCM as an unsupported format" in:
    val bytes = wav(PcmFormatTag, 44100, 1, 8, Array.fill[Byte](16)(120))

    withTempFile("pcm8.wav", bytes): path =>
      isUnsupported(WavAudioReader.load(path)) shouldBe true

  it should "reject 24 bit PCM as an unsupported format" in:
    val bytes = wav(PcmFormatTag, 44100, 1, 24, Array.fill[Byte](24)(1))

    withTempFile("pcm24.wav", bytes): path =>
      isUnsupported(WavAudioReader.load(path)) shouldBe true

  it should "reject 32 bit float WAV as an unsupported format" in:
    val bytes = wav(FloatFormatTag, 44100, 1, 32, Array.fill[Byte](16)(0))

    withTempFile("float32.wav", bytes): path =>
      isUnsupported(WavAudioReader.load(path)) shouldBe true

  it should "reject WAV files with more than two channels" in:
    val bytes = pcm16Wav(44100, 3, Vector.fill(30)(100))

    withTempFile("three-channels.wav", bytes): path =>
      isUnsupported(WavAudioReader.load(path)) shouldBe true

  it should "reject a file that is not a WAV at all" in:
    val bytes = "this is plain text, not audio".getBytes("UTF-8")

    withTempFile("notes.wav", bytes): path =>
      isUnsupported(WavAudioReader.load(path)) shouldBe true

  it should "reject an empty file without throwing" in
    withTempFile("zero-bytes.wav", Array.emptyByteArray): path =>
      WavAudioReader.load(path).isLeft shouldBe true

  it should "report CorruptedData when the data is shorter than the header declares" in:
    val data      = pcm16Data(Vector.tabulate(100)(_ * 10))
    val truncated =
      wav(PcmFormatTag, 44100, 1, 16, data.take(100), declaredDataSize = Some(data.length))

    withTempFile("truncated.wav", truncated): path =>
      isCorrupted(WavAudioReader.load(path)) shouldBe true

  it should "discard an incomplete trailing frame, as Java Sound does, keeping all whole frames" in:
    val data = pcm16Data(Vector(100, -100, 200, -200, 300, -300)).dropRight(3)
    withTempFile("partial-frame.wav", wav(PcmFormatTag, 44100, 2, 16, data)): path =>
      val track = loadTrack[StereoTrack](path)

      track.frameCount shouldBe 2L
      track.chunks.flatMap(_.left) shouldBe Vector(100 / 32768f, 200 / 32768f)
      track.chunks.flatMap(_.right) shouldBe Vector(-100 / 32768f, -200 / 32768f)

  it should "report CorruptedData for a zero sample rate" in:
    val bytes = wav(PcmFormatTag, 0, 1, 16, pcm16Data(Vector(1, 2, 3)))

    withTempFile("zero-rate.wav", bytes): path =>
      isCorrupted(WavAudioReader.load(path)) shouldBe true

  "Loading corrupted files" should "return an error, never throw, for a WAV truncated at any length" in withTempDir:
    dir =>
      val valid = pcm16Wav(44100, 2, pcmSampleSequence(200))
      val path  = dir.resolve("truncated.wav")

      valid.indices.foreach: length =>
        Files.write(path, valid.take(length))
        withClue(s"truncated at $length bytes: ")(
          WavAudioReader.load(path.toString).isLeft shouldBe true
        )

      Files.write(path, valid)
      WavAudioReader.load(path.toString).isRight shouldBe true

  it should "never throw when a byte of the header is replaced by an arbitrary value" in withTempDir:
    dir =>
      val valid = pcm16Wav(44100, 2, pcmSampleSequence(200))
      val path  = dir.resolve("damaged.wav")

      for
        index <- 0 until 44
        value <- Seq(0x00, 0x01, 0x7f, 0x80, 0xff)
      do
        Files.write(path, valid.updated(index, value.toByte))
        WavAudioReader.load(path.toString) should matchPattern { case Left(_) | Right(_) => }

  it should "reject random bytes as an error" in withTempDir: dir =>
    val random = scala.util.Random(42L)
    val path   = dir.resolve("random.wav")

    (1 to 50).foreach: _ =>
      Files.write(path, random.nextBytes(random.nextInt(300)))
      WavAudioReader.load(path.toString).isLeft shouldBe true
