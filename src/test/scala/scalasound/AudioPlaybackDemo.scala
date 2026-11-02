package scalasound

import cats.effect.{IO, IOApp}
import scalasound.io.WavAudioReader
import scalasound.player.{AudioPlayer, SpeakerAudioOutput}
import scalasound.processing.SimpleAudioProcessor
import scalasound.state.{AppStateStore, Command, ImportState, PlaybackState}

import java.nio.file.Paths
import scala.concurrent.duration.DurationInt

object AudioPlaybackDemo extends IOApp.Simple:

  val run: IO[Unit] =
    for
      store <- AppStateStore.make
      path = Paths.get(getClass.getResource("/file_example_WAV_5MG.wav").toURI).toString
      track <- IO.blocking(WavAudioReader.load(path))
        .map(_.left.map(e => new RuntimeException(e.toString)))
        .rethrow

      player = AudioPlayer.streaming(SimpleAudioProcessor, SpeakerAudioOutput())
      _ <- player.run(store)
        .compile
        .drain
        .background
        .use { _ =>
          for
            // Tell the store a track was loaded.
            _ <- store.dispatch(Command.UpdateImport(ImportState.Imported(track)))
            // Tell the store to play. The player sees Playing and starts writing chunks.
            _ <- store.dispatch(Command.Play)
            _ <- IO.sleep(5.seconds)
            _ <- store.dispatch(Command.Pause)
            _ <- printPosition(store, "Paused")
            _ <- IO.sleep(3.seconds)
            _ <- printPosition(store, "Resuming")
            _ <- store.dispatch(Command.Play)
            _ <- IO.sleep(2.seconds)
            _ <- printPosition(store, "Before seek")
            _ <- store.dispatch(Command.SeekToSeconds(3.0))
            _ <- printPosition(store, "After seek")
            _ <- store.playbackChanges
              .dropWhile(_ != PlaybackState.Playing)
              .filter(_ == PlaybackState.Stopped)
              .take(1)
              .compile
              .drain
          yield ()
        }
    yield ()

  private def printPosition(store: AppStateStore, label: String): IO[Unit] =
    store.current.flatMap { s =>
      IO.println(f"$label: chunk ${s.chunkIndex}, ${s.currentPositionSeconds}%.2f s")
    }