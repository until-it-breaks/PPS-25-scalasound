package scalasound.model.state

import cats.effect.std.Queue
import cats.effect.{IO, Resource}
import fs2.Stream
import munit.CatsEffectSuite
import scalasound.model.domain.AudioChunk.MonoChunk
import scalasound.model.domain.MonoTrack
import scalasound.model.state.{AppState, AppStateStore, Command, ExportState, ImportState, PlaybackState}

class AppStateStoreSpec extends CatsEffectSuite:

  private val dummyTrack = MonoTrack(44100, Vector(MonoChunk(Vector.empty)))
  private val otherTrack = MonoTrack(48000, Vector(MonoChunk(Vector.empty)))

  /**
   * A fake store whose `changes` stream is a fixed list of states.
   * Used to test `playbackChanges` / `trackChanges`, which only transform `changes`.
   */
  private def storeEmitting(states: AppState*): AppStateStore =
    new AppStateStore:
      def current: IO[AppState]                 = IO.pure(states.last)
      def dispatch(c: Command): IO[AppState]    = IO.pure(states.last)
      def changes: Stream[IO, AppState]         = Stream.emits(states)

  private val base = AppState.initial

  test("should initialize with AppState.initial") {
    AppStateStore.make.flatMap(_.current).map { state =>
      assertEquals(state, AppState.initial)
    }
  }

  test("dispatch should update the current state") {
    for
      store <- AppStateStore.make
      _     <- store.dispatch(Command.UpdateImport(ImportState.Imported(dummyTrack)))
      _     <- store.dispatch(Command.Play)
      state <- store.current
    yield
      assertEquals(state.track, Some(dummyTrack))
      assertEquals(state.playbackState, PlaybackState.Playing)
  }

  test("a new subscriber should start from the latest state") {
    for
      store <- AppStateStore.make
      _     <- store.dispatch(Command.UpdateImport(ImportState.Imported(dummyTrack)))
      _     <- store.dispatch(Command.Play)
      first <- store.changes.take(1).compile.lastOrError
    yield assertEquals(first.playbackState, PlaybackState.Playing)
  }

  test("playbackChanges should drop repeated playback states") {
    val store = storeEmitting(
      base,
      base.copy(playbackState = PlaybackState.Playing),
      base.copy(playbackState = PlaybackState.Playing, chunkIndex = 1), // not a playback change
      base.copy(playbackState = PlaybackState.Paused)
    )
    store.playbackChanges.compile.toList.map { result =>
      assertEquals(
        result,
        List(PlaybackState.Stopped, PlaybackState.Playing, PlaybackState.Paused)
      )
    }
  }

  test("trackChanges should drop repeated tracks") {
    val store = storeEmitting(
      base,
      base.copy(track = Some(dummyTrack)),
      base.copy(track = Some(dummyTrack), chunkIndex = 1), // same track
      base.copy(track = Some(otherTrack))
    )
    store.trackChanges.compile.toList.map { result =>
      assertEquals(result, List(None, Some(dummyTrack), Some(otherTrack)))
    }
  }

  /**
   * Runs `stream` in the background and pushes each emission into a queue.
   */
  private def subscribe[A](stream: Stream[IO, A]): Resource[IO, Queue[IO, A]] =
    for
      queue <- Resource.eval(Queue.unbounded[IO, A])
      _     <- stream.evalMap(queue.offer).compile.drain.background
    yield queue

  test("a live subscriber should receive states as they are dispatched") {
    for
      store <- AppStateStore.make
      result <- subscribe(store.changes).use { queue =>
        for
          initial <- queue.take
          _       <- store.dispatch(Command.UpdateExport(ExportState.Exporting))
          next    <- queue.take
        yield (initial, next)
      }
    yield
      assertEquals(result._1, AppState.initial)
      assertEquals(result._2.exportState, ExportState.Exporting)
  }