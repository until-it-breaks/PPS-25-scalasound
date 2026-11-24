package scalasound.controller

import cats.effect.IO
import scalasound.model.state.AppStateStore
import scalasound.model.state.Command.{Pause, Play, SeekToIndex, Stop}

import scala.concurrent.duration.FiniteDuration

/** The operations a user can perform on playback: play, pause, stop and seek.
  */
trait PlaybackController:
  /** Starts or resumes playback.
    */
  def play: IO[Unit]

  /** Pauses playback, keeping the current position.
    */
  def pause: IO[Unit]

  /** Stops playback.
    */
  def stop: IO[Unit]

  /** Moves the play head to the given position in the current track.
    *
    * @param position
    *   the target position, measured from the start of the track
    */
  def seek(position: FiniteDuration): IO[Unit]

object PlaybackController:

  /** Creates a [[PlaybackController]] that carries out each request by dispatching a command to the
    * given store.
    *
    * @param store
    *   the [[AppStateStore]] that receives the playback commands
    */
  def apply(store: AppStateStore): PlaybackController = PlaybackControllerImpl(store)

private final class PlaybackControllerImpl(store: AppStateStore) extends PlaybackController:

  def play: IO[Unit] = store.dispatch(Play).void

  def pause: IO[Unit] = store.dispatch(Pause).void

  def stop: IO[Unit] = store.dispatch(Stop).void

  def seek(position: FiniteDuration): IO[Unit] =
    for
      state <- store.current
      _     <- state.track match
        case None        => IO.unit
        case Some(track) => store.dispatch(SeekToIndex(track.chunkIndexAt(position))).void
    yield ()
