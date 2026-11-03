package scalasound.gui.mainPanel

import scalafx.application.JFXApp3.PrimaryStage
import scalafx.geometry.Insets
import scalafx.scene.layout.{BorderPane, VBox}

/* Container for all the effects that can be applied on the current track. */
private[gui] final class EffectPane(stage: PrimaryStage) extends BorderPane:

  padding = Insets(20)
  style = "-fx-background-color: #4287f5;"
  right = new VolumeGainBox()
  left = new DistortionPanBox()
  center = new VBox()
