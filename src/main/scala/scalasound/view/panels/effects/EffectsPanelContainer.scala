package scalasound.view.panels.effects

import scalafx.application.JFXApp3.PrimaryStage
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.Cursor
import scalafx.scene.control.Button
import scalafx.scene.layout.{BorderPane, HBox}
import scalasound.controller.EffectsController

object EffectsPanelContainer:

  private val resetAllButton = new Button("Reset All"):
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
  
  private val titleBox = new BorderPane:
    padding = Insets(0, 0, 20, 0)
    center = Utility.label("Effects", true, 24, "white")
    right = resetAllButton

/**
 * Container for all the effects that can be applied on the current track.
 */
final class EffectsPanelContainer(stage: PrimaryStage,
                        effectsControls: EffectsControls) extends BorderPane:
  import EffectsPanelContainer.*
  
  private given EffectsControls = effectsControls
  private val volumeGainBox = VolumeGainBox()
  private val distortionPanBox = DistortionPanBox()
  private val echoAutopanBox = EchoAutopanBox()
  
  resetAllButton.onAction = _ =>
    effectsControls.removeAll()
    volumeGainBox.resetSlidersAndValues()
    distortionPanBox.resetSlidersAndValues()
    echoAutopanBox.resetSlidersAndValues()

  padding = Insets(20)
  style = s"-fx-background-color: ${Utility.BackgroundColor};"
  top = titleBox
  right = echoAutopanBox
  center = distortionPanBox
  left = volumeGainBox
  BorderPane.setMargin(distortionPanBox, Insets(0, 15, 0, 15))