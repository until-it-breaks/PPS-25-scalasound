package scalasound.player

import cats.effect.IO
import fs2.Stream
import scalasound.processing.AudioProcessor
import scalasound.state.AppStateStore

/**
 * Plays the track in the [[AppState]] through an [[AudioOutput]].
 *
 * An [[AudioPlayer]] only watches the [[AppStateStore]] and reacts to it: while `Playing` it plays from
 * `chunkIndex`, advances the play head with `NextChunk`, and dispatches `Stop` at the end
 * of the track. It does nothing until [[run]] is called.
 */
trait AudioPlayer:

  /**
   * Starts listening to the [[AppStateStore]] and playing audio accordingly.
   *
   * @param store the [[AppStateStore]] to watch for [[AppState]] changes and to dispatch play head updates to.
   * @return a stream that never completes on its own; cancel it to stop the player
   */
  def run(store: AppStateStore): Stream[IO, Unit]

object AudioPlayer:

  /**
   * An [[AudioPlayer]] that plays one chunk at a time, processing each chunk just before writing it.
   *
   * @param processor used to apply [[AudioEffect]]s to each [[AudioChunk]] just before it is written
   * @param out where the processed chunks are written (e.g. the speakers)
   * @return a player that is idle until [[AudioPlayer.run]] is called
   */
  def streaming(processor: AudioProcessor, out: AudioOutput): AudioPlayer =
    StreamingAudioPlayer(processor, out)