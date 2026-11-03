package scalasound.view.mainPanel

import scalafx.application.JFXApp3.PrimaryStage
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.control.Label
import scalafx.scene.layout.{BorderPane, HBox, VBox}

/* Container for all the effects that can be applied on the current track. */
private[mainPanel] final class EffectPane(stage: PrimaryStage) extends BorderPane:

  private val titleLabel = new Label("Effects"):
    style = """
      -fx-text-fill: #ecf0f1;
      -fx-font-size: 24px;
      -fx-font-weight: bold;
    """
  private val titleBox = new HBox:
    alignment = Pos.Center
    padding = Insets(0, 0, 20, 0)
    children = titleLabel

  padding = Insets(20)
  style = "-fx-background-color: #4287f5;"
  top = titleBox
  right = new VolumeGainBox()
  left = new DistortionPanBox()
  center = new EchoAutopanBox()

