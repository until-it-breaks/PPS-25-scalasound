package scalasound.model.io.wav

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scalasound.model.domain.AudioChunk.{MonoChunk, StereoChunk}
import scalasound.model.io.wav.Pcm16.toNormalised
import scalasound.model.io.wav.PcmChunks

class PcmChunksSpec extends AnyFlatSpec with Matchers:

  private val AllPcmValuesOf16BitRange: Vector[Int] = (Short.MinValue to Short.MaxValue).toVector

  private def normalised(pcm: Seq[Int]): Vector[Float] = pcm.map(_.toShort.toNormalised).toVector

  private def unsigned(bytes: Array[Byte]): Vector[Int] = bytes.map(_ & 0xff).toVector

  private def encodedPcm(pcm: Seq[Int]): Array[Byte] = littleEndian(pcm).map(_.toByte).toArray

  private def littleEndian(pcm: Seq[Int]): Vector[Int] =
    pcm.flatMap(value => Seq(value & 0xff, (value >> 8) & 0xff)).toVector

  "Encoding a mono chunk" should "write the samples as little-endian PCM, low byte first" in:
    val chunk = MonoChunk(normalised(Seq(0, 1, -1, 256, Short.MaxValue, Short.MinValue)))

    unsigned(PcmChunks.encode(chunk)) shouldBe
      Vector(0x00, 0x00, 0x01, 0x00, 0xff, 0xff, 0x00, 0x01, 0xff, 0x7f, 0x00, 0x80)

  it should "produce two bytes per sample and none for an empty chunk" in:
    PcmChunks.encode(MonoChunk(normalised(1 to 10))).length shouldBe 20
    PcmChunks.encode(MonoChunk(Vector.empty)).length shouldBe 0

  it should "clip samples outside the normalised range" in:
    unsigned(PcmChunks.encode(MonoChunk(Vector(2.0f, -2.0f)))) shouldBe Vector(
      0xff,
      0x7f,
      0x00,
      0x80
    )

  "Encoding a stereo chunk" should "interleave the channels as (L0, R0, L1, R1, ...)" in:
    val chunk = StereoChunk(normalised(Seq(1, 2, 3)), normalised(Seq(-1, -2, -3)))

    unsigned(PcmChunks.encode(chunk)) shouldBe littleEndian(Seq(1, -1, 2, -2, 3, -3))

  it should "keep the left and the right channel apart" in:
    val chunk = StereoChunk(normalised(Seq(100, 100)), normalised(Seq(0, 0)))

    unsigned(PcmChunks.encode(chunk)) shouldBe littleEndian(Seq(100, 0, 100, 0))

  it should "produce four bytes per frame and none for an empty chunk" in:
    PcmChunks.encode(StereoChunk(normalised(1 to 10), normalised(1 to 10))).length shouldBe 40
    PcmChunks.encode(StereoChunk(Vector.empty, Vector.empty)).length shouldBe 0

  "Decoding mono bytes" should "split the frames in chunks, the last one holding the remainder" in:
    val pcm    = (0 until 2500).map(_ - 1000)
    val chunks = PcmChunks.decodeMono(encodedPcm(pcm), 2500, 1024)

    chunks.map(_.samples.length) shouldBe Vector(1024, 1024, 452)
    chunks.flatMap(_.samples) shouldBe normalised(pcm)

  it should "produce a single chunk when it has exactly 1024 frames" in:
    PcmChunks
      .decodeMono(encodedPcm(0 until 1024), 1024, 1024)
      .map(_.samples.length) shouldBe Vector(1024)

  it should "produce a single short chunk for a single frame" in:
    PcmChunks.decodeMono(encodedPcm(Seq(-7)), 1, 1024) shouldBe Vector(
      MonoChunk(normalised(Seq(-7)))
    )

  it should "produce no chunks when there are no frames" in:
    PcmChunks.decodeMono(Array.emptyByteArray, 0, 1024) shouldBe Vector.empty

  "Decoding stereo bytes" should "separate the interleaved channels" in:
    val chunks = PcmChunks.decodeStereo(encodedPcm(Seq(1, -1, 2, -2, 3, -3)), 3, 1024)

    chunks shouldBe Vector(StereoChunk(normalised(Seq(1, 2, 3)), normalised(Seq(-1, -2, -3))))

  it should "keep both channels aligned in every chunk, with the remainder in the last one" in:
    val frames      = 2500
    val interleaved = (0 until frames).flatMap(frame => Seq(frame, -frame))
    val chunks      = PcmChunks.decodeStereo(encodedPcm(interleaved), frames, 1024)

    chunks.map(_.left.length) shouldBe Vector(1024, 1024, 452)
    chunks.map(_.right.length) shouldBe Vector(1024, 1024, 452)
    chunks.flatMap(_.left) shouldBe normalised(0 until frames)
    chunks.flatMap(_.right) shouldBe normalised((0 until frames).map(-_))

  it should "produce no chunks when there are no frames" in:
    PcmChunks.decodeStereo(Array.emptyByteArray, 0, 1024) shouldBe Vector.empty

  "Encoding and decoding" should "give back the same PCM bytes for every 16-bit value, in mono" in:
    val bytes   = encodedPcm(AllPcmValuesOf16BitRange)
    val decoded = PcmChunks.decodeMono(bytes, AllPcmValuesOf16BitRange.length, 1024)

    decoded.flatMap(chunk => unsigned(PcmChunks.encode(chunk))) shouldBe unsigned(bytes)

  it should "give back the same PCM bytes for every 16-bit value, in stereo" in:
    val interleaved =
      AllPcmValuesOf16BitRange.zip(AllPcmValuesOf16BitRange.reverse).flatMap((l, r) => Seq(l, r))
    val bytes   = encodedPcm(interleaved)
    val decoded = PcmChunks.decodeStereo(bytes, AllPcmValuesOf16BitRange.length, 1024)

    decoded.flatMap(chunk => unsigned(PcmChunks.encode(chunk))) shouldBe unsigned(bytes)
