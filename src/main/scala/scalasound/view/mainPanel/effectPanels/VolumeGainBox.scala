package scalasound.view.mainPanel.effectPanels

import scalafx.geometry.{Insets, Pos}
import scalafx.scene.layout.HBox
import scalasound.model.processing.{AudioConfiguration, AudioEffect}
import scalasound.model.processing.AudioEffectType.{Gain, Volume}

/* Box for the Volume-Gain effects. */
private[effectPanels] final class VolumeGainBox(using effectControls: EffectsControls) extends HBox with EffectPane:

  /* Importing of the Gain parameter values. */
  private val gainConfig = AudioConfiguration.parameterConfiguration(Gain)
  private val gain = Utility.effectContainer(Gain.name,
    gainConfig.gainConfiguration,
    v => effectControls.setEffect(AudioEffect.gain(v)),
    v => f"${v}%.2f",
    isHorizontal = false)

  /* Importing of the Volume parameter values. */
  private val volumeConfig = AudioConfiguration.parameterConfiguration(Volume)
  private val volume = Utility.effectContainer(Volume.name,
    volumeConfig.volumeConfiguration,
    v => effectControls.setEffect(AudioEffect.volume(v)),
    v => f"${v * 100}%.2f%%",
    isHorizontal = false)

  alignment = Pos.Center
  padding = Insets(10, 30, 10, 30)
  spacing = 50
  children = Seq(gain.panel, volume.panel)

  override def resetSlidersAndValues(): Unit =
    gain.slider.value = gainConfig.gainConfiguration.default
    volume.slider.value = volumeConfig.volumeConfiguration.default