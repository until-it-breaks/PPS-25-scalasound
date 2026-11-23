package scalasound.model.player

import cats.effect.IO
import cats.kernel.Eq
import fs2.Stream
import scalasound.model.domain.AudioChunk
import scalasound.model.processing.AudioProcessor
import scalasound.model.state.{AppState, AppStateStore, Command, PlaybackState}

/** An [[AudioPlayer]] that processes and plays audio in real time, one chunk at a time.
 *
 * Each chunk is processed with the current effects just before it is played, so effect changes are
 * heard on the next chunk and nothing is processed in advance.
  *
  * @param processor
  *   the processor used to apply effects to each [[AudioChunk]]
  * @param out
  *   where the processed chunks are written (e.g. the speakers)
  */
private final class StreamingAudioPlayer(processor: AudioProcessor, out: AudioOutput)
    extends AudioPlayer:

  private given Eq[(PlaybackState, Int)] = Eq.fromUniversalEquals

  def run(store: AppStateStore): Stream[IO, Unit] =
    store.changes
      .changesBy(state => (state.playbackState, state.chunkIndex))
      .switchMap { state =>
        state.playbackState match
          case PlaybackState.Playing =>
            Stream.eval(playChunk(state, store).handleErrorWith(stopAfterFailure(store)))
          case _ => Stream.empty
      }

  /** Prints the failure and stops playback; the player itself is kept alive. */
  private def stopAfterFailure(store: AppStateStore)(error: Throwable): IO[Unit] =
    for
      _ <- IO.println(s"Audio player failed: $error")
      _ <- store.dispatch(Command.Stop)
    yield ()

  /** Plays the chunk at the play head, then advances the play head.
    *
    * If there is nothing to play (no track, or the play head is past the last chunk), it dispatches
    * `Stop` instead.
    *
    * @param state
    *   the state that triggered this write; its `chunkIndex` picks the chunk
    * @param store
    *   the store to dispatch `NextChunk` or `Stop` to
    */
  private def playChunk(state: AppState, store: AppStateStore): IO[Unit] =
    val current =
      for
        track <- state.track
        chunk <- track.chunks.lift(state.chunkIndex)
      yield (track, chunk)
    current match
      case Some((track, chunk)) =>
        for
          processed <- process(chunk, state)
          _         <- out.write(processed, track.sampleRate.value)
          _         <- store.dispatch(Command.NextChunk)
        yield ()
      case None =>
        store.dispatch(Command.Stop).void

  /** Applies the effects to the chunk. If the processor reports an [[AudioError]], prints it and
    * falls back to the unprocessed chunk, so playback continues.
    */
  private def process(chunk: AudioChunk, state: AppState): IO[AudioChunk] =
    for
      result    <- IO(processor.apply(chunk, state.effects, state.chunkIndex))
      processed <- result match
        case Right(value) => IO.pure(value)
        case Left(error)  =>
          IO.println(s"Audio processing failed at chunk ${state.chunkIndex}: $error").as(chunk)
    yield processed
