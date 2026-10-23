package scalasound.domain

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scalasound.domain.AudioChunk.{MonoChunk, StereoChunk}

class AudioTrackSpec extends AnyFlatSpec with Matchers:

  private def mono(n: Int): MonoChunk = MonoChunk(Vector.fill(n)(0f))
  private def stereo(n: Int): StereoChunk = StereoChunk(Vector.fill(n)(0f), Vector.fill(n)(0f))

  "A mono track with one chunk" should "last 'frames / sampleRate' seconds" in {
    MonoTrack(44100, Vector(mono(22050))).durationInSeconds shouldBe 0.5
  }

  "A mono track with chunks of different sizes" should "sum all frames, not count chunks" in {
    val track = MonoTrack(1000, Vector(mono(1024), mono(1024), mono(452)))
    track.frameCount shouldBe 2500L
    track.durationInSeconds shouldBe 2.5
  }

  "A stereo track" should "count one frame per left-right pair" in {
    val track = StereoTrack(100, Vector(stereo(100), stereo(50)))
    track.frameCount shouldBe 150L
    track.durationInSeconds shouldBe 1.5
  }

  "Tracks without frames" should "have zero duration" in {
    MonoTrack(44100, Vector.empty).durationInSeconds shouldBe 0.0
    StereoTrack(44100, Vector.empty).durationInSeconds shouldBe 0.0
    MonoTrack(44100, Vector(MonoChunk(Vector.empty))).durationInSeconds shouldBe 0.0
  }

  "A track" should "reject a non-positive sample rate" in {
    an[IllegalArgumentException] should be thrownBy MonoTrack(0, Vector.empty)
    an[IllegalArgumentException] should be thrownBy StereoTrack(-1, Vector.empty)
  }

  "A stereo track" should "reject chunks whose channels have different lengths" in {
    an[IllegalArgumentException] should be thrownBy
      StereoTrack(44100, Vector(StereoChunk(Vector(0f, 0f), Vector(0f))))
  }
