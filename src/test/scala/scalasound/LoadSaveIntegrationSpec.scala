package scalasound

import cats.effect.unsafe.IORuntime
import cats.effect.{IO, Resource}
import munit.CatsEffectSuite
import scalafx.scene.control.Alert.AlertType
import scalasound.controller.{EffectsController, LoadSaveController}
import scalasound.model.io.AudioFormatRegistry
import scalasound.model.io.wav.{WavAudioFormat, WavAudioReader, WavAudioWriter}
import scalasound.model.processing.{AudioEffect, AudioProcessor}
import scalasound.model.state.{AppStateStore, ExportState, ImportState}
import scalasound.view.panels.loadsave.LoadSaveLogic

import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.*

class LoadSaveIntegrationSpec extends CatsEffectSuite:

  private val tempDir: Resource[IO, Path] =
    Resource.make(IO.blocking(Files.createTempDirectory("scalasound-integration"))): dir =>
      IO.blocking:
        Option(dir.toFile.listFiles()).foreach(_.foreach(_.delete()))
        Files.delete(dir)

  private given IORuntime = munitIORuntime

  private def application(
      recorder: AlertRecorder
  ): Resource[IO, (AppStateStore, LoadSaveController, EffectsController)] =
    for
      store              <- Resource.eval(AppStateStore.make)
      formats            <- Resource.eval(orFail(AudioFormatRegistry.of(WavAudioFormat.fileType)))
      loadSaveController <- LoadSaveController.make(store, formats)
      effectsController = EffectsController(store)
      logic             = LoadSaveLogic(
        store = store,
        controller = loadSaveController,
        runOnGUIThread = update => update,
        showAlert = recorder.show
      )
      _ <- logic.observe.background
    yield (store, loadSaveController, effectsController)

  private def orFail[E, A](result: Either[E, A]): IO[A] =
    IO.fromEither(result.left.map(error => AssertionError(error.toString)))

  private def waitUntil(condition: IO[Boolean]): IO[Unit] =
    def loop: IO[Unit] =
      condition.flatMap:
        case true  => IO.unit
        case false => IO.cede >> loop

    loop.timeout(5.seconds)

  private final case class RecordedAlert(alertType: AlertType, title: String, message: String)

  private final class AlertRecorder:
    val alerts = AtomicReference(Vector.empty[RecordedAlert])

    def show(alertType: AlertType, title: String, message: String): Unit =
      alerts.updateAndGet(_ :+ RecordedAlert(alertType, title, message))
      ()

  test(
    "a WAV file is imported and the track is exported back with LoadSaveLogic notifying and acknowledging each outcome"
  ):
    tempDir.use: dir =>
      val input = dir.resolve("input.wav")
      val track = WavAudioReader
        .load(Paths.get("src/test/resources/file_example_WAV_2MG.wav"))
        .unsafeRunSync()
        .getOrElse(fail("Failed to load the test WAV file."))
      val recorder = AlertRecorder()

      application(recorder).use: (store, loadSaveController, _) =>
        for
          _ <- WavAudioWriter.save(track, input).flatMap(orFail(_))
          _ <- loadSaveController.load(input)
          _ <- waitUntil(
            store.current.map(state =>
              state.track.contains(track) && state.importState == ImportState.Idle
            )
          )
          _ <- loadSaveController.save(dir.resolve("output"))
          _ <- waitUntil(
            store.current
              .map(_.exportState == ExportState.Idle)
              .map(_ && recorder.alerts.get.exists(_.title == "Export completed"))
          )
          exported <- WavAudioReader
            .load(dir.resolve("output.wav"))
            .flatMap(orFail(_))
        yield
          assertEquals(
            recorder.alerts.get,
            Vector(
              RecordedAlert(
                AlertType.Information,
                "Import completed",
                "The track has been imported."
              ),
              RecordedAlert(
                AlertType.Information,
                "Export completed",
                "The track has been exported."
              )
            )
          )
          assertEquals(exported, track)

  test(
    "active effects are applied to the track before exporting without modifying the loaded track"
  ):
    tempDir.use: dir =>
      val input = dir.resolve("input.wav")
      val track = WavAudioReader
        .load(Paths.get("src/test/resources/file_example_WAV_2MG.wav"))
        .unsafeRunSync()
        .getOrElse(fail("Failed to load the test WAV file."))
      val recorder = AlertRecorder()

      val activeEffect  = AudioEffect.gain(0.0f)
      val expectedTrack = AudioProcessor.withRules
        .apply(track, List(activeEffect))
        .getOrElse(fail("Failed to apply effect in test setup."))

      application(recorder).use: (store, loadSaveController, effectsController) =>
        for
          _ <- WavAudioWriter.save(track, input).flatMap(orFail(_))
          _ <- loadSaveController.load(input)
          _ <- waitUntil(
            store.current.map(state =>
              state.track.contains(track) && state.importState == ImportState.Idle
            )
          )
          _ <- effectsController.addEffect(activeEffect)
          _ <- loadSaveController.save(dir.resolve("processed_output"))
          _ <- waitUntil(
            store.current
              .map(_.exportState == ExportState.Idle)
              .map(_ && recorder.alerts.get.exists(_.title == "Export completed"))
          )
          exported <- WavAudioReader
            .load(dir.resolve("processed_output.wav"))
            .flatMap(orFail(_))
          current <- store.current
        yield
          assertNotEquals(exported, track)
          assertEquals(exported, expectedTrack)
          assertEquals(current.track, Some(track))

  test(
    "failures trigger error alerts in LoadSaveLogic and are automatically acknowledged back to Idle"
  ):
    tempDir.use: dir =>
      val missingFile = dir.resolve("missing.wav")
      val recorder    = AlertRecorder()

      application(recorder).use: (store, loadSaveController, _) =>
        for
          _ <- loadSaveController.load(missingFile)
          _ <- waitUntil(
            store.current
              .map(_.importState == ImportState.Idle)
              .map(_ && recorder.alerts.get.exists(_.title == "Import failed"))
          )
          _ <- loadSaveController.save(dir.resolve("out.wav"))
          _ <- waitUntil(
            store.current
              .map(_.exportState == ExportState.Idle)
              .map(_ && recorder.alerts.get.exists(_.title == "Export failed"))
          )
          current <- store.current
        yield
          assert(current.track.isEmpty)
          assertEquals(
            recorder.alerts.get,
            Vector(
              RecordedAlert(
                AlertType.Error,
                "Import failed",
                s"The file was not found: $missingFile"
              ),
              RecordedAlert(
                AlertType.Error,
                "Export failed",
                "There is no track to export. Import a track first."
              )
            )
          )

  test(
    "an extension that is not supported is reported as an import error without opening the file"
  ):
    tempDir.use: dir =>
      val recorder = AlertRecorder()

      application(recorder).use: (store, loadSaveController, _) =>
        for
          unsupported <- IO.blocking(Files.write(dir.resolve("song.mp3"), Array[Byte](1, 2, 3)))
          _           <- loadSaveController.load(unsupported)
          _           <- waitUntil(
            store.current
              .map(_.importState == ImportState.Idle)
              .map(_ && recorder.alerts.get.exists(_.title == "Import failed"))
          )
        yield assertEquals(
          recorder.alerts.get,
          Vector(
            RecordedAlert(
              AlertType.Error,
              "Import failed",
              "Files with the extension .mp3 are not supported"
            )
          )
        )
