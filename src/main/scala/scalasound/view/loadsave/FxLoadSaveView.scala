package scalasound.view.loadsave

import cats.effect.unsafe.IORuntime
import scalafx.beans.property.{BooleanProperty, DoubleProperty, StringProperty}
import scalafx.scene.control.Alert
import scalafx.scene.control.Alert.AlertType
import scalasound.controller.IOController
import scalasound.model.domain.AudioTrack
import scalasound.model.io.{AudioLoadError, AudioSaveError}

/** ScalaFX implementation of [[LoadSaveView]].
 *
 *  @param controller acknowledges the results once the user has seen them
 */
class FxLoadSaveView(controller: IOController)(using IORuntime) extends LoadSaveView:

  private val importInProgress: BooleanProperty = BooleanProperty(false)
  private val exportBlocked: BooleanProperty = BooleanProperty(false)
  private val exportInProgress: BooleanProperty = BooleanProperty(false)
  private val operationStatusText: StringProperty = StringProperty("")
  private val trackDurationSeconds: DoubleProperty = DoubleProperty(0.0)

  override def importIdle(): Unit =
    setImportInProgress(false)
    operationStatusText.value = ""

  override def importStarted(): Unit =
    setImportInProgress(true)
    operationStatusText.value = "Importing"

  override def importCompleted(track: AudioTrack): Unit =
    setImportInProgress(false)
    operationStatusText.value = f"Imported a track of ${track.durationInSeconds}%.1f s"
    trackDurationSeconds.value = track.durationInSeconds

  override def importFailed(error: AudioLoadError): Unit =
    setImportInProgress(false)
    operationStatusText.value = ""
    showAndWait(AlertType.Error, "Import failed", ErrorMessages.describe(error))
    controller.acknowledgeImport.unsafeRunAndForget()

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
    controller.acknowledgeExport.unsafeRunAndForget()

  override def exportFailed(error: AudioSaveError): Unit =
    setExportInProgress(false)
    operationStatusText.value = ""
    showAndWait(AlertType.Error, "Export failed", ErrorMessages.describe(error))
    controller.acknowledgeExport.unsafeRunAndForget()

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
