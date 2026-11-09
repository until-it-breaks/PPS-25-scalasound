package scalasound.view.loadsave

import cats.effect.IO
import scalafx.application.Platform
import scalasound.model.state.{AppStateStore, ExportState, ImportState}

/** Observes the import and export states of an [[AppStateStore]] and updates a [[LoadSaveView]] accordingly. */
trait LoadSaveObserver:

  /** Subscribes to the import and export state changes and updates the view for each one.
   *
   *  @return an effect that completes when both state streams end
   */
  def observe: IO[Unit]

object LoadSaveObserver:

  def apply(store: AppStateStore, view: LoadSaveView): LoadSaveObserver =
    LoadSaveObserverImpl(store, view, action => Platform.runLater(() => action()))

/** Keeps a [[LoadSaveView]] in sync with the import and export states of the [[AppStateStore]].
 *
 *  Every change of the import or export state is translated into a call
 *  on the view on its thread.
 *
 *  The store is the source of truth: only the current state is observed, so a state
 *  that is replaced by another, before it can be read, may be skipped: this is not a problem,
 *  because `Exported` and `Failed` are saved until they are acknowledged.
 *
 *  @param store source of the import and export [[AppState]] changes
 *  @param view the view to update with the current import and export states
 *  @param runOnFx a function that runs the given action on the JavaFX thread
 */
private[loadsave] final class LoadSaveObserverImpl(
                        store: AppStateStore,
                        view: LoadSaveView,
                        runOnFx: (() => Unit) => Unit
                      ) extends LoadSaveObserver:

  override def observe: IO[Unit] =
    store.importStateChanges
      .evalMap(state => onFx(showImport(state)))
      .merge(store.exportStateChanges.evalMap(state => onFx(showExport(state))))
      .compile
      .drain

  private def onFx(update: => Unit): IO[Unit] =
    IO(runOnFx(() => update))

  private def showImport(state: ImportState): Unit =
    state match
      case ImportState.Idle => view.importIdle()
      case ImportState.Importing => view.importStarted()
      case ImportState.Imported(track) => view.importCompleted(track)
      case ImportState.Failed(error) => view.importFailed(error)

  private def showExport(state: ExportState): Unit =
    state match
      case ExportState.Idle => view.exportIdle()
      case ExportState.Exporting => view.exportStarted()
      case ExportState.Exported => view.exportCompleted()
      case ExportState.Failed(error) => view.exportFailed(error)
