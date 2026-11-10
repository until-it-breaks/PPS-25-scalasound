package scalasound.view.loadsave

import scalafx.beans.property.{BooleanProperty, StringProperty}
import scalafx.scene.control.Alert
import scalafx.scene.control.Alert.AlertType
import scalasound.controller.LoadSaveController
import scalasound.model.domain.AudioTrack
import scalasound.model.io.{AudioLoadError, AudioSaveError}

/** What the user sees of the import and export operations. */
trait LoadSaveControls:

  val exportInProgress: BooleanProperty = BooleanProperty(false)
  val importInProgress: BooleanProperty = BooleanProperty(false)
  val exportBlocked: BooleanProperty = BooleanProperty(false)
  val operationStatusText: StringProperty = StringProperty("")

  /** No import is in progress. */
  def importIdle(): Unit

  /** An import has started. */
  def importStarted(): Unit

  /** An import has completed.
   *
   *  @param track the loaded track
   */
  def importCompleted(track: AudioTrack): Unit

  /** An import has failed.
   *
   *  @param error the reason for the failure
   */
  def importFailed(error: AudioLoadError): Unit

  /** No export is in progress. */
  def exportIdle(): Unit

  /** An export has started. */
  def exportStarted(): Unit

  /** An export has completed. */
  def exportCompleted(): Unit

  /** An export has failed.
   *
   *  @param error the reason for the failure
   */
  def exportFailed(error: AudioSaveError): Unit

/** ScalaFX implementation of [[LoadSaveControls]].
 *
 *  @param controller acknowledges the results once the user has seen them
 */
final class LoadSaveControlsImpl(controller: LoadSaveController) extends LoadSaveControls:

  override def importIdle(): Unit =
    setImportInProgress(false)
    operationStatusText.value = ""

  override def importStarted(): Unit =
    setImportInProgress(true)
    operationStatusText.value = "Importing"

  override def importCompleted(track: AudioTrack): Unit =
    setImportInProgress(false)
    operationStatusText.value = ""
    showAndWait(AlertType.Information, "Import completed", "The track has been imported.")
    controller.acknowledgeImport

  override def importFailed(error: AudioLoadError): Unit =
    setImportInProgress(false)
    operationStatusText.value = ""
    showAndWait(AlertType.Error, "Import failed", ErrorMessages.describe(error))
    controller.acknowledgeImport

  override def exportIdle(): Unit =
    setExportInProgress(false)
    operationStatusText.value = ""

  override def exportStarted(): Unit =
    setExportInProgress(true)
    operationStatusText.value = "Exporting"

  override def exportCompleted(): Unit =
    setExportInProgress(false)
    operationStatusText.value = ""
    showAndWait(AlertType.Information, "Export completed", "The track has been exported.")
    controller.acknowledgeExport

  override def exportFailed(error: AudioSaveError): Unit =
    setExportInProgress(false)
    operationStatusText.value = ""
    showAndWait(AlertType.Error, "Export failed", ErrorMessages.describe(error))
    controller.acknowledgeExport

  private def setImportInProgress(value: Boolean): Unit =
    importInProgress.value = value
    refreshExportBlocked()

  private def setExportInProgress(value: Boolean): Unit =
    exportInProgress.value = value
    refreshExportBlocked()

  private def refreshExportBlocked(): Unit =
    exportBlocked.value = importInProgress.value || exportInProgress.value

  private def showAndWait(alertType: AlertType, alertTitle: String, message: String): Unit =
    val alert = new Alert(alertType):
      title = alertTitle
      headerText = alertTitle
      contentText = message
    alert.showAndWait()
    ()
