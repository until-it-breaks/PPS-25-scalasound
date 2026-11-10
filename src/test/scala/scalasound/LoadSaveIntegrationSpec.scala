package scalasound

import cats.effect.{IO, Resource}
import munit.CatsEffectSuite
import scalasound.controller.LoadSaveController
import scalasound.model.domain.AudioTrack
import scalasound.model.io.{AudioLoadError, AudioSaveError, WavAudioReader, WavAudioWriter}
import scalasound.model.state.AppStateStore
import scalasound.view.loadsave.{LoadSaveControls, LoadSaveObserverImpl}

import java.nio.file.{Files, Path}
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.*

class LoadSaveIntegrationSpec extends CatsEffectSuite:

  private final class RecordingView extends LoadSaveControls:
    val events = AtomicReference(Vector.empty[String])
    val importedTracks = AtomicReference(Vector.empty[AudioTrack])

    private def record(event: String): Unit =
      events.updateAndGet(_ :+ event)
      ()

    def importIdle(): Unit = record("import:idle")
    def importStarted(): Unit = record("import:started")
    def importCompleted(track: AudioTrack): Unit =
      importedTracks.updateAndGet(_ :+ track)
      record("import:completed")
    def importFailed(error: AudioLoadError): Unit = record(s"import:failed:$error")
    def exportIdle(): Unit = record("export:idle")
    def exportStarted(): Unit = record("export:started")
    def exportCompleted(): Unit = record("export:completed")
    def exportFailed(error: AudioSaveError): Unit = record(s"export:failed:$error")

  private val tempDir: Resource[IO, Path] =
    Resource.make(IO.blocking(Files.createTempDirectory("scalasound-integration"))) { dir =>
      IO.blocking {
        dir.toFile.listFiles().foreach(_.delete())
        Files.delete(dir)
      }
    }

  /** The controller and the observer, working on a new store; the observer updates the given view synchronously. */
  private def application(view: RecordingView): Resource[IO, LoadSaveController] = {
    for
      store <- Resource.eval(AppStateStore.make)
      controller <- LoadSaveController.make(store, WavAudioReader, WavAudioWriter)
      _ <- LoadSaveObserverImpl(store, view, update => update).observe.background
      _ <- Resource.eval(waitUntil(view.events.get.contains("import:idle") && view.events.get.contains("export:idle")))
    yield controller
  }

  private def waitUntil(condition: => Boolean): IO[Unit] =
    def loop: IO[Unit] =
      IO(condition).flatMap {
        case true => IO.unit
        case false => IO.cede >> loop
      }
    loop.timeout(5.seconds)

  private def orFail[E, A](result: Either[E, A]): IO[A] =
    IO.fromEither(result.left.map(error => AssertionError(error.toString)))

  test("a WAV file is imported and the track is exported back with the view following each outcome") {
    tempDir.use { dir =>
      val input = dir.resolve("input.wav")
      val track = WavAudioReader.load("src/test/resources/file_example_WAV_2MG.wav")
        .getOrElse(fail("Failed to load the test WAV file."))
      val view = RecordingView()
      application(view).use { controller =>
        for
          _ <- IO.blocking(WavAudioWriter.save(track, input)).flatMap(orFail(_))
          _ <- IO.blocking(controller.load(input))
          _ <- waitUntil(view.importedTracks.get.nonEmpty)
          _ <- IO.blocking(controller.save(dir.resolve("output")))
          _ <- waitUntil(view.events.get.contains("export:completed"))
          _ <- IO.blocking(controller.acknowledgeExport)
          _ <- waitUntil(view.events.get.count(_ == "export:idle") >= 2)
          exported <- IO.blocking(WavAudioReader.load(dir.resolve("output.wav").toString)).flatMap(orFail(_))
        yield
          assertEquals(view.importedTracks.get, Vector[AudioTrack](track))
          assertEquals(exported, track)
      }
    }
  }

  test("failures reach the view and are cleared once acknowledged") {
    tempDir.use { dir =>
      val view = RecordingView()
      application(view).use { controller =>
        for
          _ <- IO.blocking(controller.load(dir.resolve("missing.wav")))
          _ <- waitUntil(view.events.get.exists(_.startsWith("import:failed:")))
          _ <- IO.blocking(controller.acknowledgeImport)
          _ <- waitUntil(view.events.get.count(_ == "import:idle") >= 2)
          _ <- IO.blocking(controller.save(dir.resolve("out.wav")))
          _ <- waitUntil(view.events.get.contains("export:failed:NoTrackLoaded"))
          _ <- IO.blocking(controller.acknowledgeExport)
          _ <- waitUntil(view.events.get.count(_ == "export:idle") >= 2)
        yield assert(view.importedTracks.get.isEmpty)
      }
    }
  }
