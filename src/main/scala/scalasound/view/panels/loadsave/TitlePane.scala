package scalasound.view.panels.loadsave

import scalafx.geometry.{Insets, Pos}
import scalafx.scene.Cursor
import scalafx.scene.control.{Button, Label}
import scalafx.scene.layout.{BorderPane, HBox}
import scalafx.stage.FileChooser

private[panels] object TitlePane:
  private val BackgroundColor = "#4287f5"

  private def titleLabel: Label = new Label("ScalaSound"):
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

  def fileChooserConfig: FileChooser = new FileChooser:
    title = "Choose .wav file"
    extensionFilters.add(new FileChooser.ExtensionFilter("Audio Files", "*.wav"))

  def saveChooserConfig: FileChooser = new FileChooser:
    title = "Export as .wav file"
    initialFileName = "track.wav"
    extensionFilters.add(new FileChooser.ExtensionFilter("Audio Files", "*.wav"))

private[panels] final class TitlePane extends BorderPane:
  import TitlePane.*

  val statusLabel: Label = new Label:
    style = """
      -fx-text-fill: #ecf0f1;
      -fx-font-size: 16px;
    """
  val importButton: Button = createButton("Import")
  val exportButton: Button = createButton("Export")

  private val buttonContainer: HBox = new HBox:
    spacing = 10
    children = Seq(importButton, exportButton)

  padding = Insets(25)
  style = s"-fx-background-color: $BackgroundColor;"
  left = titleLabel
  center = statusLabel
  right = buttonContainer
  BorderPane.setAlignment(titleLabel, Pos.CenterLeft)
  BorderPane.setAlignment(statusLabel, Pos.Center)
  BorderPane.setAlignment(buttonContainer, Pos.CenterRight)
