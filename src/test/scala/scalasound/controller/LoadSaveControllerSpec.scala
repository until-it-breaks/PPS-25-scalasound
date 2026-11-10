package scalasound.controller

import cats.effect.std.Dispatcher
import cats.effect.{Deferred, IO}
import munit.CatsEffectSuite
import scalasound.model.domain.AudioChunk.MonoChunk
import scalasound.model.domain.{AudioTrack, MonoTrack}
import scalasound.model.io.{AudioLoadError, AudioReader, AudioSaveError, AudioWriter}
import scalasound.model.state.{AppStateStore, Command, ExportState, ImportState}

import java.nio.file.{Path, Paths}
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.*

class LoadSaveControllerSpec extends CatsEffectSuite:

  private val trackA = MonoTrack(44100, Vector(MonoChunk(Vector(0.5f))))
  private val trackB = MonoTrack(48000, Vector(MonoChunk(Vector(-0.25f))))
  private val inputPath = Paths.get("in.wav")
  private val outputPath = Paths.get("out.wav")

  private def succeedingReader = FakeReader(_ => Right(trackA))

  private def succeedingWriter = FakeWriter((_, _) => Right(()))

  private def withController(reader: AudioReader, writer: AudioWriter)
                            (test: (AppStateStore, LoadSaveController) => IO[Unit]): IO[Unit] =
    AppStateStore.make.flatMap { store =>
      LoadSaveController.make(store, reader, writer).use(controller => test(store, controller))
    }

  private def awaitImport(store: AppStateStore)(predicate: ImportState => Boolean): IO[ImportState] =
    store.changes
      .map(_.importState)
      .filter(predicate)
      .take(1)
      .compile
      .lastOrError
      .timeout(5.seconds)

  private def awaitExport(store: AppStateStore)(predicate: ExportState => Boolean): IO[ExportState] =
    store.changes
      .map(_.exportState)
      .filter(predicate)
      .take(1)
      .compile
      .lastOrError
      .timeout(5.seconds)

  private def isFinalImport(state: ImportState): Boolean = state match
    case ImportState.Imported(_) | ImportState.Failed(_) => true
    case _ => false

  private def isFinalExport(state: ExportState): Boolean = state match
    case ExportState.Exported | ExportState.Failed(_) => true
    case _ => false

  private def retryUntilSuccess(attempt: IO[Boolean]): IO[Unit] =
    attempt.flatMap {
      case true => IO.unit
      case false => IO.cede >> retryUntilSuccess(attempt)
    }

  private final class FakeReader(behaviour: String => Either[AudioLoadError, AudioTrack]) extends AudioReader:
    val loaded = AtomicReference(Vector.empty[String])

    override def load(filePath: String): Either[AudioLoadError, AudioTrack] =
      loaded.updateAndGet(_ :+ filePath)
      behaviour(filePath)

  private final class FakeWriter(behaviour: (AudioTrack, Path) => Either[AudioSaveError, Unit]) extends AudioWriter:
    val saved = AtomicReference(Vector.empty[(AudioTrack, Path)])

    override def save(track: AudioTrack, path: Path): Either[AudioSaveError, Unit] =
      saved.updateAndGet(_ :+ (track -> path))
      behaviour(track, path)

  test("load imports the track and ends in state Imported") {
    val reader = succeedingReader
    withController(reader, succeedingWriter) { (store, controller) =>
      for
        fiber <- awaitImport(store)(isFinalImport).start
        _ <- IO.blocking(controller.load(inputPath))
        state <- fiber.joinWithNever
        current <- store.current
      yield
        assertEquals(state, ImportState.Imported(trackA))
        assertEquals(current.track, Some(trackA))
        assertEquals(reader.loaded.get, Vector("in.wav"))
    }
  }

  test("load ends in state Failed with the error returned by the reader") {
    val error = AudioLoadError.UnsupportedFormat("not a wav")
    withController(FakeReader(_ => Left(error)), succeedingWriter) { (store, controller) =>
      for
        fiber <- awaitImport(store)(isFinalImport).start
        _ <- IO.blocking(controller.load(inputPath))
        state <- fiber.joinWithNever
        current <- store.current
      yield
        assertEquals(state, ImportState.Failed(error))
        assertEquals(current.track, None)
    }
  }

  test("load ends in state Failed when the reader throws an unexpected exception") {
    withController(FakeReader(_ => throw RuntimeException("error")), succeedingWriter) { (store, controller) =>
      for
        fiber <- awaitImport(store)(isFinalImport).start
        _ <- IO.blocking(controller.load(inputPath))
        state <- fiber.joinWithNever
      yield assertEquals(state, ImportState.Failed(AudioLoadError.ReadFailure("error")))
    }
  }

  test("load reports the state Importing while the reader is working") {
    Dispatcher.parallel[IO].use { dispatcher =>
      for
        importGate <- Deferred[IO, Unit]
        reader = FakeReader { _ =>
          dispatcher.unsafeRunSync(importGate.get)
          Right(trackA)
        }
        _ <- withController(reader, succeedingWriter) { (store, controller) =>
          for
            importingFiber <- awaitImport(store)(_ == ImportState.Importing).start
            doneFiber <- awaitImport(store)(isFinalImport).start
            _ <- IO.blocking(controller.load(inputPath))
            importing <- importingFiber.joinWithNever
            _ <- importGate.complete(()).void
            done <- doneFiber.joinWithNever
          yield
            assertEquals(importing, ImportState.Importing)
            assertEquals(done, ImportState.Imported(trackA))
        }
      yield ()
    }
  }

  test("load is accepted and runs while an export is currently in progress") {
    Dispatcher.parallel[IO].use { dispatcher =>
      for
        exportGate <- Deferred[IO, Unit]
        writer = FakeWriter { (_, _) =>
          dispatcher.unsafeRunSync(exportGate.get)
          Right(())
        }
        reader = succeedingReader
        _ <- withController(reader, writer) { (store, controller) =>
          for
            _ <- store.dispatch(Command.UpdateImport(ImportState.Imported(trackA)))
            exportFiber <- awaitExport(store)(_ == ExportState.Exporting).start
            _ <- IO.blocking(controller.save(outputPath))
            _ <- exportFiber.joinWithNever
            importFiber <- awaitImport(store)(isFinalImport).start
            _ <- IO.blocking(controller.load(inputPath))
            importState <- importFiber.joinWithNever
            _ <- exportGate.complete(()).void
          yield assertEquals(importState, ImportState.Imported(trackA))
        }
      yield ()
    }
  }

  test("a second load is ignored while the first one is still running") {
    Dispatcher.parallel[IO].use { dispatcher =>
      for
        importGate <- Deferred[IO, Unit]
        reader = FakeReader { _ =>
          dispatcher.unsafeRunSync(importGate.get)
          Right(trackA)
        }
        _ <- withController(reader, succeedingWriter) { (store, controller) =>
          for
            importingFiber <- awaitImport(store)(_ == ImportState.Importing).start
            doneFiber <- awaitImport(store)(isFinalImport).start
            _ <- IO.blocking(controller.load(inputPath))
            _ <- importingFiber.joinWithNever
            _ <- IO.blocking(controller.load(Paths.get("other.wav")))
            _ <- importGate.complete(()).void
            _ <- doneFiber.joinWithNever
          yield assertEquals(reader.loaded.get, Vector("in.wav"))
        }
      yield ()
    }
  }

  test("a new load is accepted once the previous one has completed") {
    val reader = succeedingReader
    withController(reader, succeedingWriter) { (store, controller) =>
      for
        firstFiber <- awaitImport(store)(isFinalImport).start
        _ <- IO.blocking(controller.load(inputPath))
        _ <- firstFiber.joinWithNever
        _ <- retryUntilSuccess(IO.blocking(
          controller.load(inputPath)) >> IO(reader.loaded.get.size >= 2)
        ).timeout(5.seconds)
      yield ()
    }
  }

  test("save exports the current track and ends in state Exported") {
    val writer = succeedingWriter
    withController(succeedingReader, writer) { (store, controller) =>
      for
        _ <- store.dispatch(Command.UpdateImport(ImportState.Imported(trackA)))
        fiber <- awaitExport(store)(isFinalExport).start
        _ <- IO.blocking(controller.save(outputPath))
        state <- fiber.joinWithNever
      yield
        assertEquals(state, ExportState.Exported)
        assertEquals(writer.saved.get, Vector[(AudioTrack, Path)](trackA -> outputPath))
    }
  }

  test("save appends the '.wav' extension when it is missing") {
    val writer = succeedingWriter
    withController(succeedingReader, writer) { (store, controller) =>
      for
        _ <- store.dispatch(Command.UpdateImport(ImportState.Imported(trackA)))
        fiber <- awaitExport(store)(isFinalExport).start
        _ <- IO.blocking(controller.save(Paths.get("song")))
        _ <- fiber.joinWithNever
      yield assertEquals(writer.saved.get.map(_._2), Vector(Paths.get("song.wav")))
    }
  }

  test("save ends in state Failed with the error returned by the writer") {
    val error = AudioSaveError.WriteFailure(outputPath, "disk full")
    withController(succeedingReader, FakeWriter((_, _) => Left(error))) { (store, controller) =>
      for
        _ <- store.dispatch(Command.UpdateImport(ImportState.Imported(trackA)))
        fiber <- awaitExport(store)(isFinalExport).start
        _ <- IO.blocking(controller.save(outputPath))
        state <- fiber.joinWithNever
      yield assertEquals(state, ExportState.Failed(error))
    }
  }

  test("save ends in state Failed when the writer throws an unexpected exception") {
    withController(succeedingReader, FakeWriter((_, _) => throw RuntimeException("error"))) { (store, controller) =>
      for
        _ <- store.dispatch(Command.UpdateImport(ImportState.Imported(trackA)))
        fiber <- awaitExport(store)(isFinalExport).start
        _ <- IO.blocking(controller.save(outputPath))
        state <- fiber.joinWithNever
      yield assertEquals(state, ExportState.Failed(AudioSaveError.WriteFailure(outputPath, "error")))
    }
  }

  test("save fails with NoTrackLoaded when no track is loaded") {
    val writer = succeedingWriter
    withController(succeedingReader, writer) { (store, controller) =>
      for
        fiber <- awaitExport(store)(isFinalExport).start
        _ <- IO.blocking(controller.save(outputPath))
        state <- fiber.joinWithNever
      yield
        assertEquals(state, ExportState.Failed(AudioSaveError.NoTrackLoaded))
        assertEquals(writer.saved.get, Vector.empty)
    }
  }

  test("save exports the track loaded when it was requested, even if another one is loaded meanwhile") {
    Dispatcher.parallel[IO].use { dispatcher =>
      for
        exportGate <- Deferred[IO, Unit]
        writer = FakeWriter { (_, _) =>
          dispatcher.unsafeRunSync(exportGate.get)
          Right(())
        }
        reader = FakeReader(_ => Right(trackB))
        _ <- withController(reader, writer) { (store, controller) =>
          for
            _ <- store.dispatch(Command.UpdateImport(ImportState.Imported(trackA)))
            exportingFib <- awaitExport(store)(_ == ExportState.Exporting).start
            _ <- IO.blocking(controller.save(outputPath))
            _ <- exportingFib.joinWithNever
            importedFib <- awaitImport(store)(_ == ImportState.Imported(trackB)).start
            _ <- IO.blocking(controller.load(inputPath))
            _ <- importedFib.joinWithNever
            exportedFib <- awaitExport(store)(isFinalExport).start
            _ <- exportGate.complete(()).void
            state <- exportedFib.joinWithNever
          yield
            assertEquals(state, ExportState.Exported)
            assertEquals(writer.saved.get.map(_._1), Vector[AudioTrack](trackA))
        }
      yield ()
    }
  }

  test("save is ignored while an import is running") {
    val writer = succeedingWriter
    withController(succeedingReader, writer) { (store, controller) =>
      for
        _ <- store.dispatch(Command.UpdateImport(ImportState.Imported(trackA)))
        _ <- store.dispatch(Command.UpdateImport(ImportState.Importing))
        _ <- IO.blocking(controller.save(outputPath))
        current <- store.current
      yield
        assertEquals(current.exportState, ExportState.Idle)
        assertEquals(writer.saved.get, Vector.empty)
    }
  }

  test("a second save is ignored while the first one is still running") {
    Dispatcher.parallel[IO].use { dispatcher =>
      for
        exportGate <- Deferred[IO, Unit]
        writer = FakeWriter { (_, _) =>
          dispatcher.unsafeRunSync(exportGate.get)
          Right(())
        }
        _ <- withController(succeedingReader, writer) { (store, controller) =>
          for
            _ <- store.dispatch(Command.UpdateImport(ImportState.Imported(trackA)))
            exportingFib <- awaitExport(store)(_ == ExportState.Exporting).start
            exportedFib <- awaitExport(store)(isFinalExport).start
            _ <- IO.blocking(controller.save(outputPath))
            _ <- exportingFib.joinWithNever
            _ <- IO.blocking(controller.save(Paths.get("other.wav")))
            _ <- exportGate.complete(()).void
            _ <- exportedFib.joinWithNever
          yield assertEquals(writer.saved.get.map(_._2), Vector(outputPath))
        }
      yield ()
    }
  }

  test("acknowledging a completed or failed import brings the import state back to Idle") {
    withController(succeedingReader, succeedingWriter) { (store, controller) =>
      for
        _ <- store.dispatch(Command.UpdateImport(ImportState.Imported(trackA)))
        _ <- IO.blocking(controller.acknowledgeImport)
        afterImported <- awaitImport(store)(_ == ImportState.Idle)

        _ <- store.dispatch(Command.UpdateImport(ImportState.Failed(AudioLoadError.FileNotFound("x.wav"))))
        _ <- IO.blocking(controller.acknowledgeImport)
        afterFailed <- awaitImport(store)(_ == ImportState.Idle)
      yield
        assertEquals(afterImported, ImportState.Idle)
        assertEquals(afterFailed, ImportState.Idle)
    }
  }

  test("acknowledging an import that is in progress does nothing") {
    withController(succeedingReader, succeedingWriter) { (store, controller) =>
      for
        _ <- store.dispatch(Command.UpdateImport(ImportState.Importing))
        _ <- IO.blocking(controller.acknowledgeImport)
        current <- awaitImport(store)(_ == ImportState.Importing)
      yield assertEquals(current, ImportState.Importing)
    }
  }

  test("acknowledging a completed or failed export brings the export state back to Idle") {
    withController(succeedingReader, succeedingWriter) { (store, controller) =>
      for
        _ <- store.dispatch(Command.UpdateExport(ExportState.Exported))
        _ <- IO.blocking(controller.acknowledgeExport)
        afterExported <- awaitExport(store)(_ == ExportState.Idle)

        _ <- store.dispatch(Command.UpdateExport(ExportState.Failed(AudioSaveError.NoTrackLoaded)))
        _ <- IO.blocking(controller.acknowledgeExport)
        afterFailed <- awaitExport(store)(_ == ExportState.Idle)
      yield
        assertEquals(afterExported, ExportState.Idle)
        assertEquals(afterFailed, ExportState.Idle)
    }
  }

  test("acknowledging an export that is in progress does nothing") {
    withController(succeedingReader, succeedingWriter) { (store, controller) =>
      for
        _ <- store.dispatch(Command.UpdateExport(ExportState.Exporting))
        _ <- IO.blocking(controller.acknowledgeExport)
        current <- awaitExport(store)(_ == ExportState.Exporting)
      yield assertEquals(current, ExportState.Exporting)
    }
  }

  test("withWavExtension appends '.wav' only when it is missing") {
    assertEquals(LoadSaveController.withWavExtension(Paths.get("track")), Paths.get("track.wav"))
    assertEquals(LoadSaveController.withWavExtension(Paths.get("track.wav")), Paths.get("track.wav"))
    assertEquals(LoadSaveController.withWavExtension(Paths.get("track.WAV")), Paths.get("track.WAV"))
    assertEquals(LoadSaveController.withWavExtension(Paths.get("track.mp3")), Paths.get("track.mp3.wav"))
    assertEquals(LoadSaveController.withWavExtension(Paths.get("dir", "track")), Paths.get("dir", "track.wav"))
  }
