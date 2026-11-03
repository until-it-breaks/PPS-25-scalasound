package scalasound.gui.mainPanel

import scalafx.geometry.{Insets, Orientation, Pos}
import scalafx.scene.control.{Label, Slider}
import scalafx.scene.layout.{HBox, VBox}

// TODO: AGGIUNGERE COMMENTI
private[mainPanel] object VolumeGainBox:
  
  private def label(label: String): Label = new Label(label):
    style = """
      -fx-font-size: 16px;
    """
  
  private def container(text: String,
                        minVal: Float,
                        maxVal: Float,
                        defaultVal: Float): VBox = new VBox:

    private val slider: Slider =
      new Slider {
        min = minVal
        max = maxVal
        value = defaultVal
        orientation = Orientation.Vertical
        prefHeight = 300
        prefWidth = 20
      }

    private val valueLabel = label(defaultVal.toString)
    valueLabel.text <== slider.value.map(v => f"${v.floatValue()}%.2f")

    alignment = Pos.Center
    spacing = 10
    children = Seq(label(text), slider, valueLabel)
  end container

/* Box for the Volume-Gain effects. */
private[mainPanel] final class VolumeGainBox extends HBox:
  import VolumeGainBox.*
  
  private val gain = container("Gain", 0, 0, 0) // TODO: CAPIRE COME COLLEGARLI AI COMPANION OBJECT
  private val volume = container("Volume", 0, 0, 0)
  
  alignment = Pos.Center
  padding = Insets(10, 20, 10, 20)
  spacing = 20
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
  children = Seq(gain, volume)
