package scalasound.view.loadsave

import cats.effect.IO
import scalafx.application.Platform
import scalasound.model.state.{AppStateStore, ExportState, ImportState}

/** Observes the import and export states of an [[AppStateStore]] and updates a [[LoadSaveControls]] accordingly. */
trait LoadSaveObserver:

  /** Subscribes to the import and export state changes and updates the view for each one.
   *
   *  @return an effect that completes when both state streams end
   */
  def observe: IO[Unit]

final class LoadSaveObserverImpl(
                                  appStateStore: AppStateStore,
                                  loadSaveControls: LoadSaveControls
                                ) extends LoadSaveObserver:

  override def observe: IO[Unit] =
    appStateStore.importStateChanges
      .evalMap(state => onFx(showImport(state)))
      .merge(appStateStore.exportStateChanges.evalMap(state => onFx(showExport(state))))
      .compile
      .drain

  private def onFx(update: => Unit): IO[Unit] =
    IO(Platform.runLater(() => update))

  private def showImport(importState: ImportState): Unit = importState match
    case ImportState.Idle            => loadSaveControls.importIdle()
    case ImportState.Importing       => loadSaveControls.importStarted()
    case ImportState.Imported(track) => loadSaveControls.importCompleted(track)
    case ImportState.Failed(error)   => loadSaveControls.importFailed(error)

  private def showExport(exportState: ExportState): Unit = exportState match
    case ExportState.Idle          => loadSaveControls.exportIdle()
    case ExportState.Exporting     => loadSaveControls.exportStarted()
    case ExportState.Exported      => loadSaveControls.exportCompleted()
    case ExportState.Failed(error) => loadSaveControls.exportFailed(error)
