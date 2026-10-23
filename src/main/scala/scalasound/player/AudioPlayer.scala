package scalasound.player

import scalasound.domain.{AudioChunk, AudioTrack, MonoTrack, StereoTrack}
import scalasound.processing.AudioProcessor
import scalasound.state.{AppState, StateStore}

/**
 * Controls playback of a loaded track.
 */
trait AudioPlayer:
  /**
   * Starts playback, or resumes it if paused.
   */
  def play(): Unit

  /**
   * Pauses playback, keeping the current position.
   */
  def pause(): Unit

  /**
   * Stops playback, rewinds to chunk 0, and keeps the track loaded.
   */
  def stop(): Unit

  /**
   * Moves to the given chunk index, keeping the current status.
   *
   * @param chunkIndex index of the chunk to jump to
   */
  def seek(chunkIndex: Int): Unit

/**
 * Streams a track chunk by chunk, reading state externally from a [[StateStore]].
 * Playback must be driven by calling [[tick]] repeatedly.
 *
 * @param store     holds the playback state (status, track, chunk index)
 * @param processor applies the active effects to each chunk before output
 * @param out      receives each processed chunk; may block until the audio is consumed
 */
final class StreamingAudioPlayer(
  store: StateStore,
  processor: AudioProcessor,
  out: AudioOutput
) extends AudioPlayer:
  import scalasound.state.Status.*

  // temporary. adt needs some changes
  extension (track: AudioTrack)
    private def chunks: Vector[AudioChunk] = track match
      case t: MonoTrack => t.chunks
      case t: StereoTrack => t.chunks
      case other => throw IllegalArgumentException("Unsupported track")

  def play(): Unit =
    store.update(_.play())

  def pause(): Unit =
    store.update(_.pause())

  def stop(): Unit =
    store.update(_.stop())

  def seek(index: Int): Unit =
    store.update(_.seek(index))

  def tick(): Boolean =
    val current = store.current
    current match
      case AppState(Playing, Some(track), index) =>
        track.chunks.lift(index) match
          case Some(chunk) =>
            out.write(processor.process(chunk, List()), track.sampleRate)
            store.update(_.nextChunk)
            true
          case None =>
            store.update(_.stop())
            false
      case _ => false