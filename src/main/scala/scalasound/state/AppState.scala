package scalasound.state

import scalasound.domain.AudioTrack

/**
 * Represents the current lifecycle status of the audio player.
 */
enum Status:
  case Stopped
  case Playing
  case Paused

/**
 * Everything the [[AudioPlayer]] needs to know, modeled as an immutable data structure.
 *
 * @param status     whether playback is running
 * @param track      the loaded track, if any
 * @param chunkIndex index of the next chunk to play
 */
final case class AppState(
  status: Status,
  track: Option[AudioTrack],
  chunkIndex: Int
):
  /**
   * Transitions the state to [[Status.Playing]] if a track is loaded.
   * Returns the unmodified state if no track is present.
   */
  def play(): AppState =
    track match
      case None    => this
      case Some(_) => copy(status = Status.Playing)

  /**
   * Transitions the state to [[Status.Paused]] if playback is currently active.
   * Returns the unmodified state otherwise.
   */
  def pause(): AppState =
    status match
      case Status.Playing => copy(status = Status.Paused)
      case _              => this

  /**
   * Stops playback, resetting the chunk index back to `0`.
   * The loaded track is retained so playback can be restarted from the beginning.
   */
  def stop(): AppState =
    copy(status = Status.Stopped, chunkIndex = 0)

  /**
   * Moves the playback position to the specified chunk index if a track is loaded.
   *
   * @param index the target chunk index to jump to
   */
  def seek(index: Int): AppState =
    track match
      case None    => this
      case Some(_) => copy(chunkIndex = index)

  /**
   * Advances the playback position by one chunk.
   */
  def nextChunk: AppState =
    copy(chunkIndex = chunkIndex + 1)

object AppState:
  val initial: AppState = AppState(Status.Stopped, None, 0)

/**
 * A storage abstraction for managing the [[AppState]].
 */
trait StateStore:
  /**
   * Retrieves a snapshot of the current application state.
   *
   * @return the latest [[AppState]] instance
   */
  def current: AppState

  /**
   * Updates the [[AppState]] using the provided function.
   *
   * @param f a function transforming the current state to a new state
   * @return the newly updated [[AppState]] after the transformation is applied
   */
  def update(f: AppState => AppState): AppState

/**
 * A simple state store using a variable.
 */
final class SimpleStateStore extends StateStore:
  private var state: AppState = AppState.initial

  def current: AppState = state

  def update(f: AppState => AppState): AppState =
    state = f(state)
    state