package scalasound.view.mainPanel

import scalafx.beans.property.FloatProperty
import scalafx.geometry.{Insets, Orientation, Pos}
import scalafx.scene.control.{Label, Slider}
import scalafx.scene.layout.{HBox, VBox}
import scalasound.model.processing.{AudioConfiguration, GainEffect}
import scalasound.model.processing.AudioEffectType.{Gain, Volume}

private[mainPanel] object VolumeGainBox:

  private def label(label: String): Label = new Label(label):
    style = """
      -fx-font-size: 16px;
    """

  private def container(text: String,
                        effectProperty: FloatProperty,
                        minVal: Float,
                        maxVal: Float,
                        defaultVal: Float): VBox = new VBox:

    private val slider: Slider = new Slider:
        min = minVal
        max = maxVal
        value = defaultVal
        orientation = Orientation.Vertical
        prefHeight = 300
        prefWidth = 20

    private val valueLabel = label(defaultVal.toString)
    valueLabel.text <== slider.value.map(v => f"${v.floatValue()}%.2f")
    effectProperty <== slider.value

    alignment = Pos.Center
    spacing = 10
    children = Seq(label(text), slider, valueLabel)
  end container

/* Box for the Volume-Gain effects. */
private[mainPanel] final class VolumeGainBox extends HBox:
  import VolumeGainBox.*

  private val gainConfig = AudioConfiguration.parameterConfiguration(Gain)
  private val _gainValue: FloatProperty = FloatProperty(gainConfig.gainConfiguration.min)
  private val gain = container(Gain.name,
    _gainValue,
    gainConfig.gainConfiguration.min,
    gainConfig.gainConfiguration.max,
    gainConfig.gainConfiguration.default)
  private val volumeConfig = AudioConfiguration.parameterConfiguration(Volume)
  private val _volumeValue: FloatProperty = FloatProperty(volumeConfig.volumeConfiguration.default)
  private val volume = container(Volume.name,
    _volumeValue,
    volumeConfig.volumeConfiguration.min,
    volumeConfig.volumeConfiguration.max,
    volumeConfig.volumeConfiguration.default)
  
  alignment = Pos.Center
  padding = Insets(10, 30, 10, 30)
  spacing = 50
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

  /**
   * @return the observer on gain value.
   */
  def gainValue: FloatProperty = _gainValue

  /**
   * @return the observer on volume value.
   */
  def volumeValue: FloatProperty = _volumeValue
