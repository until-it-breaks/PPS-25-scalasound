package scalasound.view.panels.effects

import scalafx.geometry.{Insets, Pos}
import scalafx.scene.control.Slider
import scalafx.scene.layout.HBox
import scalasound.model.processing.AudioConfiguration
import scalasound.model.processing.AudioEffectType.{Gain, Volume}

/* Box for the Volume-Gain effects. */
private[effects] final class VolumeGainBox extends HBox with EffectPane:

  /* Importing of the Gain and Volume parameter values. */
  private val gainConfig = AudioConfiguration.parameterConfiguration(Gain)
  private val volumeConfig = AudioConfiguration.parameterConfiguration(Volume)

  private val gainContainer = Utility.effectContainer(Gain.name,
    gainConfig.gainConfiguration,
    v => f"$v%.2f",
    isHorizontal = false)

  private val volumeContainer = Utility.effectContainer(Volume.name,
    volumeConfig.volumeConfiguration,
    v => f"${v * 100}%.2f%%",
    isHorizontal = false)

  alignment = Pos.Center
  padding = Insets(10, 30, 10, 30)
  spacing = 50
  children = Seq(gainContainer.panel, volumeContainer.panel)

  override def resetSlidersAndValues(): Unit =
    gainContainer.slider.value = gainConfig.gainConfiguration.default
    volumeContainer.slider.value = volumeConfig.volumeConfiguration.default

  /**
    * @return the slider of Gain parameter.
    */
  def gainSlider: Slider = gainContainer.slider

  /**
   * @return the slider of Volume parameter.
   */
  def volumeSlider: Slider = volumeContainer.slider