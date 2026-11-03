package scalasound.controller

import cats.effect.IO
import scalasound.model.domain.AudioChunk
import scalasound.model.state.{AppStateStore, Command}
import scalasound.model.state.Command.{Pause, Play, Stop}

trait PlaybackController:
  def play: IO[Unit]
  def pause: IO[Unit]
  def stop: IO[Unit]
  def seek(positionSeconds: Double): IO[Unit]

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
      if index >= chunks.length then
        math.max(0, chunks.length - 1)
      else
        val nextAccumulated =
          accumulatedFrames + chunks(index).frameCount
        if targetFrame < nextAccumulated then
          index
        else
          loop(index + 1, nextAccumulated)
    loop(0, 0L)