package scalasound.view.mainPanel

import cats.effect.unsafe.IORuntime
import scalafx.application.JFXApp3.PrimaryStage
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.Cursor
import scalafx.scene.control.{Button, Label, Slider}
import scalafx.scene.layout.{BorderPane, HBox}
import scalafx.stage.FileChooser
import scalasound.controller.IOController
import scalasound.view.loadsave.FxLoadSaveView

private[mainPanel] object TitlePane:
  private val BackgroundColor = "#4287f5"

  private def titleLabel = new Label("ScalaSound"):
    style = """
      -fx-text-fill: #ecf0f1;
      -fx-font-size: 30px;
      -fx-font-weight: bold;
    """

  private def createButton(text: String): Button = new Button(text):
    cursor = Cursor.Hand
    padding = Insets(5, 10, 5, 10)
    style = """
      -fx-background-color: white;
      -fx-text-fill: black;
      -fx-font-size: 16px;
      -fx-font-weight: bold;
      -fx-border-color: black;
      -fx-border-width: 2px;
      -fx-border-radius: 5px;
      -fx-background-radius: 5px;
      -fx-alignment: center;
    """

  private def fileChooserConfig = new FileChooser:
    title = "Choose .wav file"
    extensionFilters.add(new FileChooser.ExtensionFilter("Audio Files", "*.wav"))

  private def saveChooserConfig = new FileChooser:
    title = "Export as .wav file"
    initialFileName = "track.wav"
    extensionFilters.add(new FileChooser.ExtensionFilter("Audio Files", "*.wav"))
  
/*
 * Container for the upper panel of the GUI,
 * containing the Title, the Import button and the status of the import/export operations.
 */
private[mainPanel] final class TitlePane(stage: PrimaryStage, controller: IOController, view: FxLoadSaveView)
                                        (using IORuntime) extends BorderPane:
  import TitlePane.*

  private val statusLabel = new Label:
    style = """
      -fx-text-fill: #ecf0f1;
      -fx-font-size: 16px;
    """
  private val importButton = createButton("Import")
  private val exportButton = createButton("Export")
  private val buttonContainer: HBox = new HBox:
    spacing = 10
    children = Seq(importButton, exportButton)

  statusLabel.text <== view.operationStatusText
  importButton.disable <== view.importInProgress
  exportButton.disable <== view.exportBlocked

  importButton.onAction = _ =>
    Option(fileChooserConfig.showOpenDialog(stage))
      .foreach(file => controller.load(file.toPath).unsafeRunAndForget())
  exportButton.onAction = _ =>
    Option(saveChooserConfig.showSaveDialog(stage))
      .foreach(file => controller.save(file.toPath).unsafeRunAndForget())

  padding = Insets(25)
  style = s"-fx-background-color: ${BackgroundColor};"
  left = titleLabel
  center = statusLabel
  right = buttonContainer
  BorderPane.setAlignment(titleLabel, Pos.CenterLeft)
  BorderPane.setAlignment(statusLabel, Pos.Center)
  BorderPane.setAlignment(buttonContainer, Pos.CenterRight)

end TitlePane
