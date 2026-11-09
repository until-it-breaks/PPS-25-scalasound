package scalasound.view.mainPanel.effectPanels

import scalafx.beans.property.{FloatProperty, ReadOnlyFloatProperty}
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.control.Slider
import scalafx.scene.layout.{HBox, VBox}
import scalasound.model.processing.AudioConfiguration
import scalasound.model.processing.AudioEffectType.{Gain, Volume}

private[effectPanels] object VolumeGainBox:

  private def container(text: String,
                        effectProperty: FloatProperty,
                        minVal: Float,
                        maxVal: Float,
                        defaultVal: Float,
                        metrics: Float => String): VBox = new VBox:

    private val slider: Slider = Utility.slider(minVal, maxVal, defaultVal, false)
    private val valueLabel = Utility.label(defaultVal.toString)
    valueLabel.text <== slider.value.map(v => metrics(v.floatValue()))
    effectProperty <== slider.value

    alignment = Pos.Center
    spacing = 10
    children = Seq(Utility.label(text, true, Utility.TitleEffectSize), slider, valueLabel)

/* Box for the Volume-Gain effects. */
private[effectPanels] final class VolumeGainBox extends HBox:
  import VolumeGainBox.*

  /* Importing of the Gain parameter values. */
  private val gainConfig = AudioConfiguration.parameterConfiguration(Gain)
  private val _gainValue: FloatProperty = FloatProperty(gainConfig.gainConfiguration.default)
  private val gain = container(Gain.name,
    _gainValue,
    gainConfig.gainConfiguration.min,
    gainConfig.gainConfiguration.max,
    gainConfig.gainConfiguration.default,
    v => f"${v}%.2f")

  /* Importing of the Volume parameter values. */
  private val volumeConfig = AudioConfiguration.parameterConfiguration(Volume)
  private val _volumeValue: FloatProperty = FloatProperty(volumeConfig.volumeConfiguration.default)
  private val volume = container(Volume.name,
    _volumeValue,
    volumeConfig.volumeConfiguration.min,
    volumeConfig.volumeConfiguration.max,
    volumeConfig.volumeConfiguration.default,
    v => f"${v * 100}%.2f%%")
  
  alignment = Pos.Center
  padding = Insets(10, 30, 10, 30)
  spacing = 50
  style = """
        -fx-background-color: white;
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
  def gainValue: ReadOnlyFloatProperty = _gainValue

  /**
   * @return the observer on volume value.
   */
  def volumeValue: ReadOnlyFloatProperty = _volumeValue
