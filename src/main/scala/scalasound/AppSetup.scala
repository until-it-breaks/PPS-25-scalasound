package scalasound

import cats.effect.{IO, Resource}
import cats.effect.unsafe.IORuntime
import scalasound.controller.{EffectsController, LoadSaveController, PlaybackControllerImpl}
import scalasound.model.io.{WavAudioReader, WavAudioWriter}
import scalasound.model.player.{AudioOutput, AudioPlayer}
import scalasound.model.processing.AudioProcessorWithRules
import scalasound.model.state.AppStateStore
import scalasound.view.AppDependencies
import scalasound.view.loadsave.{LoadSaveControlsImpl, LoadSaveObserverImpl}
import scalasound.view.panels.effects.EffectsLogic
import scalasound.view.panels.playback.TrackBoxLogic

object AppSetup:
  def make(ready: IO[Unit])(using IORuntime): Resource[IO, AppDependencies] =
    for
      store              <- Resource.eval(AppStateStore.make)
      loadSaveController <- LoadSaveController.make(store, WavAudioReader, WavAudioWriter)
      loadSaveControls = LoadSaveControlsImpl(loadSaveController)
      trackBoxLogic    = TrackBoxLogic(store, PlaybackControllerImpl(store))
      effectsLogic     = EffectsLogic(EffectsController(store))
      _ <- startPlayer(store)
      _ <- (ready >> LoadSaveObserverImpl(store, loadSaveControls).observe).background
      _ <- (ready >> trackBoxLogic.observe).background
    yield AppDependencies(effectsLogic, loadSaveController, loadSaveControls, trackBoxLogic)

  private def startPlayer(store: AppStateStore): Resource[IO, Unit] =
    AudioPlayer
      .streaming(AudioProcessorWithRules, AudioOutput.speakers)
      .run(store)
      .compile
      .drain
      .background
      .map(_ => ())
