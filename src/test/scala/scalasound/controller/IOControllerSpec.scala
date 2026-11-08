package scalasound.controller

import cats.effect.IO
import munit.CatsEffectSuite
import scalasound.model.domain.AudioChunk.MonoChunk
import scalasound.model.domain.{AudioTrack, MonoTrack}
import scalasound.model.io.{AudioLoadError, AudioReader, AudioSaveError, AudioWriter}
import scalasound.model.state.{AppStateStore, Command, ExportState, ImportState}

import java.nio.file.{Path, Paths}
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.*

class IOControllerSpec extends CatsEffectSuite:

  private val trackA = MonoTrack(44100, Vector(MonoChunk(Vector(0.5f))))
  private val trackB = MonoTrack(48000, Vector(MonoChunk(Vector(-0.25f))))
  private val inputPath = Paths.get("in.wav")
  private val outputPath = Paths.get("out.wav")

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

  private def succeedingReader = FakeReader(_ => Right(trackA))
  private def succeedingWriter = FakeWriter((_, _) => Right(()))

  private def withController(reader: AudioReader, writer: AudioWriter)
                            (test: (AppStateStore, IOController) => IO[Unit]): IO[Unit] =
    AppStateStore.make.flatMap { store =>
      IOController.make(store, reader, writer).use(controller => test(store, controller))
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
    case ImportState.Imported(_) | ImportState.Failed(_) =>
      true
    case _ =>
      false

  private def isFinalExport(state: ExportState): Boolean = state match
    case ExportState.Exported | ExportState.Failed(_) =>
      true
    case _ =>
      false

  private def retryUntilSuccess(attempt: IO[Boolean]): IO[Unit] =
    attempt.flatMap {
      case true =>
        IO.unit
      case false =>
        IO.cede >> retryUntilSuccess(attempt)
    }

  test("load imports the track and ends in state Imported") {
    val reader = succeedingReader
    withController(reader, succeedingWriter) { (store, controller) =>
      for
        _ <- controller.load(inputPath)
        state <- awaitImport(store)(isFinalImport)
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
        _ <- controller.load(inputPath)
        state <- awaitImport(store)(isFinalImport)
        current <- store.current
      yield
        assertEquals(state, ImportState.Failed(error))
        assertEquals(current.track, None)
    }
  }

  test("load ends in state Failed when the reader throws an unexpected exception") {
    withController(FakeReader(_ => throw RuntimeException("error")), succeedingWriter) { (store, controller) =>
      for
        _ <- controller.load(inputPath)
        state <- awaitImport(store)(isFinalImport)
      yield assertEquals(state, ImportState.Failed(AudioLoadError.ReadFailure("error")))
    }
  }

  test("load reports the state Importing while the reader is working") {
    val gate = CountDownLatch(1)
    val reader = FakeReader { _ =>
      gate.await()
      Right(trackA)
    }
    withController(reader, succeedingWriter) { (store, controller) =>
      val scenario =
        for
          _ <- controller.load(inputPath)
          importing <- awaitImport(store)(_ == ImportState.Importing)
          _ <- IO(gate.countDown())
          done <- awaitImport(store)(isFinalImport)
        yield
          assertEquals(importing, ImportState.Importing)
          assertEquals(done, ImportState.Imported(trackA))
      scenario.guarantee(IO(gate.countDown()))
    }
  }

  test("load is accepted and runs while an export is currently in progress") {
    val exportGate = CountDownLatch(1)
    val writer = FakeWriter { (_, _) =>
      exportGate.await()
      Right(())
    }
    val reader = succeedingReader

    withController(reader, writer) { (store, controller) =>
      val scenario = for
        _ <- store.dispatch(Command.UpdateImport(ImportState.Imported(trackA)))
        _ <- controller.save(outputPath)
        _ <- awaitExport(store)(_ == ExportState.Exporting)
        _ <- controller.load(inputPath)
        importState <- awaitImport(store)(isFinalImport)
        _ <- IO(exportGate.countDown())
      yield assertEquals(importState, ImportState.Imported(trackA))

      scenario.guarantee(IO(exportGate.countDown()))
    }
  }

  test("a second load is ignored while the first one is still running") {
    val gate = CountDownLatch(1)
    val reader = FakeReader { _ =>
      gate.await()
      Right(trackA)
    }
    withController(reader, succeedingWriter) { (store, controller) =>
      val scenario =
        for
          _ <- controller.load(inputPath)
          _ <- awaitImport(store)(_ == ImportState.Importing)
          _ <- controller.load(Paths.get("other.wav"))
          _ <- IO(gate.countDown())
          _ <- awaitImport(store)(isFinalImport)
        yield assertEquals(reader.loaded.get, Vector("in.wav"))
      scenario.guarantee(IO(gate.countDown()))
    }
  }

  test("a new load is accepted once the previous one has completed") {
    val reader = succeedingReader
    withController(reader, succeedingWriter) { (store, controller) =>
      for
        _ <- controller.load(inputPath)
        _ <- awaitImport(store)(isFinalImport)
        // The controller is ready again just after the final state is published: retry until accepted.
        _ <- retryUntilSuccess(controller.load(inputPath) *> IO(reader.loaded.get.size >= 2)).timeout(5.seconds)
      yield ()
    }
  }

  test("save exports the current track and ends in state Exported") {
    val writer = succeedingWriter
    withController(succeedingReader, writer) { (store, controller) =>
      for
        _ <- store.dispatch(Command.UpdateImport(ImportState.Imported(trackA)))
        _ <- controller.save(outputPath)
        state <- awaitExport(store)(isFinalExport)
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
        _ <- controller.save(Paths.get("song"))
        _ <- awaitExport(store)(isFinalExport)
      yield assertEquals(writer.saved.get.map(_._2), Vector(Paths.get("song.wav")))
    }
  }

  test("save ends in state Failed with the error returned by the writer") {
    val error = AudioSaveError.WriteFailure(outputPath, "disk full")
    withController(succeedingReader, FakeWriter((_, _) => Left(error))) { (store, controller) =>
      for
        _ <- store.dispatch(Command.UpdateImport(ImportState.Imported(trackA)))
        _ <- controller.save(outputPath)
        state <- awaitExport(store)(isFinalExport)
      yield assertEquals(state, ExportState.Failed(error))
    }
  }

  test("save ends in state Failed when the writer throws an unexpected exception") {
    withController(succeedingReader, FakeWriter((_, _) => throw RuntimeException("error"))) { (store, controller) =>
      for
        _ <- store.dispatch(Command.UpdateImport(ImportState.Imported(trackA)))
        _ <- controller.save(outputPath)
        state <- awaitExport(store)(isFinalExport)
      yield assertEquals(state, ExportState.Failed(AudioSaveError.WriteFailure(outputPath, "error")))
    }
  }

  test("save fails with NoTrackLoaded when no track is loaded") {
    val writer = succeedingWriter
    withController(succeedingReader, writer) { (store, controller) =>
      for
        _ <- controller.save(outputPath)
        state <- awaitExport(store)(isFinalExport)
      yield
        assertEquals(state, ExportState.Failed(AudioSaveError.NoTrackLoaded))
        assertEquals(writer.saved.get, Vector.empty)
    }
  }

  test("save exports the track loaded when it was requested, even if another one is loaded meanwhile") {
    val gate = CountDownLatch(1)
    val writer = FakeWriter { (_, _) =>
      gate.await()
      Right(())
    }
    val reader = FakeReader(_ => Right(trackB))
    withController(reader, writer) { (store, controller) =>
      val scenario =
        for
          _ <- store.dispatch(Command.UpdateImport(ImportState.Imported(trackA)))
          _ <- controller.save(outputPath)
          _ <- awaitExport(store)(_ == ExportState.Exporting)
          _ <- controller.load(inputPath)
          _ <- awaitImport(store)(_ == ImportState.Imported(trackB))
          _ <- IO(gate.countDown())
          state <- awaitExport(store)(isFinalExport)
        yield
          assertEquals(state, ExportState.Exported)
          assertEquals(writer.saved.get.map(_._1), Vector[AudioTrack](trackA))
      scenario.guarantee(IO(gate.countDown()))
    }
  }

  test("save is ignored while an import is running") {
    val writer = succeedingWriter
    withController(succeedingReader, writer) { (store, controller) =>
      for
        _ <- store.dispatch(Command.UpdateImport(ImportState.Imported(trackA)))
        _ <- store.dispatch(Command.UpdateImport(ImportState.Importing))
        _ <- controller.save(outputPath)
        current <- store.current
      yield
        assertEquals(current.exportState, ExportState.Idle)
        assertEquals(writer.saved.get, Vector.empty)
    }
  }

  test("a second save is ignored while the first one is still running") {
    val gate = CountDownLatch(1)
    val writer = FakeWriter { (_, _) =>
      gate.await()
      Right(())
    }
    withController(succeedingReader, writer) { (store, controller) =>
      val scenario =
        for
          _ <- store.dispatch(Command.UpdateImport(ImportState.Imported(trackA)))
          _ <- controller.save(outputPath)
          _ <- awaitExport(store)(_ == ExportState.Exporting)
          _ <- controller.save(Paths.get("other.wav"))
          _ <- IO(gate.countDown())
          _ <- awaitExport(store)(isFinalExport)
        yield assertEquals(writer.saved.get.map(_._2), Vector(outputPath))
      scenario.guarantee(IO(gate.countDown()))
    }
  }

  test("acknowledging a completed or failed import brings the import state back to Idle") {
    withController(succeedingReader, succeedingWriter) { (store, controller) =>
      for
        _ <- store.dispatch(Command.UpdateImport(ImportState.Imported(trackA)))
        _ <- controller.acknowledgeImport
        afterImported <- store.current

        _ <- store.dispatch(Command.UpdateImport(ImportState.Failed(AudioLoadError.FileNotFound("x.wav"))))
        _ <- controller.acknowledgeImport
        afterFailed <- store.current
      yield
        assertEquals(afterImported.importState, ImportState.Idle)
        assertEquals(afterFailed.importState, ImportState.Idle)
    }
  }

  test("acknowledging an import that is in progress does nothing") {
    withController(succeedingReader, succeedingWriter) { (store, controller) =>
      for
        _ <- store.dispatch(Command.UpdateImport(ImportState.Importing))
        _ <- controller.acknowledgeImport
        current <- store.current
      yield assertEquals(current.importState, ImportState.Importing)
    }
  }

  test("acknowledging a completed or failed export brings the export state back to Idle") {
    withController(succeedingReader, succeedingWriter) { (store, controller) =>
      for
        _ <- store.dispatch(Command.UpdateExport(ExportState.Exported))
        _ <- controller.acknowledgeExport
        afterExported <- store.current

        _ <- store.dispatch(Command.UpdateExport(ExportState.Failed(AudioSaveError.NoTrackLoaded)))
        _ <- controller.acknowledgeExport
        afterFailed <- store.current
      yield
        assertEquals(afterExported.exportState, ExportState.Idle)
        assertEquals(afterFailed.exportState, ExportState.Idle)
    }
  }

  test("acknowledging an export that is in progress does nothing") {
    withController(succeedingReader, succeedingWriter) { (store, controller) =>
      for
        _ <- store.dispatch(Command.UpdateExport(ExportState.Exporting))
        _ <- controller.acknowledgeExport
        current <- store.current
      yield assertEquals(current.exportState, ExportState.Exporting)
    }
  }

  test("withWavExtension appends '.wav' only when it is missing") {
    assertEquals(IOController.withWavExtension(Paths.get("track")), Paths.get("track.wav"))
    assertEquals(IOController.withWavExtension(Paths.get("track.wav")), Paths.get("track.wav"))
    assertEquals(IOController.withWavExtension(Paths.get("track.WAV")), Paths.get("track.WAV"))
    assertEquals(IOController.withWavExtension(Paths.get("track.mp3")), Paths.get("track.mp3.wav"))
    assertEquals(IOController.withWavExtension(Paths.get("dir", "track")), Paths.get("dir", "track.wav"))
  }
