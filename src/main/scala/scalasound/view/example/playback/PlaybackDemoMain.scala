package scalasound.view.example.playback

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import scalasound.controller.{PlaybackController, PlaybackControllerImpl}
import scalasound.model.io.WavAudioReader
import scalasound.model.player.{AudioOutput, AudioPlayer}
import scalasound.model.processing.SimpleAudioProcessor
import scalasound.model.state.{AppStateStore, Command, ImportState}

import java.nio.file.Paths

/**
 * Entry point for the playback demo.
 *
 * Creates the [[AppStateStore]], loads an example track into it, starts the audio player
 * in the background, and then launches the JavaFX GUI with the playback controls.
 *
 * The track is preloaded only because the demo assumes one is already loaded. In the real
 * application the store would start empty and the user would import a track through the UI.
 */
object PlaybackDemoMain:

  given IORuntime = IORuntime.global

  def main(args: Array[String]): Unit =
    val (store, controller) = setup.unsafeRunSync()
    new PlaybackDemo(store, controller).main(args)

  private def setup: IO[(AppStateStore, PlaybackController)] =
    for
      store <- AppStateStore.make
      _ <- loadExampleTrack(store)
      _ <- startPlayer(store)
    yield (store, PlaybackControllerImpl(store))

  // DEMO ONLY: the real app would not load a track here. The user would
  // import one through the UI, and that action would dispatch the command.
  // It is here because the playback demo assumes a track is already loaded.
  private def loadExampleTrack(store: AppStateStore): IO[Unit] =
    for
      path <- IO(Paths.get(getClass.getResource("/example.wav").toURI).toString)
      track <- IO.blocking(WavAudioReader.load(path))
        .map(_.left.map(e => new RuntimeException(e.toString)))
        .rethrow
      _ <- store.dispatch(Command.UpdateImport(ImportState.Imported(track)))
    yield ()

  private def startPlayer(store: AppStateStore): IO[Unit] =
    AudioPlayer.streaming(SimpleAudioProcessor, AudioOutput.speakers)
      .run(store)
      .compile
      .drain
      .start
      .void