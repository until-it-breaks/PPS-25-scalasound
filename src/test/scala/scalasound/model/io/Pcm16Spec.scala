package scalasound.model.io

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scalasound.model.io.Pcm16.{fromLittleEndianBytes, isRepresentable, toNormalised, toPcm16}

import scala.util.Random

class Pcm16Spec extends AnyFlatSpec with Matchers:

  private val AllPcmValuesOf16BitRange: Seq[Short] =
    (Short.MinValue to Short.MaxValue).map(_.toShort)

  private def bytes(values: Int*): Array[Byte] = values.map(_.toByte).toArray

  "Decoding a PCM value" should "map the extremes and zero to the reference normalised samples" in {
    Short.MinValue.toNormalised shouldBe -1.0f
    Short.MaxValue.toNormalised shouldBe 32767 / 32768f
    (0: Short).toNormalised shouldBe 0.0f
  }

  it should "be exact for every 16-bit value" in {
    AllPcmValuesOf16BitRange.foreach(pcm => pcm.toNormalised.toDouble shouldBe pcm / 32768.0)
  }

  it should "always produce a sample in [-1.0, 1.0)" in {
    AllPcmValuesOf16BitRange.foreach: pcm =>
      val sample = pcm.toNormalised
      (sample >= -1.0f && sample < 1.0f) shouldBe true
  }

  "Quantising a normalised sample" should "map the reference values to the extremes of the range" in {
    (0.0f).toPcm16 shouldBe (0: Short)
    (-1.0f).toPcm16 shouldBe Short.MinValue
    (1.0f).toPcm16 shouldBe Short.MaxValue
  }

  it should "give back the same PCM value after decoding it, for every 16-bit value" in {
    AllPcmValuesOf16BitRange.foreach(pcm => pcm.toNormalised.toPcm16 shouldBe pcm)
  }

  it should "clip every finite value outside [-1.0, 1.0) to the extremes" in {
    val outside = Seq(1.0f, 1.0001f, 1.5f, 2.0f, 1e30f, Float.MaxValue)
    outside.foreach: sample =>
      sample.toPcm16 shouldBe Short.MaxValue
      (-sample).toPcm16 shouldBe Short.MinValue
  }

  it should "follow clamp(floor(x * 32768 + 0.5), -32768, 32767) on a seeded sample of values" in {
    val random  = Random(20260122L)
    val samples = Seq.fill(20000)((random.nextFloat() * 4f) - 2f)
    samples.foreach: sample =>
      val reference = math.floor(sample.toDouble * 32768.0 + 0.5).max(-32768.0).min(32767.0).toInt
      sample.toPcm16.toInt shouldBe reference
  }

  it should "round ties towards +infinity" in {
    (0.5f / 32768f).toPcm16 shouldBe (1: Short)
    (-0.5f / 32768f).toPcm16 shouldBe (0: Short)
    (1.5f / 32768f).toPcm16 shouldBe (2: Short)
    (-1.5f / 32768f).toPcm16 shouldBe (-1: Short)
  }

  it should "be monotonic: a larger sample never gives a smaller PCM value" in {
    val random = Random(7L)
    val sorted = Seq.fill(20000)((random.nextFloat() * 3f) - 1.5f).sorted
    val pcm    = sorted.map(_.toPcm16.toInt)
    pcm.zip(pcm.drop(1)).foreach((lower, higher) => (lower <= higher) shouldBe true)
  }

  "Representability" should "reject NaN and both infinities" in {
    Float.NaN.isRepresentable shouldBe false
    Float.PositiveInfinity.isRepresentable shouldBe false
    Float.NegativeInfinity.isRepresentable shouldBe false
  }

  it should "accept every finite value, including the ones that will be clipped" in {
    Seq(0.0f, -0.0f, 1.0f, -1.0f, 5.0f, Float.MaxValue, Float.MinValue, Float.MinPositiveValue)
      .foreach(_.isRepresentable shouldBe true)
  }

  "Reading little-endian bytes" should "put the low byte first" in {
    fromLittleEndianBytes(bytes(0x34, 0x12), 0) shouldBe (0x1234: Short)
  }

  it should "keep the sign carried by the high byte" in {
    fromLittleEndianBytes(bytes(0x00, 0x80), 0) shouldBe Short.MinValue
    fromLittleEndianBytes(bytes(0xff, 0x7f), 0) shouldBe Short.MaxValue
    fromLittleEndianBytes(bytes(0xff, 0xff), 0) shouldBe (-1: Short)
  }

  it should "address samples, not bytes" in {
    val encoded = bytes(0x01, 0x00, 0x02, 0x00, 0xfe, 0xff)
    (0 to 2).map(fromLittleEndianBytes(encoded, _).toInt) shouldBe Vector(1, 2, -2)
  }
