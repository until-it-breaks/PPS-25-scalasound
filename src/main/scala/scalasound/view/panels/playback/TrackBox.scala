package scalasound.view.panels.playback

import scalafx.geometry.{Insets, Pos}
import scalafx.scene.Cursor
import scalafx.scene.control.{Button, Label, Slider}
import scalafx.scene.layout.{HBox, VBox}

private[panels] object TrackBox:

  /** Text shown in the time labels when no track is loaded. */
  val EmptyTime: String = "--:--"

  /** Slider range when no track is loaded. */
  val EmptyDuration: Double = 1.0

  /** Slider position when no track is loaded. */
  val EmptyPosition: Double = 0.0

  private def createTimeLabel: Label = new Label(EmptyTime):
    style = """
       -fx-font-size: 20px;
    """

  private def createAudioButton(text: String): Button = new Button(text):
    cursor = Cursor.Hand
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

/** Container for the timeline of the track and the buttons that manage the playback (Play, Pause,
  * Stop).
  */
private[panels] final class TrackBox extends VBox:
  import TrackBox.*

  val timeLine: Slider      = new Slider(0, EmptyDuration, EmptyPosition) { prefWidth = 500 }
  val startTimeLabel: Label = createTimeLabel
  val endTimeLabel: Label   = createTimeLabel
  val playButton: Button    = createAudioButton("Play")
  val pauseButton: Button   = createAudioButton("Pause")
  val stopButton: Button    = createAudioButton("Stop")

  private val timelineBox = new HBox:
    alignment = Pos.Center
    spacing = 10
    children = Seq(startTimeLabel, timeLine, endTimeLabel)

  private val buttonsBox = new HBox:
    alignment = Pos.Center
    spacing = 10
    children = Seq(playButton, pauseButton, stopButton)

  alignment = Pos.Center
  spacing = 30
  padding = Insets(20)
  children = Seq(timelineBox, buttonsBox)
