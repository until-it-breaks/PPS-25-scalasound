package scalasound.controller

import cats.effect.{Deferred, IO}
import munit.CatsEffectSuite
import scalasound.model.domain.AudioChunk.MonoChunk
import scalasound.model.domain.{AudioTrack, MonoTrack}
import scalasound.model.io.*
import scalasound.model.io.FormatResolutionError.*
import scalasound.model.state.{AppStateStore, Command, ExportState, ImportState}

import java.nio.file.{Path, Paths}
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration.*

class LoadSaveControllerSpec extends CatsEffectSuite:

  private val trackA =
    MonoTrack(44100, Vector(MonoChunk(Vector(0.5f)))).fold(e => fail(e.toString), identity)
  private val trackB =
    MonoTrack(48000, Vector(MonoChunk(Vector(-0.25f)))).fold(e => fail(e.toString), identity)
  private val inputPath  = Paths.get("in.wav")
  private val outputPath = Paths.get("out.wav")

  private def succeedingReader = FakeReader(_ => IO.pure(Right(trackA)))

  private def succeedingWriter = FakeWriter((_, _) => IO.pure(Right(())))

  private def withController(reader: AudioReader, writer: AudioWriter)(
      test: (AppStateStore, LoadSaveController) => IO[Unit]
  ): IO[Unit] = withFormats(wavFormat(reader, writer))(test)

  private def wavFormat(reader: AudioReader, writer: AudioWriter): AudioFileType =
    AudioFileType("WAV", "wav", reader = Some(reader), writer = Some(writer))

  private def withFormats(fileTypes: AudioFileType*)(
      test: (AppStateStore, LoadSaveController) => IO[Unit]
  ): IO[Unit] =
    val formats = AudioFormatRegistry.of(fileTypes*).fold(error => fail(error.toString), identity)
    AppStateStore.make.flatMap: store =>
      LoadSaveController.make(store, formats).use(controller => test(store, controller))

  private def awaitImport(
      store: AppStateStore
  )(predicate: ImportState => Boolean): IO[ImportState] =
    store.changes
      .map(_.importState)
      .filter(predicate)
      .take(1)
      .compile
      .lastOrError
      .timeout(5.seconds)

  private def awaitExport(
      store: AppStateStore
  )(predicate: ExportState => Boolean): IO[ExportState] =
    store.changes
      .map(_.exportState)
      .filter(predicate)
      .take(1)
      .compile
      .lastOrError
      .timeout(5.seconds)

  private def isFinalImport(state: ImportState): Boolean = state match
    case ImportState.Imported(_) | ImportState.Failed(_) => true
    case _                                               => false

  private def isFinalExport(state: ExportState): Boolean = state match
    case ExportState.Exported | ExportState.Failed(_) => true
    case _                                            => false

  private def retryUntilSuccess(attempt: IO[Boolean]): IO[Unit] =
    attempt.flatMap:
      case true  => IO.unit
      case false => IO.cede >> retryUntilSuccess(attempt)

  private final class FakeReader(
      behaviour: String => IO[Either[AudioLoadError, AudioTrack]]
  ) extends AudioReader:
    val loaded = AtomicReference(Vector.empty[String])

    override def load(filePath: String): IO[Either[AudioLoadError, AudioTrack]] =
      IO.defer:
        loaded.updateAndGet(_ :+ filePath)
        behaviour(filePath)

  private final class FakeWriter(
      behaviour: (AudioTrack, Path) => IO[Either[AudioSaveError, Unit]]
  ) extends AudioWriter:
    val saved = AtomicReference(Vector.empty[(AudioTrack, Path)])

    override def save(track: AudioTrack, path: Path): IO[Either[AudioSaveError, Unit]] =
      IO.defer:
        saved.updateAndGet(_ :+ (track -> path))
        behaviour(track, path)

  test("load imports the track and ends in state Imported"):
    val reader = succeedingReader
    withController(reader, succeedingWriter): (store, controller) =>
      for
        fiber   <- awaitImport(store)(isFinalImport).start
        _       <- controller.load(inputPath)
        state   <- fiber.joinWithNever
        current <- store.current
      yield
        assertEquals(state, ImportState.Imported(trackA))
        assertEquals(current.track, Some(trackA))
        assertEquals(reader.loaded.get, Vector("in.wav"))

  test("load ends in state Failed with the error returned by the reader"):
    val error = AudioLoadError.UnsupportedFormat("not a wav")
    withController(FakeReader(_ => IO.pure(Left(error))), succeedingWriter): (store, controller) =>
      for
        fiber   <- awaitImport(store)(isFinalImport).start
        _       <- controller.load(inputPath)
        state   <- fiber.joinWithNever
        current <- store.current
      yield
        assertEquals(state, ImportState.Failed(error))
        assertEquals(current.track, None)

  test("load ends in state Failed when the reader throws an unexpected exception"):
    withController(FakeReader(_ => throw RuntimeException("error")), succeedingWriter):
      (store, controller) =>
        for
          fiber <- awaitImport(store)(isFinalImport).start
          _     <- controller.load(inputPath)
          state <- fiber.joinWithNever
        yield assertEquals(state, ImportState.Failed(AudioLoadError.ReadFailure("error")))

  test("load ends in state Failed when the reader effect fails with an error"):
    withController(FakeReader(_ => IO.raiseError(RuntimeException("error"))), succeedingWriter):
      (store, controller) =>
        for
          fiber <- awaitImport(store)(isFinalImport).start
          _     <- controller.load(inputPath)
          state <- fiber.joinWithNever
        yield assertEquals(state, ImportState.Failed(AudioLoadError.ReadFailure("error")))

  test("load reports the state Importing while the reader is working"):
    for
      importGate <- Deferred[IO, Unit]
      reader = FakeReader(_ => importGate.get.as(Right(trackA)))
      _ <- withController(reader, succeedingWriter): (store, controller) =>
        for
          importingFiber <- awaitImport(store)(_ == ImportState.Importing).start
          doneFiber      <- awaitImport(store)(isFinalImport).start
          _              <- controller.load(inputPath)
          importing      <- importingFiber.joinWithNever
          _              <- importGate.complete(()).void
          done           <- doneFiber.joinWithNever
        yield
          assertEquals(importing, ImportState.Importing)
          assertEquals(done, ImportState.Imported(trackA))
    yield ()

  test("load is accepted and runs while an export is currently in progress"):
    for
      exportGate <- Deferred[IO, Unit]
      writer = FakeWriter((_, _) => exportGate.get.as(Right(())))
      reader = succeedingReader
      _ <- withController(reader, writer): (store, controller) =>
        for
          _           <- store.dispatch(Command.UpdateImport(ImportState.Imported(trackA)))
          exportFiber <- awaitExport(store)(_ == ExportState.Exporting).start
          _           <- controller.save(outputPath)
          _           <- exportFiber.joinWithNever
          importFiber <- awaitImport(store)(isFinalImport).start
          _           <- controller.load(inputPath)
          importState <- importFiber.joinWithNever
          _           <- exportGate.complete(()).void
        yield assertEquals(importState, ImportState.Imported(trackA))
    yield ()

  test("a second load is ignored while the first one is still running"):
    for
      importGate <- Deferred[IO, Unit]
      reader = FakeReader(_ => importGate.get.as(Right(trackA)))
      _ <- withController(reader, succeedingWriter): (store, controller) =>
        for
          importingFiber <- awaitImport(store)(_ == ImportState.Importing).start
          doneFiber      <- awaitImport(store)(isFinalImport).start
          _              <- controller.load(inputPath)
          _              <- importingFiber.joinWithNever
          _              <- controller.load(Paths.get("other.wav"))
          _              <- importGate.complete(()).void
          _              <- doneFiber.joinWithNever
        yield assertEquals(reader.loaded.get, Vector("in.wav"))
    yield ()

  test("a new load is accepted once the previous one has completed"):
    val reader = succeedingReader
    withController(reader, succeedingWriter): (store, controller) =>
      for
        firstFiber <- awaitImport(store)(isFinalImport).start
        _          <- controller.load(inputPath)
        _          <- firstFiber.joinWithNever
        _          <- retryUntilSuccess(
          controller.load(inputPath) >> IO(reader.loaded.get.size >= 2)
        ).timeout(5.seconds)
      yield ()

  test("save exports the current track and ends in state Exported"):
    val writer = succeedingWriter
    withController(succeedingReader, writer): (store, controller) =>
      for
        _     <- store.dispatch(Command.UpdateImport(ImportState.Imported(trackA)))
        fiber <- awaitExport(store)(isFinalExport).start
        _     <- controller.save(outputPath)
        state <- fiber.joinWithNever
      yield
        assertEquals(state, ExportState.Exported)
        assertEquals(writer.saved.get, Vector[(AudioTrack, Path)](trackA -> outputPath))

  test("save appends the '.wav' extension when it is missing"):
    val writer = succeedingWriter
    withController(succeedingReader, writer): (store, controller) =>
      for
        _     <- store.dispatch(Command.UpdateImport(ImportState.Imported(trackA)))
        fiber <- awaitExport(store)(isFinalExport).start
        _     <- controller.save(Paths.get("song"))
        _     <- fiber.joinWithNever
      yield assertEquals(writer.saved.get.map(_._2), Vector(Paths.get("song.wav")))

  test("save ends in state Failed with the error returned by the writer"):
    val error = AudioSaveError.WriteFailure(outputPath, "disk full")
    withController(succeedingReader, FakeWriter((_, _) => IO.pure(Left(error)))): (store, controller) =>
      for
        _     <- store.dispatch(Command.UpdateImport(ImportState.Imported(trackA)))
        fiber <- awaitExport(store)(isFinalExport).start
        _     <- controller.save(outputPath)
        state <- fiber.joinWithNever
      yield assertEquals(state, ExportState.Failed(error))

  test("save ends in state Failed when the writer throws an unexpected exception"):
    withController(succeedingReader, FakeWriter((_, _) => throw RuntimeException("error"))):
      (store, controller) =>
        for
          _     <- store.dispatch(Command.UpdateImport(ImportState.Imported(trackA)))
          fiber <- awaitExport(store)(isFinalExport).start
          _     <- controller.save(outputPath)
          state <- fiber.joinWithNever
        yield assertEquals(
          state,
          ExportState.Failed(AudioSaveError.WriteFailure(outputPath, "error"))
        )

  test("save fails with NoTrackLoaded when no track is loaded"):
    val writer = succeedingWriter
    withController(succeedingReader, writer): (store, controller) =>
      for
        fiber <- awaitExport(store)(isFinalExport).start
        _     <- controller.save(outputPath)
        state <- fiber.joinWithNever
      yield
        assertEquals(state, ExportState.Failed(AudioSaveError.NoTrackLoaded))
        assertEquals(writer.saved.get, Vector.empty)

  test(
    "save exports the track loaded when it was requested, even if another one is loaded meanwhile"
  ):
    for
      exportGate <- Deferred[IO, Unit]
      writer = FakeWriter((_, _) => exportGate.get.as(Right(())))
      reader = FakeReader(_ => IO.pure(Right(trackB)))
      _ <- withController(reader, writer): (store, controller) =>
        for
          _            <- store.dispatch(Command.UpdateImport(ImportState.Imported(trackA)))
          exportingFib <- awaitExport(store)(_ == ExportState.Exporting).start
          _            <- controller.save(outputPath)
          _            <- exportingFib.joinWithNever
          importedFib  <- awaitImport(store)(_ == ImportState.Imported(trackB)).start
          _            <- controller.load(inputPath)
          _            <- importedFib.joinWithNever
          exportedFib  <- awaitExport(store)(isFinalExport).start
          _            <- exportGate.complete(()).void
          state        <- exportedFib.joinWithNever
        yield
          assertEquals(state, ExportState.Exported)
          assertEquals(writer.saved.get.map(_._1), Vector[AudioTrack](trackA))
    yield ()

  test("save ends in state Failed when the writer effect fails with an error"):
    withController(succeedingReader, FakeWriter((_, _) => IO.raiseError(RuntimeException("error")))):
      (store, controller) =>
        for
          _     <- store.dispatch(Command.UpdateImport(ImportState.Imported(trackA)))
          fiber <- awaitExport(store)(isFinalExport).start
          _     <- controller.save(outputPath)
          state <- fiber.joinWithNever
        yield assertEquals(
          state,
          ExportState.Failed(AudioSaveError.WriteFailure(outputPath, "error"))
        )

  test("save is ignored while an import is running"):
    val writer = succeedingWriter
    withController(succeedingReader, writer): (store, controller) =>
      for
        _       <- store.dispatch(Command.UpdateImport(ImportState.Imported(trackA)))
        _       <- store.dispatch(Command.UpdateImport(ImportState.Importing))
        _       <- controller.save(outputPath)
        current <- store.current
      yield
        assertEquals(current.exportState, ExportState.Idle)
        assertEquals(writer.saved.get, Vector.empty)

  test("acknowledging a completed or failed import brings the import state back to Idle"):
    withController(succeedingReader, succeedingWriter): (store, controller) =>
      for
        _             <- store.dispatch(Command.UpdateImport(ImportState.Imported(trackA)))
        _             <- controller.acknowledgeImport
        afterImported <- store.current.map(_.importState)

        _ <- store.dispatch(
          Command.UpdateImport(ImportState.Failed(AudioLoadError.FileNotFound("x.wav")))
        )
        _           <- controller.acknowledgeImport
        afterFailed <- store.current.map(_.importState)
      yield
        assertEquals(afterImported, ImportState.Idle)
        assertEquals(afterFailed, ImportState.Idle)

  test("acknowledging an import that is in progress does nothing"):
    withController(succeedingReader, succeedingWriter): (store, controller) =>
      for
        _       <- store.dispatch(Command.UpdateImport(ImportState.Importing))
        _       <- controller.acknowledgeImport
        current <- store.current.map(_.importState)
      yield assertEquals(current, ImportState.Importing)

  test("acknowledging a completed or failed export brings the export state back to Idle"):
    withController(succeedingReader, succeedingWriter): (store, controller) =>
      for
        _             <- store.dispatch(Command.UpdateExport(ExportState.Exported))
        _             <- controller.acknowledgeExport
        afterExported <- store.current.map(_.exportState)

        _ <- store.dispatch(Command.UpdateExport(ExportState.Failed(AudioSaveError.NoTrackLoaded)))
        _ <- controller.acknowledgeExport
        afterFailed <- store.current.map(_.exportState)
      yield
        assertEquals(afterExported, ExportState.Idle)
        assertEquals(afterFailed, ExportState.Idle)

  test("acknowledging an export that is in progress does nothing"):
    withController(succeedingReader, succeedingWriter): (store, controller) =>
      for
        _       <- store.dispatch(Command.UpdateExport(ExportState.Exporting))
        _       <- controller.acknowledgeExport
        current <- store.current.map(_.exportState)
      yield assertEquals(current, ExportState.Exporting)

  test("load uses only the reader of the format of the path"):
    val wavReader  = succeedingReader
    val fakeReader = FakeReader(_ => IO.pure(Right(trackB)))
    val wavWriter  = succeedingWriter
    val fakeWriter = succeedingWriter
    withFormats(
      wavFormat(wavReader, wavWriter),
      AudioFileType("Fake", "fake", reader = Some(fakeReader), writer = Some(fakeWriter))
    ): (store, controller) =>
      for
        fiber <- awaitImport(store)(isFinalImport).start
        _     <- controller.load(Paths.get("song.FAKE"))
        state <- fiber.joinWithNever
      yield
        assertEquals(state, ImportState.Imported(trackB))
        assertEquals(fakeReader.loaded.get, Vector("song.FAKE"))
        assertEquals(wavReader.loaded.get, Vector.empty)
        assertEquals(wavWriter.saved.get, Vector.empty)
        assertEquals(fakeWriter.saved.get, Vector.empty)

  test("save uses only the writer of the format of the path"):
    val wavReader  = succeedingReader
    val wavWriter  = succeedingWriter
    val fakeReader = succeedingReader
    val fakeWriter = succeedingWriter
    withFormats(
      wavFormat(wavReader, wavWriter),
      AudioFileType("Fake", "fake", reader = Some(fakeReader), writer = Some(fakeWriter))
    ): (store, controller) =>
      for
        _     <- store.dispatch(Command.UpdateImport(ImportState.Imported(trackA)))
        fiber <- awaitExport(store)(isFinalExport).start
        _     <- controller.save(Paths.get("out.fake"))
        state <- fiber.joinWithNever
      yield
        assertEquals(state, ExportState.Exported)
        assertEquals(fakeWriter.saved.get.map(_._2), Vector(Paths.get("out.fake")))
        assertEquals(wavWriter.saved.get, Vector.empty)
        assertEquals(wavReader.loaded.get, Vector.empty)
        assertEquals(fakeReader.loaded.get, Vector.empty)

  test(
    "load of a path, with an unsupported extension, fails with UnresolvedFormat and reads nothing"
  ):
    val reader = succeedingReader
    withController(reader, succeedingWriter): (store, controller) =>
      for
        fiber <- awaitImport(store)(isFinalImport).start
        _     <- controller.load(Paths.get("song.mp3"))
        state <- fiber.joinWithNever
      yield
        assertEquals(
          state,
          ImportState.Failed(AudioLoadError.UnresolvedFormat(UnsupportedExtension("mp3")))
        )
        assertEquals(reader.loaded.get, Vector.empty)

  test("load of a path, without an extension, fails with UnresolvedFormat"):
    withController(succeedingReader, succeedingWriter): (store, controller) =>
      for
        fiber <- awaitImport(store)(isFinalImport).start
        _     <- controller.load(Paths.get("song"))
        state <- fiber.joinWithNever
      yield assertEquals(
        state,
        ImportState.Failed(AudioLoadError.UnresolvedFormat(MissingExtension(Paths.get("song"))))
      )

  test("load of a format, that cannot be read, fails with UnresolvedFormat"):
    withFormats(AudioFileType("Export only", "out", writer = Some(succeedingWriter))):
      (store, controller) =>
        for
          fiber <- awaitImport(store)(isFinalImport).start
          _     <- controller.load(Paths.get("song.out"))
          state <- fiber.joinWithNever
        yield assertEquals(
          state,
          ImportState.Failed(AudioLoadError.UnresolvedFormat(ReadingNotSupported("Export only")))
        )

  test(
    "save to a path, with an unsupported extension, fails with UnresolvedFormat and writes nothing"
  ):
    val writer = succeedingWriter
    withController(succeedingReader, writer): (store, controller) =>
      for
        _       <- store.dispatch(Command.UpdateImport(ImportState.Imported(trackA)))
        fiber   <- awaitExport(store)(isFinalExport).start
        _       <- controller.save(Paths.get("track.mp3"))
        state   <- fiber.joinWithNever
        current <- store.current
      yield
        assertEquals(
          state,
          ExportState.Failed(AudioSaveError.UnresolvedFormat(UnsupportedExtension("mp3")))
        )
        assertEquals(writer.saved.get, Vector.empty)
        assertEquals(current.track, Some(trackA))
        assertEquals(current.importState, ImportState.Imported(trackA))

  test("save in a format, that cannot be written, fails with UnresolvedFormat"):
    withFormats(
      wavFormat(succeedingReader, succeedingWriter),
      AudioFileType("Import only", "in", reader = Some(succeedingReader))
    ): (store, controller) =>
      for
        _     <- store.dispatch(Command.UpdateImport(ImportState.Imported(trackA)))
        fiber <- awaitExport(store)(isFinalExport).start
        _     <- controller.save(Paths.get("out.in"))
        state <- fiber.joinWithNever
      yield assertEquals(
        state,
        ExportState.Failed(AudioSaveError.UnresolvedFormat(WritingNotSupported("Import only")))
      )

  test("save without a loaded track still reports NoTrackLoaded"):
    withController(succeedingReader, succeedingWriter): (store, controller) =>
      for
        fiber <- awaitExport(store)(isFinalExport).start
        _     <- controller.save(Paths.get("track.mp3"))
        state <- fiber.joinWithNever
      yield assertEquals(state, ExportState.Failed(AudioSaveError.NoTrackLoaded))

  test("a failed selection leaves the controller free to start another import"):
    val reader = succeedingReader
    withController(reader, succeedingWriter): (store, controller) =>
      for
        failed   <- awaitImport(store)(isFinalImport).start
        _        <- controller.load(Paths.get("song.mp3"))
        _        <- failed.joinWithNever
        _        <- controller.acknowledgeImport
        imported <- awaitImport(store)(isFinalImport).start
        _        <- controller.load(inputPath)
        state    <- imported.joinWithNever
      yield assertEquals(state, ImportState.Imported(trackA))

  test("the controller accepts a new import after a failed one with an effect error"):
    val reader = FakeReader(path =>
      if path == "bad.wav" then IO.raiseError(RuntimeException("error")) else IO.pure(Right(trackA))
    )
    withController(reader, succeedingWriter): (store, controller) =>
      for
        failed <- awaitImport(store)(isFinalImport).start
        _      <- controller.load(Paths.get("bad.wav"))
        first  <- failed.joinWithNever
        _      <- controller.acknowledgeImport
        ok     <- awaitImport(store)(isFinalImport).start
        _      <- retryUntilSuccess(controller.load(inputPath) >> IO(reader.loaded.get.contains("in.wav")))
        second <- ok.joinWithNever
      yield
        assertEquals(first, ImportState.Failed(AudioLoadError.ReadFailure("error")))
        assertEquals(second, ImportState.Imported(trackA))
