package scalasound.controller

import cats.effect.std.Supervisor
import cats.effect.{IO, Ref, Resource}
import scalasound.model.domain.AudioTrack
import scalasound.model.io.error.{AudioLoadError, AudioSaveError}
import scalasound.model.io.{AudioFormatRegistry, ResolvedWriter}
import scalasound.model.processing.{AudioEffect, AudioProcessor, AudioProcessorWithRules}
import scalasound.model.state.{AppStateStore, Command, ExportState, ImportState}

import java.nio.file.Path

/** The operations a user can perform to import and export audio tracks: load a file and save the
  * current track.
  */
trait LoadSaveController:

  /** Starts loading the audio file at the given path in the background.
    *
    * The extension of the audio file selects the reader.
    *
    * The import state goes from `Idle` to `Importing` and then to `Imported(track)` or
    * `Failed(error)`.
    *
    * @param path
    *   the file to load
    */
  def load(path: Path): IO[Unit]

  /** Starts saving the currently loaded track (with active effects applied) to the given path in
    * the background.
    *
    * The extension of the path selects the writer; a path without an extension is saved in the
    * default format of the registry.
    *
    * The export state goes from `Idle` to `Exporting` and then to `Exported` or `Failed(error)`.
    *
    * @param path
    *   the destination file
    */
  def save(path: Path): IO[Unit]

  /** Acknowledges the result of an import, bringing the import state back to `Idle`. */
  def acknowledgeImport: IO[Unit]

  /** Acknowledges the result of an export, bringing the export state back to `Idle`. */
  def acknowledgeExport: IO[Unit]

object LoadSaveController:

  /** Creates a [[LoadSaveController]] that runs file I/O in supervised background fibers and
    * updates the [[AppStateStore]] with the results.
    *
    * @param store
    *   the [[AppStateStore]] that holds the current track and receives the state updates
    * @param formats
    *   the [[AudioFormatRegistry]] that selects the reader or the writer of a file from its extension
    * @param processor
    *   applies the active effects before exporting
    */
  def make(
      store: AppStateStore,
      formats: AudioFormatRegistry,
      processor: AudioProcessor = AudioProcessorWithRules
  ): Resource[IO, LoadSaveController] =
    for
      supervisor <- Supervisor[IO](await = false)
      importBusy <- Resource.eval(Ref.of[IO, Boolean](false))
      exportBusy <- Resource.eval(Ref.of[IO, Boolean](false))
    yield LoadSaveControllerImpl(
      store,
      formats,
      processor,
      supervisor,
      importBusy,
      exportBusy
    )

private[controller] final class LoadSaveControllerImpl(
    store: AppStateStore,
    formats: AudioFormatRegistry,
    processor: AudioProcessor,
    supervisor: Supervisor[IO],
    importBusy: Ref[IO, Boolean],
    exportBusy: Ref[IO, Boolean]
) extends LoadSaveController:

  override def load(path: Path): IO[Unit] =
    runInBackground(startIfIdle(importBusy)(runImport(path)))

  private def runImport(path: Path): IO[Unit] =
    val operation =
      for
        _      <- store.dispatch(Command.UpdateImport(ImportState.Importing))
        result <- loadFrom(path).attempt
        _      <- store.dispatch(Command.UpdateImport(toImportState(result)))
      yield ()
    operation.onCancel(store.dispatch(Command.UpdateImport(ImportState.Idle)).void)

  private def loadFrom(path: Path): IO[Either[AudioLoadError, AudioTrack]] =
    formats.readerFor(path) match
      case Left(error)   => IO.pure(Left(AudioLoadError.UnresolvedFormat(error)))
      case Right(reader) => IO.defer(reader.load(path.toString))

  private def toImportState(
      result: Either[Throwable, Either[AudioLoadError, AudioTrack]]
  ): ImportState =
    result match
      case Right(Right(track)) => ImportState.Imported(track)
      case Right(Left(error))  => ImportState.Failed(error)
      case Left(throwable) => ImportState.Failed(AudioLoadError.ReadFailure(throwable.description))

  override def save(path: Path): IO[Unit] =
    runInBackground:
      for
        state <- store.current
        _     <- state.importState match
          case ImportState.Importing => IO.unit
          case _ => startIfIdle(exportBusy)(runExport(state.track, state.effects, path))
      yield ()

  private def runInBackground(task: IO[Unit]): IO[Unit] =
    supervisor.supervise(task).void

  private def startIfIdle(busy: Ref[IO, Boolean])(task: IO[Unit]): IO[Unit] =
    for
      wasBusy <- busy.getAndSet(true)
      _       <- if wasBusy then IO.unit else task.guarantee(busy.set(false))
    yield ()

  private def runExport(
      snapshot: Option[AudioTrack],
      effects: List[AudioEffect],
      path: Path
  ): IO[Unit] =
    snapshot match
      case None =>
        store.dispatch(Command.UpdateExport(ExportState.Failed(AudioSaveError.NoTrackLoaded))).void
      case Some(track) =>
        val operation =
          for
            _      <- store.dispatch(Command.UpdateExport(ExportState.Exporting))
            result <- processAndSave(track, effects, path).attempt
            _      <- store.dispatch(Command.UpdateExport(toExportState(result, path)))
          yield ()
        operation.onCancel(store.dispatch(Command.UpdateExport(ExportState.Idle)).void)

  private def processAndSave(
      track: AudioTrack,
      effects: List[AudioEffect],
      path: Path
  ): IO[Either[AudioSaveError, Unit]] =
    IO.delay(resolveAndProcess(track, effects, path)).flatMap:
      case Left(error)                => IO.pure(Left(error))
      case Right((target, processed)) => IO.defer(target.writer.save(processed, target.path))

  private def resolveAndProcess(
      track: AudioTrack,
      effects: List[AudioEffect],
      path: Path
  ): Either[AudioSaveError, (ResolvedWriter, AudioTrack)] =
    for
      target <- formats.writerFor(path).left.map(AudioSaveError.UnresolvedFormat.apply)
      processed <- processor
        .apply(track, effects)
        .left
        .map(error => AudioSaveError.InvalidTrack(error.toString))
    yield (target, processed)

  private def toExportState(
      result: Either[Throwable, Either[AudioSaveError, Unit]],
      path: Path
  ): ExportState =
    result match
      case Right(Right(_))    => ExportState.Exported
      case Right(Left(error)) => ExportState.Failed(error)
      case Left(throwable)    =>
        ExportState.Failed(AudioSaveError.WriteFailure(path, throwable.description))

  override def acknowledgeImport: IO[Unit] =
    for
      state <- store.current
      _     <- state.importState match
        case ImportState.Imported(_) | ImportState.Failed(_) =>
          store.dispatch(Command.UpdateImport(ImportState.Idle)).void
        case _ => IO.unit
    yield ()

  override def acknowledgeExport: IO[Unit] =
    for
      state <- store.current
      _     <- state.exportState match
        case ExportState.Exported | ExportState.Failed(_) =>
          store.dispatch(Command.UpdateExport(ExportState.Idle)).void
        case _ => IO.unit
    yield ()

  extension (throwable: Throwable)
    private def description: String =
      Option(throwable.getMessage).getOrElse(throwable.getClass.getSimpleName)
