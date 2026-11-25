package scalasound.model.domain

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scalasound.model.domain.AudioChunk.{MonoChunk, StereoChunk}
import scalasound.model.domain.{AudioDomainError, MonoTrack, StereoTrack}

import scala.concurrent.duration.*

class AudioTrackSpec extends AnyFlatSpec with Matchers with EitherValues:

  private def mono(n: Int): MonoChunk     = MonoChunk(Vector.fill(n)(0f))
  private def stereo(n: Int): StereoChunk = StereoChunk(Vector.fill(n)(0f), Vector.fill(n)(0f))

  "A chunk" should "be empty when all its channels have no samples" in:
    mono(0).isEmpty shouldBe true
    stereo(0).isEmpty shouldBe true

  it should "not be empty when it has samples" in:
    mono(1).isEmpty shouldBe false
    stereo(1).isEmpty shouldBe false

  it should "not be empty when only one channel of a stereo chunk has samples" in:
    StereoChunk(Vector(0f), Vector.empty).isEmpty shouldBe false
    StereoChunk(Vector.empty, Vector(0f)).isEmpty shouldBe false

  "A mono track with one chunk" should "last 'frames / sampleRate' seconds" in:
    MonoTrack(44100, Vector(mono(22050))).value.duration shouldBe 500.millis

  "A mono track with chunks of different sizes" should "sum all frames, not count chunks" in:
    val track = MonoTrack(1000, Vector(mono(1024), mono(1024), mono(452))).value
    track.frameCount shouldBe 2500L
    track.duration shouldBe 2500.millis

  "A stereo track" should "count one frame per left-right pair" in:
    val track = StereoTrack(100, Vector(stereo(100), stereo(50))).value
    track.frameCount shouldBe 150L
    track.duration shouldBe 1500.millis

  "Tracks without frames" should "have zero duration" in:
    MonoTrack(44100, Vector.empty).value.duration shouldBe Duration.Zero
    StereoTrack(44100, Vector.empty).value.duration shouldBe Duration.Zero
    MonoTrack(44100, Vector(MonoChunk(Vector.empty))).value.duration shouldBe Duration.Zero

  "A track" should "reject a non-positive sample rate" in:
    MonoTrack(0, Vector.empty) shouldBe Left(AudioDomainError.InvalidSampleRate(0))
    StereoTrack(-1, Vector.empty) shouldBe Left(AudioDomainError.InvalidSampleRate(-1))

  "A stereo track" should "reject chunks whose channels have different lengths" in:
    StereoTrack(44100, Vector(StereoChunk(Vector(0f, 0f), Vector(0f)))) shouldBe
      Left(
        AudioDomainError.DifferentLengthsStereoChannels(
          chunkIndex = 0,
          leftLength = 2,
          rightLength = 1
        )
      )

  "chunkIndexAt" should "return the chunk that contains the given position" in:
    val track = MonoTrack(1000, Vector(mono(1000), mono(1000), mono(500))).value
    track.chunkIndexAt(Duration.Zero) shouldBe 0
    track.chunkIndexAt(999.millis) shouldBe 0
    track.chunkIndexAt(1.second) shouldBe 1
    track.chunkIndexAt(2400.millis) shouldBe 2

  it should "go to the first or the last chunk for positions outside the track" in:
    val track = MonoTrack(1000, Vector(mono(1000), mono(1000), mono(500))).value
    track.chunkIndexAt((-3).seconds) shouldBe 0
    track.chunkIndexAt(10.seconds) shouldBe 2

  it should "be 0 for a track without chunks" in:
    MonoTrack(1000, Vector.empty).value.chunkIndexAt(5.seconds) shouldBe 0

  "positionAt" should "return the time at which the chunk starts" in:
    val track = MonoTrack(1000, Vector(mono(1000), mono(1000), mono(500))).value
    track.positionAt(0) shouldBe Duration.Zero
    track.positionAt(1) shouldBe 1.second
    track.positionAt(2) shouldBe 2.seconds
