package scalasound.controller

import cats.effect.IO
import scalasound.model.domain.AudioChunk
import scalasound.model.state.{AppStateStore, Command}
import scalasound.model.state.Command.{Pause, Play, Stop}

/**
 * The operations a user can perform on playback: play, pause, stop and seek.
 */
trait PlaybackController:
  /**
   * Starts or resumes playback.
   */
  def play: IO[Unit]
  /**
   * Pauses playback, keeping the current position.
   */
  def pause: IO[Unit]
  /**
   * Stops playback.
   */
  def stop: IO[Unit]

  /**
   * Moves play head to the given position in the current track.
   *
   * @param positionSeconds the target position in seconds
   */
  def seek(positionSeconds: Double): IO[Unit]

/**
 * A [[PlaybackController]] that carries out each request by dispatching a command to the [[AppStateStore]].
 *
 * @param store the [[AppStateStore]] that receives the playback commands
 */
class PlaybackControllerImpl(store: AppStateStore) extends PlaybackController:

  def play: IO[Unit] =
    store.dispatch(Play).void

  def pause: IO[Unit] =
    store.dispatch(Pause).void

  def stop: IO[Unit] =
    store.dispatch(Stop).void

  def seek(positionSeconds: Double): IO[Unit] =
    store.current.flatMap { state =>
      state.track match
        case None =>
          IO.unit
        case Some(track) =>
          val targetFrame =
            math.max(0L, (positionSeconds * track.sampleRate).toLong)
          val index =
            findChunkIndexForFrame(track.chunks, targetFrame)
          store.dispatch(Command.SeekToIndex(index)).void
    }

  private def findChunkIndexForFrame(chunks: Vector[AudioChunk], targetFrame: Long): Int =
    import scala.annotation.tailrec
    @tailrec
    def loop(index: Int, accumulatedFrames: Long): Int =
      chunks.lift(index) match
        case None =>
          math.max(0, chunks.length - 1)
        case Some(chunk) =>
          val nextAccumulated = accumulatedFrames + chunk.frameCount
          targetFrame < nextAccumulated match
            case true => index
            case false => loop(index + 1, nextAccumulated)

    loop(0, 0L)