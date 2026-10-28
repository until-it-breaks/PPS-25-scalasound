package scalasound.state

import scalasound.domain.AudioTrack

/** Represents the current lifecycle status of the [[AudioPlayer]]. */
enum PlaybackStatus:
  case Stopped
  case Playing
  case Paused

/**
 * Core application state for the [[AudioPlayer]].
 *
 * @param status     state of the playback
 * @param track      the loaded track, if any
 * @param chunkIndex index of the next chunk to play
 */
final case class AppState(
  status: PlaybackStatus,
  track: Option[AudioTrack],
  chunkIndex: Int
):
  import PlaybackStatus.*

  /** Loads a new track, resetting the chunk index to 0 and stopping playback.*/
  def load(track: AudioTrack): AppState =
    AppState(Stopped, Some(track), 0)

  /** Transitions to Playing if a track is loaded, does nothing otherwise. */
  def play(): AppState =
    track match
      case None    => this
      case Some(_) => copy(status = Playing)

  /** Transitions to [[Paused]] if currently playing, does nothing otherwise. */
  def pause(): AppState =
    status match
      case Playing => copy(status = Paused)
      case _       => this

  /** Stops playback and resets chunk index to 0, keeping the loaded track. */
  def stop(): AppState =
    copy(status = Stopped, chunkIndex = 0)

  /** Seeks to the specified chunk index if a track is loaded. */
  def seek(index: Int): AppState =
    track match
      case None    => this
      case Some(_) => copy(chunkIndex = math.max(0, index))

  /** Advances the chunk index by one. */
  def nextChunk: AppState =
    copy(chunkIndex = chunkIndex + 1)

object AppState:
  import PlaybackStatus.*
  val initial: AppState = AppState(Stopped, None, 0)

import cats.effect.IO
import cats.effect.Ref

/** Storage abstraction for managing [[AppState]]. */
trait AppStateStore:
  /** Retrieves a snapshot of the current application state. */
  def current: IO[AppState]
  /** Updates the [[AppState]] using the provided function. */
  def update(f: AppState => AppState): IO[AppState]

/** A simple state store using a mutable variable. NOT thread-safe for concurrent writes. */
final class SimpleAppStateStore extends AppStateStore:
  private var state: AppState = AppState.initial

  def current: IO[AppState] = IO.pure(state)
  def update(f: AppState => AppState): IO[AppState] =
    IO {
      state = f(state)
      state
    }

/** A thread-safe, non-blocking state store backed by a [[Ref]]. */
final class CatsRefAppStateStore private(ref: Ref[IO, AppState]) extends AppStateStore:
  def current: IO[AppState] = ref.get
  def update(f: AppState => AppState): IO[AppState] = ref.updateAndGet(f)