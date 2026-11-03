package scalasound

import cats.effect.{IO, IOApp}
import scalasound.model.io.WavAudioReader
import scalasound.model.player.{AudioOutput, AudioPlayer}
import scalasound.model.processing.SimpleAudioProcessor
import scalasound.model.state.{AppStateStore, Command, ImportState, PlaybackState}

import java.nio.file.Paths
import scala.concurrent.duration.DurationInt

object AudioPlaybackDemo extends IOApp.Simple:

  val run: IO[Unit] =
    for
      _ <- IO.println("[DEMO] Initializing app state store...")
      store <- AppStateStore.make

      _ <- IO.println("[DEMO] Loading sample WAV file from resources...")
      path = Paths.get(getClass.getResource("/file_example_WAV_5MG.wav").toURI).toString
      track <- IO.blocking(WavAudioReader.load(path))
        .map(_.left.map(e => new RuntimeException(e.toString)))
        .rethrow

      _ <- IO.println(s"[DEMO] Track loaded successfully!")
      _ <- IO.println(s"       - Sample Rate: ${track.sampleRate} Hz")
      _ <- IO.println(f"       - Duration: ${track.durationInSeconds}%.2f seconds")
      _ <- IO.println(s"       - Total Frames: ${track.frameCount}")
      _ <- IO.println(s"       - Total Chunks: ${track.chunks.length}")
      _ <- IO.println("[DEMO] Starting streaming audio player in the background...")

      // Can swap to AudioOutput.blockingConsole
      player = AudioPlayer.streaming(SimpleAudioProcessor, AudioOutput.speakers)
      _ <- player.run(store)
        .compile
        .drain
        .background
        .use { _ =>
          for
            _ <- IO.println("[DEMO] -> Dispatching Command.UpdateImport (Track Loaded)")
            _ <- store.dispatch(Command.UpdateImport(ImportState.Imported(track)))

            _ <- IO.println("[DEMO] -> Dispatching Command.Play")
            _ <- store.dispatch(Command.Play)

            _ <- IO.sleep(5.seconds)

            _ <- IO.println("[DEMO] -> Dispatching Command.Pause")
            _ <- store.dispatch(Command.Pause)
            _ <- printPosition(store, "Paused State")

            _ <- IO.sleep(3.seconds)

            _ <- IO.println("[DEMO] -> Resuming (Dispatching Command.Play)")
            _ <- store.dispatch(Command.Play)

            _ <- IO.sleep(2.seconds)

            _ <- printPosition(store, "Before Seek")

            _ <- IO.println("[DEMO] -> Dispatching Command.SeekToIndex(150)")
            _ <- store.dispatch(Command.SeekToIndex(150))

            _ <- printPosition(store, "After Seek")

            _ <- IO.println("[DEMO] -> Waiting for track playback to finish...")
            _ <- store.playbackChanges
              .dropWhile(_ != PlaybackState.Playing)
              .filter(_ == PlaybackState.Stopped)
              .take(1)
              .compile
              .drain

            _ <- IO.println("[DEMO] Playback complete. Shutting down demo.")
          yield ()
        }
    yield ()

  private def printPosition(store: AppStateStore, label: String): IO[Unit] =
    store.current.flatMap { s =>
      IO.println(f"[$label] -> chunk ${s.chunkIndex}, ${s.currentPositionSeconds}%.2f s")
    }