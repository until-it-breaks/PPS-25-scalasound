package scalasound.controller

import cats.effect.std.Supervisor
import cats.effect.unsafe.IORuntime
import cats.effect.{IO, Ref, Resource}
import scalasound.model.domain.AudioTrack
import scalasound.model.io.{AudioLoadError, AudioReader, AudioSaveError, AudioWriter}
import scalasound.model.state.{AppStateStore, Command, ExportState, ImportState}

import java.nio.file.Path
import java.util.Locale

/** The operations a user can perform to import and export audio tracks: load a file and save the current track. */
trait LoadSaveController:

  /** Loads the audio file at the given path.
   *
   *  The import state goes from `Idle` to `Importing`
   *  and then to `Imported(track)` or `Failed(error)`.
   *
   *  @param path the file to load
   */
  def load(path: Path): Unit

  /** Saves the track, that is currently loaded, to the given path.
   *
   *  The export state goes from `Idle` to `Exporting`
   *  and then to `Exported` or `Failed(error)`.
   *
   *  @param path the destination file
   */
  def save(path: Path): Unit

  /** Acknowledges the result of an import, bringing the import state back to `Idle`. */
  def acknowledgeImport: Unit

  /** Acknowledges the result of an export, bringing the export state back to `Idle`. */
  def acknowledgeExport: Unit

object LoadSaveController:

  /** Creates an [[LoadSaveController]] that starts the background operations
   *  and updates the [[AppStateStore]] with the results.
   *
   *  @param store the [[AppStateStore]] that holds the current track and receives the state updates
   *  @param reader reads the audio files
   *  @param writer writes the audio files
   */
  def make(
            store: AppStateStore,
            reader: AudioReader,
            writer: AudioWriter
          )(using runtime: IORuntime): Resource[IO, LoadSaveController] =
    for
      supervisor <- Supervisor[IO](await = false)
      importBusy <- Resource.eval(Ref.of[IO, Boolean](false))
      exportBusy <- Resource.eval(Ref.of[IO, Boolean](false))
    yield LoadSaveControllerImpl(store, reader, writer, supervisor, importBusy, exportBusy)

  /** Returns the given path ending with `.wav`, appending the extension only if it is missing.
   *
   *  @param path the path chosen by the user
   */
  def withWavExtension(path: Path): Path =
    Option(path.getFileName).map(_.toString) match
      case Some(name) if name.toLowerCase(Locale.ROOT).endsWith(".wav") => path
      case Some(name) => path.resolveSibling(name + ".wav")
      case None => path

private[controller] final class LoadSaveControllerImpl(
                                                        store: AppStateStore,
                                                        reader: AudioReader,
                                                        writer: AudioWriter,
                                                        supervisor: Supervisor[IO],
                                                        importBusy: Ref[IO, Boolean],
                                                        exportBusy: Ref[IO, Boolean]
                                                      )(using runtime: IORuntime) extends LoadSaveController:

  override def load(path: Path): Unit =
    runAsync(startIfIdle(importBusy)(runImport(path)))

  private def runAsync(task: IO[Unit]): Unit =
    supervisor.supervise(task).void.unsafeRunAndForget()

  private def startIfIdle(busy: Ref[IO, Boolean])(task: IO[Unit]): IO[Unit] =
    busy.getAndSet(true).flatMap {
      case true => IO.unit
      case false => supervisor.supervise(task.guarantee(busy.set(false))).void.onError(_ => busy.set(false))
    }

  private def runImport(path: Path): IO[Unit] =
    for
      _ <- store.dispatch(Command.UpdateImport(ImportState.Importing))
      result <- IO.blocking(reader.load(path.toString)).attempt
      _ <- store.dispatch(Command.UpdateImport(toImportState(result)))
    yield ()

  private def toImportState(result: Either[Throwable, Either[AudioLoadError, AudioTrack]]): ImportState =
    result match
      case Right(Right(track)) => ImportState.Imported(track)
      case Right(Left(error)) => ImportState.Failed(error)
      case Left(throwable) => ImportState.Failed(AudioLoadError.ReadFailure(describe(throwable)))

  private def describe(throwable: Throwable): String =
    Option(throwable.getMessage).getOrElse(throwable.getClass.getSimpleName)

  override def save(path: Path): Unit =
    runAsync {
      store.current.flatMap { state =>
        state.importState match
          case ImportState.Importing => IO.unit
          case _ => startIfIdle(exportBusy)(runExport(state.track, LoadSaveController.withWavExtension(path)))
      }
    }

  private def runExport(snapshot: Option[AudioTrack], path: Path): IO[Unit] =
    snapshot match
      case None =>
        store.dispatch(Command.UpdateExport(ExportState.Failed(AudioSaveError.NoTrackLoaded))).void
      case Some(track) =>
        for
          _ <- store.dispatch(Command.UpdateExport(ExportState.Exporting))
          result <- IO.blocking(writer.save(track, path)).attempt
          _ <- store.dispatch(Command.UpdateExport(toExportState(result, path)))
        yield ()

  private def toExportState(result: Either[Throwable, Either[AudioSaveError, Unit]], path: Path): ExportState =
    result match
      case Right(Right(_)) => ExportState.Exported
      case Right(Left(error)) => ExportState.Failed(error)
      case Left(throwable) => ExportState.Failed(AudioSaveError.WriteFailure(path, describe(throwable)))

  override def acknowledgeImport: Unit =
    runAsync {
      store.current.map(_.importState).flatMap {
        case ImportState.Imported(_) | ImportState.Failed(_) =>
          store.dispatch(Command.UpdateImport(ImportState.Idle)).void
        case _ => IO.unit
      }
    }

  override def acknowledgeExport: Unit =
    runAsync {
      store.current.map(_.exportState).flatMap {
        case ExportState.Exported | ExportState.Failed(_) =>
          store.dispatch(Command.UpdateExport(ExportState.Idle)).void
        case _ => IO.unit
      }
    }
