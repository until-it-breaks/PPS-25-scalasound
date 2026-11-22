package scalasound.view.panels.loadsave

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import scalafx.CoreIncludes.jfxWindow2sfx
import scalafx.application.Platform
import scalafx.scene.control.Alert
import scalafx.scene.control.Alert.AlertType
import scalafx.stage.Window
import scalasound.controller.LoadSaveController
import scalasound.model.state.{AppStateStore, ExportState, ImportState}

import java.nio.file.Path

/** Keeps a [[TitlePane]] in sync with the import and export states of an [[AppStateStore]]
 * and forwards user actions to a [[LoadSaveController]].
 */
trait LoadSaveLogic:

  /** Connects the import and export buttons of the given pane to the controller.
   *
   * @param pane
   *   the title pane with the import and export buttons
   */
  def attach(pane: TitlePane): Unit

  /** Subscribes to import and export state changes and updates the attached [[TitlePane]]
   * on the GUI thread for each one.
   *
   * @return
   *   an effect that completes when both state streams end
   */
  def observe: IO[Unit]

object LoadSaveLogic:

  /** Creates a [[LoadSaveLogic]] that synchronises a [[TitlePane]] with the given
   * [[AppStateStore]] and delegates import/export actions to the [[LoadSaveController]].
   *
   * @param store
   *   source of import and export state changes
   * @param controller
   *   receives the import, export and acknowledgement requests
   * @param runOnGUIThread
   *   schedules GUI updates on the GUI thread
   * @param showAlert
   *   displays modal notifications to the user
   */
  def apply(
             store: AppStateStore,
             controller: LoadSaveController,
             runOnGUIThread: (=> Unit) => Unit = update => Platform.runLater(() => update),
             showAlert: (AlertType, String, String) => Unit = defaultShowAlert
           )(using IORuntime): LoadSaveLogic =
    LoadSaveLogicImpl(store, controller, runOnGUIThread, showAlert)

  private def defaultShowAlert(alertType: AlertType, alertTitle: String, message: String): Unit =
    val alert = new Alert(alertType):
      title = alertTitle
      headerText = alertTitle
      contentText = message
    alert.showAndWait()
    ()

private final class LoadSaveLogicImpl(
                                       store: AppStateStore,
                                       controller: LoadSaveController,
                                       runOnGUIThread: (=> Unit) => Unit,
                                       showAlert: (AlertType, String, String) => Unit
                                     )(using IORuntime) extends LoadSaveLogic:

  private var titlePane: Option[TitlePane] = None
  private var importInProgress: Boolean = false
  private var exportInProgress: Boolean = false

  override def attach(pane: TitlePane): Unit =
    titlePane = Some(pane)
    refreshView()

    pane.importButton.onAction = _ =>
      chooseOpenFile(pane).foreach(controller.load(_).unsafeRunAndForget())

    pane.exportButton.onAction = _ =>
      chooseSaveFile(pane).foreach(controller.save(_).unsafeRunAndForget())

  override def observe: IO[Unit] =
    val imports = store.importStateChanges.evalMap(state => IO(runOnGUIThread(handleImport(state))))
    val exports = store.exportStateChanges.evalMap(state => IO(runOnGUIThread(handleExport(state))))
    imports.merge(exports).compile.drain

  private def handleImport(importState: ImportState): Unit =
    importInProgress = importState == ImportState.Importing
    refreshView()

    importState match
      case ImportState.Imported(_) =>
        showAlert(AlertType.Information, "Import completed", "The track has been imported.")
        controller.acknowledgeImport.unsafeRunAndForget()
      case ImportState.Failed(error) =>
        showAlert(AlertType.Error, "Import failed", ErrorMessages.describe(error))
        controller.acknowledgeImport.unsafeRunAndForget()
      case _ => ()

  private def handleExport(exportState: ExportState): Unit =
    exportInProgress = exportState == ExportState.Exporting
    refreshView()

    exportState match
      case ExportState.Exported =>
        showAlert(AlertType.Information, "Export completed", "The track has been exported.")
        controller.acknowledgeExport.unsafeRunAndForget()
      case ExportState.Failed(error) =>
        showAlert(AlertType.Error, "Export failed", ErrorMessages.describe(error))
        controller.acknowledgeExport.unsafeRunAndForget()
      case _ => ()

  private def refreshView(): Unit =
    titlePane.foreach { pane =>
      pane.importButton.disable = importInProgress
      pane.exportButton.disable = importInProgress || exportInProgress
      pane.statusLabel.text = (importInProgress, exportInProgress) match
        case (true, _) => "Importing"
        case (_, true) => "Exporting"
        case _         => ""
    }

  private def chooseOpenFile(pane: TitlePane): Option[Path] =
    Option(TitlePane.fileChooserConfig.showOpenDialog(ownerWindow(pane))).map(_.toPath)

  private def chooseSaveFile(pane: TitlePane): Option[Path] =
    Option(TitlePane.saveChooserConfig.showSaveDialog(ownerWindow(pane))).map(_.toPath)

  private def ownerWindow(pane: TitlePane): Window =
    Option(pane.delegate.getScene).map(_.getWindow).orNull
