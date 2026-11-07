package scalasound.view.mainPanel

import scalafx.application.JFXApp3.PrimaryStage
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.control.Label
import scalafx.scene.layout.{BorderPane, HBox, VBox}

/* Container for all the effects that can be applied on the current track. */
private[mainPanel] final class EffectPane(stage: PrimaryStage) extends BorderPane:

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

