package scalasound

import cats.effect.{IO, IOApp}
import scalasound.model.io.WavAudioReader
import scalasound.model.player.{AudioOutput, AudioPlayer}
import scalasound.model.processing.SimpleAudioProcessor
import scalasound.model.state.{AppStateStore, Command, ImportState, PlaybackState}

import scala.concurrent.duration.{Duration, DurationInt, SECONDS}

object AudioPlaybackDemo extends IOApp.Simple:

  val run: IO[Unit] =
    for
      _     <- IO.println("[DEMO] Initializing app state store...")
      store <- AppStateStore.make

      _ <- IO.println("[DEMO] Loading sample WAV file from resources...")
      path = "src/test/resources/file_example_WAV_5MG.wav"
      track <- IO
        .blocking(WavAudioReader.load(path))
        .map(_.left.map(e => new RuntimeException(e.toString)))
        .rethrow

      _ <- IO.println(s"[DEMO] Track loaded successfully!")
      _ <- IO.println(s"       - Sample Rate: ${track.sampleRate} Hz")
      _ <- IO.println(f"       - Duration: ${track.duration.toUnit(SECONDS)}%.2f seconds")
      _ <- IO.println(s"       - Total Frames: ${track.frameCount}")
      _ <- IO.println(s"       - Total Chunks: ${track.chunks.length}")
      _ <- IO.println("[DEMO] Starting streaming audio player in the background...")

      // Pick one output (a Resource, so the sound line is closed when the demo ends):
      //   AudioOutput.speakers                      real playback; the sound card paces the stream
      //   Resource.pure(AudioOutput.console)        prints chunk info, no pacing (runs through the file instantly)
      //   Resource.pure(AudioOutput.console.paced)  prints chunk info at real-time speed (headless demo)
      //   AudioOutput.speakers.map(_.paced)         works, but not recommended: the line already blocks, so you'd double the delay
      output = AudioOutput.speakers
      _ <- output
        .flatMap(out =>
          AudioPlayer.streaming(SimpleAudioProcessor, out).run(store).compile.drain.background
        )
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
            _ <- store.playbackStateChanges
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
    for
      state <- store.current
      position = state.track match
        case None        => Duration.Zero
        case Some(track) => track.positionAt(state.chunkIndex)
      _ <- IO.println(f"[$label] -> chunk ${state.chunkIndex}, ${position.toUnit(SECONDS)}%.2f s")
    yield ()
