package scalasound.controller

import cats.effect.IO
import scalasound.model.domain.AudioTrack
import scalasound.model.state.AppStateStore
import scalasound.model.state.Command.{Pause, Play, SeekToIndex, Stop}

import scala.annotation.tailrec

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

  /** Moves play head to the given position in the current track.
    *
    * @param positionSeconds
    *   the target position in seconds
    */
  def seek(positionSeconds: Double): IO[Unit]

/** A [[PlaybackController]] that carries out each request by dispatching a command to the
  * [[AppStateStore]].
  *
  * @param store
  *   the [[AppStateStore]] that receives the playback commands
  */
class PlaybackControllerImpl(store: AppStateStore) extends PlaybackController:

  def play: IO[Unit] = store.dispatch(Play).void

  def pause: IO[Unit] = store.dispatch(Pause).void

  def stop: IO[Unit] = store.dispatch(Stop).void

  def seek(positionSeconds: Double): IO[Unit] =
    for
      state <- store.current
      _     <- state.track match
        case None        => IO.unit
        case Some(track) => store.dispatch(SeekToIndex(track.chunkIndexAt(positionSeconds))).void
    yield ()

  extension (track: AudioTrack)
    /** Index of the chunk that corresponds to the given position in seconds. Positions before the
      * start go to the first chunk, positions past the end go to the last one.
      */
    private def chunkIndexAt(positionSeconds: Double): Int =
      val targetFrame = (positionSeconds * track.sampleRate).toLong.max(0L)
      @tailrec
      def loop(index: Int, framesBefore: Long): Int =
        track.chunks.lift(index) match
          case None =>
            (track.chunks.length - 1).max(0)
          case Some(chunk) if targetFrame < framesBefore + chunk.frameCount =>
            index
          case Some(chunk) =>
            loop(index + 1, framesBefore + chunk.frameCount)

      loop(0, 0L)
