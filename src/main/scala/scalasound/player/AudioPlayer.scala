package scalasound.player

import cats.effect.IO
import scalasound.domain.AudioChunk.MonoChunk
import scalasound.domain.{AudioChunk, AudioTrack, MonoTrack, StereoTrack}
import scalasound.processing.AudioProcessor
import scalasound.state.{AppState, AppStateStore}

/**
 * Controls playback of a loaded track.
 */
trait AudioPlayer:
  def play(): IO[Unit]
  def pause(): IO[Unit]
  def stop(): IO[Unit]
  def seek(chunkIndex: Int): IO[Unit]

/**
 * Streams a track chunk by chunk, driven by calling [[tick]].
 *
 * @param store     holds the [[AppState]]
 * @param processor applies the active effects to each chunk before output
 * @param out       receives each processed chunk to be played
 */
final class StreamingAudioPlayer(
  store: AppStateStore,
  processor: AudioProcessor,
  out: AudioOutput
) extends AudioPlayer:
  import scalasound.state.PlaybackStatus.*

  // temporary. adt needs some changes
  extension (track: AudioTrack)
    private def chunks: Vector[AudioChunk] = track match
      case t: MonoTrack => t.chunks
      case t: StereoTrack => t.chunks
      case other => throw IllegalArgumentException("Unsupported track")

  def play(): IO[Unit] =
    store.update(_.play()).void

  def pause(): IO[Unit] =
    store.update(_.pause()).void

  def stop(): IO[Unit] =
    store.update(_.stop()).void

  def seek(index: Int): IO[Unit] =
    store.update(_.seek(index)).void

  /** Executes a single streaming tick, returning true if playback continues. */
  def tick(): IO[Boolean] =
    store.current.flatMap {
      case AppState(Playing, Some(track), index) =>
        track.chunks.lift(index) match
          case Some(chunk) =>
            // TODO handle error
            IO { out.write(processor.apply(chunk, List()).getOrElse(MonoChunk(Vector(0f))), track.sampleRate) } *>
              store.update(_.nextChunk).as(true)
          case None =>
            store.update(_.stop()).as(false)
      case _ =>
        IO.pure(false)
    }