package scalasound

import cats.effect.unsafe.IORuntime
import cats.effect.{Deferred, IO, IOApp, Resource}
import scalasound.controller.{EffectsController, EffectsControllerImpl, LoadSaveController, PlaybackControllerImpl}
import scalasound.model.io.{WavAudioReader, WavAudioWriter}
import scalasound.model.state.AppStateStore
import scalasound.view.ViewApp
import scalasound.view.example.playback.{PlaybackControls, PlaybackControlsObserver}
import scalasound.view.loadsave.{LoadSaveControls, LoadSaveControlsImpl, LoadSaveObserverImpl}
import scalasound.view.mainPanel.effectPanels.{EffectsControls, EffectsControlsImpl}

object Main extends IOApp.Simple:

  override def run: IO[Unit] =
    given IORuntime = runtime

    application.use { (effectsControls, loadSaveController, loadSaveControls, ready) =>
      IO.blocking {
        new ViewApp(
          effectsControls,
          loadSaveController,
          loadSaveControls,
          onReady = () => ready.complete(()).void.unsafeRunAndForget()
        ).main(Array.empty[String])
      }
    }

  private def application(using IORuntime): Resource[IO, (EffectsControls, LoadSaveController, LoadSaveControls, Deferred[IO, Unit])] =
    for
      ready               <- Resource.eval(Deferred[IO, Unit])
      store               <- Resource.eval(AppStateStore.make)
      loadSaveController  <- LoadSaveController.make(store, WavAudioReader, WavAudioWriter)
      loadSaveControls     = LoadSaveControlsImpl(loadSaveController)
      loadSaveObserver     = LoadSaveObserverImpl(store, loadSaveControls)
      _                   <- (ready.get >> loadSaveObserver.observe).background
      effectsController    = EffectsControllerImpl(store)
      effectsControls      = EffectsControlsImpl(effectsController)
    yield (effectsControls, loadSaveController, loadSaveControls, ready)