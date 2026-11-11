package scalasound.model.player

import cats.effect.IO
import cats.kernel.Eq
import fs2.Stream
import scalasound.model.processing.AudioProcessor
import scalasound.model.state.{AppState, AppStateStore, Command, PlaybackState}

/**
 * An [[AudioPlayer]] that plays one chunk at a time, processing each chunk just before writing it.
 *
 * @param processor used to apply [[AudioEffect]]s to each [[AudioChunk]] just before it is written
 * @param out where the processed chunks are written (e.g. the speakers)
 */
final class StreamingAudioPlayer(processor: AudioProcessor, out: AudioOutput) extends AudioPlayer:

  private given Eq[(PlaybackState, Int)] = Eq.fromUniversalEquals

  def run(store: AppStateStore): Stream[IO, Unit] =
    store.changes
      .changesBy(state => (state.playbackState, state.chunkIndex))
      .switchMap { state =>
        state.playbackState match
          case PlaybackState.Playing => Stream.eval(playChunk(state, store))
          case _                     => Stream.empty
      }
      .handleErrorWith { error =>
        Stream.eval(
          for
            _ <- IO.println(s"Audio player failed: $error")
            _ <- store.dispatch(Command.Stop)
          yield ()
        )
      }

  /**
   * Writes the chunk at the play head, then advances the play head.
   *
   * If there is nothing to play (no track, or the play head is past the last chunk), it
   * dispatches `Stop` instead.
   *
   * @param state the state that triggered this write; its `chunkIndex` picks the chunk
   * @param store the store to dispatch `NextChunk` or `Stop` to
   */
  private def playChunk(state: AppState, store: AppStateStore): IO[Unit] =
    val current =
      for
        track <- state.track
        chunk <- track.chunks.lift(state.chunkIndex)
      yield (track, chunk)
    current match
      case Some((track, chunk)) =>
        val processed = processor.apply(chunk, List(), state.chunkIndex).getOrElse(chunk) // TODO pass the actual list of effects when available in the AppState
        for
          _ <- out.write(processed, track.sampleRate)
          _ <- store.dispatch(Command.NextChunk)
        yield ()
      case None =>
        store.dispatch(Command.Stop).void