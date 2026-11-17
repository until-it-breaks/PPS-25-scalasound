package scalasound.view.panels.effects

import scalafx.geometry.Insets
import scalafx.scene.Cursor
import scalafx.scene.control.Button
import scalafx.scene.layout.BorderPane

object EffectsPanelContainer:

  private val Title: String = "Effects"
  
  private def resetAllButton: Button = new Button("Reset All"):
    cursor = Cursor.Hand
    padding = Insets(5, 10, 5, 10)
    style =
      """
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
  
  private def titleBox(button: Button) = new BorderPane:
    padding = Insets(0, 0, 20, 0)
    center = Utility.label(Title, true, 24, "white")
    right = button

/**
 * Container for all the effects that can be applied on the current track.
 */
final class EffectsPanelContainer extends BorderPane:
  import EffectsPanelContainer.*

  val volumeGainBox: VolumeGainBox = VolumeGainBox()
  val distortionPanBox: DistortionPanBox = DistortionPanBox()
  val echoAutopanBox: EchoAutopanBox = EchoAutopanBox()
  val resetAllButton: Button = EffectsPanelContainer.resetAllButton

  padding = Insets(20)
  style = s"-fx-background-color: ${Utility.BackgroundColor};"
  top = titleBox(resetAllButton)
  right = echoAutopanBox
  center = distortionPanBox
  left = volumeGainBox
  BorderPane.setMargin(distortionPanBox, Insets(0, 15, 0, 15))