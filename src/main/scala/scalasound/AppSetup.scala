package scalasound

import cats.effect.unsafe.IORuntime
import cats.effect.{IO, Resource}
import scalasound.controller.{EffectsController, LoadSaveController, PlaybackController}
import scalasound.model.io.{AudioFormatRegistry, WavAudioFormat}
import scalasound.model.player.{AudioOutput, AudioPlayer}
import scalasound.model.processing.AudioProcessor
import scalasound.model.state.AppStateStore
import scalasound.view.AppDependencies
import scalasound.view.panels.effects.{EffectsControllerWrapper, EffectsLogic}
import scalasound.view.panels.loadsave.LoadSaveLogic
import scalasound.view.panels.playback.TrackBoxLogic

object AppSetup:
  def make(ready: IO[Unit])(using IORuntime): Resource[IO, AppDependencies] =
    for
      store              <- Resource.eval(AppStateStore.make)
      formats            <- Resource.eval(supportedFormats)
      loadSaveController <- LoadSaveController.make(store, formats)
      loadSaveLogic = LoadSaveLogic(store, loadSaveController)
      trackBoxLogic = TrackBoxLogic(store, PlaybackController(store))
      effectsLogic  = EffectsLogic(EffectsControllerWrapper(EffectsController(store)))
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
    for
      out <- AudioOutput.speakers
      _   <- AudioPlayer
        .streaming(AudioProcessor.withRules, out)
        .run(store)
        .compile
        .drain
        .background
    yield ()
