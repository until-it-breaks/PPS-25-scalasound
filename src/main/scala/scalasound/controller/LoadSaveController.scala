package scalasound.controller

import cats.effect.std.Supervisor
import cats.effect.{IO, Ref, Resource}
import scalasound.model.domain.AudioTrack
import scalasound.model.io.{AudioLoadError, AudioReader, AudioSaveError, AudioWriter}
import scalasound.model.processing.{AudioEffect, AudioProcessor, AudioProcessorWithRules}
import scalasound.model.state.{AppStateStore, Command, ExportState, ImportState}

import java.nio.file.Path
import java.util.Locale

/** The operations a user can perform to import and export audio tracks: load a file and save the
  * current track.
  */
trait LoadSaveController:

  /** Starts loading the audio file at the given path in the background.
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
    * @param reader
    *   reads the audio files
    * @param writer
    *   writes the audio files
    * @param processor
    *   applies the active effects before exporting
    */
  def make(
      store: AppStateStore,
      reader: AudioReader,
      writer: AudioWriter,
      processor: AudioProcessor = AudioProcessorWithRules
  ): Resource[IO, LoadSaveController] =
    for
      supervisor <- Supervisor[IO](await = false)
      importBusy <- Resource.eval(Ref.of[IO, Boolean](false))
      exportBusy <- Resource.eval(Ref.of[IO, Boolean](false))
    yield LoadSaveControllerImpl(
      store,
      reader,
      writer,
      processor,
      supervisor,
      importBusy,
      exportBusy
    )

  /** Returns the given path ending with `.wav`, appending the extension only if it is missing.
    *
    * @param path
    *   the path chosen by the user
    */
  def withWavExtension(path: Path): Path =
    Option(path.getFileName).map(_.toString) match
      case Some(name) if name.toLowerCase(Locale.ROOT).endsWith(".wav") => path
      case Some(name) => path.resolveSibling(s"$name.wav")
      case None       => path

private[controller] final class LoadSaveControllerImpl(
    store: AppStateStore,
    reader: AudioReader,
    writer: AudioWriter,
    processor: AudioProcessor,
    supervisor: Supervisor[IO],
    importBusy: Ref[IO, Boolean],
    exportBusy: Ref[IO, Boolean]
) extends LoadSaveController:

  override def load(path: Path): IO[Unit] =
    runInBackground(startIfIdle(importBusy)(runImport(path)))

  private def runImport(path: Path): IO[Unit] =
    for
      _      <- store.dispatch(Command.UpdateImport(ImportState.Importing))
      result <- IO.blocking(reader.load(path.toString)).attempt
      _      <- store.dispatch(Command.UpdateImport(toImportState(result)))
    yield ()

  private def toImportState(
      result: Either[Throwable, Either[AudioLoadError, AudioTrack]]
  ): ImportState =
    result match
      case Right(Right(track)) => ImportState.Imported(track)
      case Right(Left(error))  => ImportState.Failed(error)
      case Left(throwable) => ImportState.Failed(AudioLoadError.ReadFailure(throwable.description))

  override def save(path: Path): IO[Unit] =
    runInBackground:
      store.current.flatMap: state =>
        state.importState match
          case ImportState.Importing => IO.unit
          case _                     =>
            val targetPath = LoadSaveController.withWavExtension(path)
            startIfIdle(exportBusy)(runExport(state.track, state.effects, targetPath))

  private def runInBackground(task: IO[Unit]): IO[Unit] =
    supervisor.supervise(task).void

  private def startIfIdle(busy: Ref[IO, Boolean])(task: IO[Unit]): IO[Unit] =
    busy.getAndSet(true).flatMap:
      case true  => IO.unit
      case false => task.guarantee(busy.set(false))

  private def runExport(
      snapshot: Option[AudioTrack],
      effects: List[AudioEffect],
      path: Path
  ): IO[Unit] =
    snapshot match
      case None =>
        store.dispatch(Command.UpdateExport(ExportState.Failed(AudioSaveError.NoTrackLoaded))).void
      case Some(track) =>
        for
          _      <- store.dispatch(Command.UpdateExport(ExportState.Exporting))
          result <- IO.blocking(processAndSave(track, effects, path)).attempt
          _      <- store.dispatch(Command.UpdateExport(toExportState(result, path)))
        yield ()

  private def processAndSave(
      track: AudioTrack,
      effects: List[AudioEffect],
      path: Path
  ): Either[AudioSaveError, Unit] =
    for
      processed <- processor
        .apply(track, effects)
        .left
        .map(error => AudioSaveError.InvalidTrack(error.toString))
      _ <- writer.save(processed, path)
    yield ()

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
    store.current.map(_.importState).flatMap:
      case ImportState.Imported(_) | ImportState.Failed(_) =>
        store.dispatch(Command.UpdateImport(ImportState.Idle)).void
      case _ => IO.unit

  override def acknowledgeExport: IO[Unit] =
    store.current.map(_.exportState).flatMap:
      case ExportState.Exported | ExportState.Failed(_) =>
        store.dispatch(Command.UpdateExport(ExportState.Idle)).void
      case _ => IO.unit

  extension (throwable: Throwable)
    private def description: String =
      Option(throwable.getMessage).getOrElse(throwable.getClass.getSimpleName)
