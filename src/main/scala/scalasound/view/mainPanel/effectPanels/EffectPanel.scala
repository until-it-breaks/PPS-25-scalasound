package scalasound.view.mainPanel.effectPanels

import scalafx.application.JFXApp3.PrimaryStage
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.layout.{BorderPane, HBox}

/* Container for all the effects that can be applied on the current track. */
final class EffectPanel(stage: PrimaryStage) extends BorderPane:

  private val volumeGainBox = new VolumeGainBox()
  private val distortionPanBox = new DistortionPanBox()
  private val echoAutopanBox = new EchoAutopanBox()
  private val titleBox = new HBox:
    alignment = Pos.Center
    padding = Insets(0, 0, 20, 0)
    children = Utility.label("Effects", true, 24, "white")

  padding = Insets(20)
  style = s"-fx-background-color: ${Utility.BackgroundColor};"
  top = titleBox
  right = echoAutopanBox
  center = distortionPanBox
  left = volumeGainBox
  BorderPane.setMargin(distortionPanBox, Insets(0, 15, 0, 15))

