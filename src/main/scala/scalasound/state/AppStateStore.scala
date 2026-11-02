package scalasound.state

import cats.Eq
import cats.effect.IO
import fs2.Stream
import fs2.concurrent.SignallingRef
import scalasound.domain.AudioTrack

given Eq[PlaybackState] = Eq.fromUniversalEquals
given Eq[ImportState] = Eq.fromUniversalEquals
given Eq[ExportState] = Eq.fromUniversalEquals
given Eq[Option[AudioTrack]] = Eq.fromUniversalEquals

/** Storage abstraction for managing [[AppState]] and exposing real-time change streams. */
trait AppStateStore:
  /** Retrieves a snapshot of the current [[AppState]]. */
  def current: IO[AppState]

  /** Dispatches a [[Command]] */
  def dispatch(command: Command): IO[AppState]

  /** A continuous stream of [[AppState]] values: the current state first, then the latest state
   * after each update. */
  def changes: Stream[IO, AppState]

  /** A dedicated stream for playback state changes only. */
  def playbackChanges: Stream[IO, PlaybackState] =
    changes.map(_.playbackState).changes

  /** A dedicated stream for loaded track changes only. */
  def trackChanges: Stream[IO, Option[AudioTrack]] =
    changes.map(_.track).changes

object AppStateStore:
  /** Creates a new thread-safe app state store instance initialized to [[AppState.initial]]. */
  def make: IO[AppStateStore] =
    SignallingRef[IO, AppState](AppState.initial).map { sigRef =>
      new AppStateStore:
        def current: IO[AppState] = sigRef.get

        def dispatch(command: Command): IO[AppState] =
          sigRef.updateAndGet(_.handle(command))

        def changes: Stream[IO, AppState] = sigRef.discrete
    }