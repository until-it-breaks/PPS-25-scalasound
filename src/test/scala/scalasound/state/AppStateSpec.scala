package scalasound.state

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import scalasound.domain.MonoTrack

class AppStateSpec extends AnyFlatSpec with Matchers:
  private val track = MonoTrack(44100, Vector.empty)

  "AppState initial state" should "be Stopped with no track and chunk 0" in {
    val state = AppState.initial
    state.status shouldBe PlaybackStatus.Stopped
    state.track shouldBe None
    state.chunkIndex shouldBe 0
  }

  "load track" should "reset to Stopped, set track, and reset chunkIndex to 0" in {
    val state = AppState.initial
      .copy(chunkIndex = 5)
      .load(track)
    state.status shouldBe PlaybackStatus.Stopped
    state.track shouldBe Some(track)
    state.chunkIndex shouldBe 0
  }

  "play transition" should "transition to Playing only if a track is loaded" in {
    val noTrackState = AppState.initial.play()
    noTrackState.status shouldBe PlaybackStatus.Stopped
    val loadedState = AppState.initial.load(track).play()
    loadedState.status shouldBe PlaybackStatus.Playing
  }

  "pause transition" should "transition to Paused only if currently Playing" in {
    val stoppedState = AppState.initial.load(track).pause()
    stoppedState.status shouldBe PlaybackStatus.Stopped
    val playingState = AppState.initial.load(track).play().pause()
    playingState.status shouldBe PlaybackStatus.Paused
  }

  "stop transition" should "reset status to Stopped and chunkIndex to 0 while keeping track" in {
    val state = AppState.initial
      .load(track)
      .play()
      .nextChunk
      .nextChunk
      .stop()

    state.status shouldBe PlaybackStatus.Stopped
    state.track shouldBe Some(track)
    state.chunkIndex shouldBe 0
  }

  "seek" should "update chunkIndex if track is loaded and clamp negative values" in {
    val stateWithTrack = AppState.initial.load(track)
    stateWithTrack.seek(15).chunkIndex shouldBe 15
    stateWithTrack.seek(-5).chunkIndex shouldBe 0
    val stateNoTrack = AppState.initial.seek(10)
    stateNoTrack.chunkIndex shouldBe 0
  }

  "nextChunk" should "increment chunkIndex by 1" in {
    val state = AppState.initial.nextChunk.nextChunk
    state.chunkIndex shouldBe 2
  }