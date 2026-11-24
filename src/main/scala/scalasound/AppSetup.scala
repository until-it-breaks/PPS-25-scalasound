package scalasound

import cats.effect.{IO, Resource}
import cats.effect.unsafe.IORuntime
import scalasound.controller.{EffectsController, LoadSaveController, PlaybackControllerImpl}
import scalasound.model.io.{AudioFormatRegistry, WavAudioFormat}
import scalasound.model.player.{AudioOutput, AudioPlayer}
import scalasound.model.processing.AudioProcessorWithRules
import scalasound.model.state.AppStateStore
import scalasound.view.AppDependencies
import scalasound.view.panels.effects.{EffectsControllerProxy, EffectsLogic}
import scalasound.view.panels.loadsave.LoadSaveLogic
import scalasound.view.panels.playback.TrackBoxLogic

object AppSetup:
  def make(ready: IO[Unit])(using IORuntime): Resource[IO, AppDependencies] =
    for
      store              <- Resource.eval(AppStateStore.make)
      formats            <- Resource.eval(supportedFormats)
      loadSaveController <- LoadSaveController.make(store, formats)
      loadSaveLogic      = LoadSaveLogic(store, loadSaveController)
      trackBoxLogic      = TrackBoxLogic(store, PlaybackControllerImpl(store))
      effectsProxy       = EffectsControllerProxy(EffectsController(store))
      effectsLogic       = EffectsLogic(effectsProxy)
      _ <- startPlayer(store)
      _ <- (ready >> loadSaveLogic.observe).background
      _ <- (ready >> trackBoxLogic.observe).background
    yield AppDependencies(effectsLogic, loadSaveLogic, trackBoxLogic)

  private def supportedFormats: IO[AudioFormatRegistry] =
    IO.fromEither(
      AudioFormatRegistry
        .of(WavAudioFormat.fileType)
        .left
        .map(error => IllegalStateException(s"Invalid audio format configuration: $error"))
    )

  private def startPlayer(store: AppStateStore): Resource[IO, Unit] =
    AudioPlayer
      .streaming(AudioProcessorWithRules, AudioOutput.speakers)
      .run(store)
      .compile
      .drain
      .background
      .map(_ => ())
