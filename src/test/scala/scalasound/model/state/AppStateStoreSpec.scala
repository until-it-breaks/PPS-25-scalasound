package scalasound.model.state

import cats.effect.std.Queue
import cats.effect.{IO, Resource}
import fs2.Stream
import munit.CatsEffectSuite
import scalasound.model.domain.AudioChunk.MonoChunk
import scalasound.model.domain.MonoTrack
import scalasound.model.processing.AudioEffect
import scalasound.model.state.*

class AppStateStoreSpec extends CatsEffectSuite:

  private val dummyTrack =
    MonoTrack(44100, Vector(MonoChunk(Vector.empty))).fold(e => fail(e.toString), identity)
  private val otherTrack =
    MonoTrack(48000, Vector(MonoChunk(Vector.empty))).fold(e => fail(e.toString), identity)

  /** A fake store whose `changes` stream is a fixed list of states. Used to test the derived streams,
    * which only transform `changes`.
    */
  private def storeEmitting(states: AppState*): AppStateStore =
    new AppStateStore:
      def current: IO[AppState]              = IO.pure(states.last)
      def dispatch(c: Command): IO[AppState] = IO.pure(states.last)
      def changes: Stream[IO, AppState]      = Stream.emits(states)

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

  test("playbackStateChanges should drop repeated playback states") {
    val store = storeEmitting(
      base,
      base.copy(playbackState = PlaybackState.Playing),
      base.copy(playbackState = PlaybackState.Playing, chunkIndex = 1), // not a playback change
      base.copy(playbackState = PlaybackState.Paused)
    )
    store.playbackStateChanges.compile.toList.map { result =>
      assertEquals(
        result,
        List(PlaybackState.Stopped, PlaybackState.Playing, PlaybackState.Paused)
      )
    }
  }

  test("playbackProgressChanges should emit only when the playback state or the chunk index change") {
    val playing = base.copy(playbackState = PlaybackState.Playing)
    val store = storeEmitting(
      base,
      base.copy(effects = List(AudioEffect.volume(0.5f))), // same playback state and chunk
      playing,
      playing.copy(effects = List(AudioEffect.volume(0.5f))), // same playback state and chunk
      playing.copy(chunkIndex = 1),
      playing.copy(chunkIndex = 1, playbackState = PlaybackState.Paused)
    )
    store.playbackProgressChanges.compile.toList.map { result =>
      assertEquals(result.map(s => (s.playbackState, s.chunkIndex)), List(
        (PlaybackState.Stopped, 0),
        (PlaybackState.Playing, 0),
        (PlaybackState.Playing, 1),
        (PlaybackState.Paused, 1)
      ))
    }
  }

  test("playheadChanges should emit only when the track or the chunk index change") {
    val store = storeEmitting(
      base,
      base.copy(playbackState = PlaybackState.Playing), // same track and chunk
      base.copy(track = Some(dummyTrack)),
      base.copy(track = Some(dummyTrack), chunkIndex = 1),
      base.copy(track = Some(dummyTrack), chunkIndex = 1, playbackState = PlaybackState.Paused),
      base.copy(track = Some(otherTrack), chunkIndex = 1) // another track, same chunk index
    )
    store.playheadChanges.compile.toList.map { result =>
      assertEquals(
        result,
        List((None, 0), (Some(dummyTrack), 0), (Some(dummyTrack), 1), (Some(otherTrack), 1))
      )
    }
  }

  test("importStateChanges should drop repeated import states") {
    val store = storeEmitting(
      base,
      base.copy(importState = ImportState.Importing),
      base.copy(importState = ImportState.Importing, chunkIndex = 1),
      base.copy(importState = ImportState.Imported(dummyTrack), track = Some(dummyTrack))
    )
    store.importStateChanges.compile.toList.map { result =>
      assertEquals(
        result,
        List(ImportState.Idle, ImportState.Importing, ImportState.Imported(dummyTrack))
      )
    }
  }

  test("exportStateChanges should drop repeated export states") {
    val store = storeEmitting(
      base,
      base.copy(exportState = ExportState.Exporting),
      base.copy(exportState = ExportState.Exporting, playbackState = PlaybackState.Playing),
      base.copy(exportState = ExportState.Exported)
    )
    store.exportStateChanges.compile.toList.map { result =>
      assertEquals(
        result,
        List(ExportState.Idle, ExportState.Exporting, ExportState.Exported)
      )
    }
  }

  /** Runs `stream` in the background and pushes each emission into a queue.
    */
  private def subscribe[A](stream: Stream[IO, A]): Resource[IO, Queue[IO, A]] =
    for
      queue <- Resource.eval(Queue.unbounded[IO, A])
      _     <- stream.evalMap(queue.offer).compile.drain.background
    yield queue

  test("a live subscriber should receive states as they are dispatched") {
    for
      store  <- AppStateStore.make
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
