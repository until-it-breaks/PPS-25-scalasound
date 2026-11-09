package scalasound.view.mainPanel

import scalafx.application.JFXApp3.PrimaryStage
import scalafx.geometry.Pos
import scalafx.scene.Cursor
import scalafx.scene.control.{Button, Label, Slider}
import scalafx.scene.layout.{HBox, VBox}

private[mainPanel] object TrackBox:

  private def formatTime(value: Int): String =
    f"${value / 60}%d:${value % 60}%02d"

  private def audioLabel: Label = new Label("-:--"):
    style = """
       -fx-font-size: 20px;
    """

  private def createAudioButton(text: String): Button = new Button(text):
    cursor = Cursor.Hand
    //padding = Insets(5, 10, 5, 10)
    prefWidth = 70
    prefHeight = 40
    style = """
      -fx-background-color: #3c6382;
      -fx-text-fill: white;
      -fx-font-size: 20px;
      -fx-font-weight: bold;
      -fx-border-color: #60a3bc;
      -fx-border-width: 2px;
      -fx-border-radius: 5px;
      -fx-background-radius: 5px;
      -fx-alignment: center;
    """
  
/*
 * Container for the track and for the buttons that
 * manage the track itself (Restart, Stop, Play, Save).
 */
private[mainPanel] final class TrackBox(stage: PrimaryStage, timeCursor: Slider) extends VBox:
  import TrackBox.*
  
  private val startTimeLabel = audioLabel
  private val endTimeLabel = audioLabel

  startTimeLabel.text <== timeCursor.value.map(v => formatTime(v.intValue()))
  endTimeLabel.text <== timeCursor.max.map(m => formatTime(m.intValue()))

  /* Box containing the slider for the track. */
  private val audioBox: HBox = new HBox:
      alignment = Pos.Center
      spacing = 10
      children = Seq(startTimeLabel, timeCursor, endTimeLabel)

  /* Box containing the buttons to manage the track. */
  private val audioButtonsBox: HBox = new HBox:

      private val restartButton = createAudioButton("◀◀")
      restartButton.onAction = _ => println("Restart")

      private val stopButton = createAudioButton("■")
      stopButton.onAction = _ => println("Stop")

      private val playButton = createAudioButton("▶")
      playButton.onAction = _ => println("Play")

      private val saveButton = createAudioButton("\uD83D\uDCBE")
      saveButton.onAction = _ => println("Save")

      alignment = Pos.Center
      spacing = 10
      children = Seq(
        restartButton,
        stopButton,
        playButton,
        saveButton)

  alignment = Pos.Center
  spacing = 30
  children = Seq(audioBox, audioButtonsBox)
