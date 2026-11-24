package scalasound.controller

import cats.effect.IO
import munit.CatsEffectSuite
import scalasound.model.domain.{AudioChunk, MonoTrack}
import scalasound.model.state.{AppStateStore, Command, ImportState, PlaybackState}

import scala.concurrent.duration.*

class PlaybackControllerSpec extends CatsEffectSuite:

  private val sampleRate     = 1000
  private val framesPerChunk = 1000
  private val chunkCount     = 5

  private val track = MonoTrack(
    sampleRate,
    Vector.fill(chunkCount)(AudioChunk.MonoChunk(Vector.fill(framesPerChunk)(0.0f)))
  ).fold(e => fail(e.toString), identity)

  private def setupWithTrack: IO[(AppStateStore, PlaybackController)] =
    for
      store <- AppStateStore.make
      _     <- store.dispatch(Command.UpdateImport(ImportState.Imported(track)))
    yield (store, PlaybackController(store))

  test("play starts playback when a track is loaded") {
    for
      (store, controller) <- setupWithTrack
      _                   <- controller.play
      state               <- store.current
    yield assertEquals(state.playbackState, PlaybackState.Playing)
  }

  test("play does nothing when no track is loaded") {
    for
      store <- AppStateStore.make
      _     <- PlaybackController(store).play
      state <- store.current
    yield assertEquals(state.playbackState, PlaybackState.Stopped)
  }

  test("pause keeps the position") {
    for
      (store, controller) <- setupWithTrack
      _                   <- controller.seek(2500.millis)
      _                   <- controller.play
      _                   <- controller.pause
      state               <- store.current
    yield
      assertEquals(state.playbackState, PlaybackState.Paused)
      assertEquals(state.chunkIndex, 2)
  }

  test("stop resets the play head to the start") {
    for
      (store, controller) <- setupWithTrack
      _                   <- controller.seek(2500.millis)
      _                   <- controller.play
      _                   <- controller.stop
      state               <- store.current
    yield
      assertEquals(state.playbackState, PlaybackState.Stopped)
      assertEquals(state.chunkIndex, 0)
  }

  test("seek moves to the chunk that corresponds to that given time in seconds") {
    for
      (store, controller) <- setupWithTrack
      _                   <- controller.seek(2500.millis)
      state               <- store.current
    yield assertEquals(state.chunkIndex, 2) // 2.5 s falls in chunk 2
  }

  test("seek to a chunk boundary picks the chunk that starts there") {
    for
      (store, controller) <- setupWithTrack
      _                   <- controller.seek(3.seconds)
      state               <- store.current
    yield assertEquals(state.chunkIndex, 3)
  }

  test("seek to 0 goes to the first chunk") {
    for
      (store, controller) <- setupWithTrack
      _                   <- controller.seek(2500.millis)
      _                   <- controller.seek(Duration.Zero)
      state               <- store.current
    yield assertEquals(state.chunkIndex, 0)
  }

  test("seek past the end goes to the last chunk") {
    for
      (store, controller) <- setupWithTrack
      _                   <- controller.seek(100.seconds)
      state               <- store.current
    yield assertEquals(state.chunkIndex, chunkCount - 1)
  }

  test("seek to a negative position goes to the first chunk") {
    for
      (store, controller) <- setupWithTrack
      _                   <- controller.seek(2500.millis)
      _                   <- controller.seek((-5).seconds)
      state               <- store.current
    yield assertEquals(state.chunkIndex, 0)
  }

  test("seek works while stopped and does not start playback") {
    for
      (store, controller) <- setupWithTrack
      _                   <- controller.seek(1500.millis)
      state               <- store.current
    yield
      assertEquals(state.playbackState, PlaybackState.Stopped)
      assertEquals(state.chunkIndex, 1)
  }

  test("seek does nothing when no track is loaded") {
    for
      store <- AppStateStore.make
      _     <- PlaybackController(store).seek(10.seconds)
      state <- store.current
    yield assertEquals(state.chunkIndex, 0)
  }
