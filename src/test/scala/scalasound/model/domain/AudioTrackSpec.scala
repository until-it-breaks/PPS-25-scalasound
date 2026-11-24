package scalasound.model.domain

import org.scalatest.EitherValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scalasound.model.domain.AudioChunk.{MonoChunk, StereoChunk}
import scalasound.model.domain.{AudioDomainError, MonoTrack, StereoTrack}

class AudioTrackSpec extends AnyFlatSpec with Matchers with EitherValues:

  private def mono(n: Int): MonoChunk     = MonoChunk(Vector.fill(n)(0f))
  private def stereo(n: Int): StereoChunk = StereoChunk(Vector.fill(n)(0f), Vector.fill(n)(0f))

  "A mono track with one chunk" should "last 'frames / sampleRate' seconds" in:
    MonoTrack(44100, Vector(mono(22050))).value.durationInSeconds shouldBe 0.5

  "A mono track with chunks of different sizes" should "sum all frames, not count chunks" in:
    val track = MonoTrack(1000, Vector(mono(1024), mono(1024), mono(452))).value
    track.frameCount shouldBe 2500L
    track.durationInSeconds shouldBe 2.5

  "A stereo track" should "count one frame per left-right pair" in:
    val track = StereoTrack(100, Vector(stereo(100), stereo(50))).value
    track.frameCount shouldBe 150L
    track.durationInSeconds shouldBe 1.5

  "Tracks without frames" should "have zero duration" in:
    MonoTrack(44100, Vector.empty).value.durationInSeconds shouldBe 0.0
    StereoTrack(44100, Vector.empty).value.durationInSeconds shouldBe 0.0
    MonoTrack(44100, Vector(MonoChunk(Vector.empty))).value.durationInSeconds shouldBe 0.0

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
