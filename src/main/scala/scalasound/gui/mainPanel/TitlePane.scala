package scalasound.gui.mainPanel

import scalafx.application.JFXApp3.PrimaryStage
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.Cursor
import scalafx.scene.control.{Button, Label, Slider}
import scalafx.scene.layout.BorderPane
import scalafx.stage.FileChooser

import java.io.File

private[mainPanel] object TitlePane:
  private val BackgroundColor = "#4287f5"

  private def titleLabel = new Label("ScalaSound"):
    style = """
      -fx-text-fill: #ecf0f1;
      -fx-font-size: 30px;
      -fx-font-weight: bold;
    """

  private def importButton = new Button("Import"):
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
  
/*
 * Container for the upper panel of the GUI,
 * containing the Title and the Import button.
 */
private[mainPanel] final class TitlePane(stage: PrimaryStage) extends BorderPane:
  import TitlePane._

  private val impButton = importButton
  
  impButton.onAction = _ =>
    val chooser = fileChooserConfig
    val selectedFile: File = chooser.showOpenDialog(stage)
    if (selectedFile != null) then
      // TODO: Complete
      println("File" + selectedFile.getName)

  padding = Insets(25)
  style = s"-fx-background-color: ${BackgroundColor};"
  left = titleLabel
  right = importButton
  BorderPane.setAlignment(titleLabel, Pos.CenterLeft)
  BorderPane.setAlignment(importButton, Pos.CenterRight)

end TitlePane