package scalasound.view.panels.effects


import scalafx.geometry.{Insets, Pos}
import scalafx.scene.layout.VBox
import scalasound.model.processing.{AudioConfiguration, AudioEffect}
import scalasound.model.processing.AudioEffectType.{Autopan, Echo}

/* Box fot the Echo-Autopan effects. */
private[effects] final class EchoAutopanBox(using effectControls: EffectsControls) extends VBox with EffectPane:

  private val FontSizeParameter = 18
  private val DefaultSampleRate = 44100f

  /* Importing of the Echo parameters values. */
  private val echoConfig = AudioConfiguration.parameterConfiguration(Echo)
  private val alpha = Utility.effectContainer("Alpha",
    echoConfig.alphaConfiguration,
    _ => updateEcho(),
    v => f"${v}%.2f",
    titleSize = FontSizeParameter)

  private val delay = Utility.effectContainer("Delay",
    echoConfig.delayConfiguration,
    _ => updateEcho(),
    v => s"${v.intValue()}ms",
    titleSize = FontSizeParameter)

  private val echo = new VBox:
    alignment = Pos.Center
    spacing = 20
    children = Seq(
      Utility.label(Echo.name, true, Utility.TitleEffectSize),
      alpha.panel, delay.panel)

  private val autopanConfig = AudioConfiguration.parameterConfiguration(Autopan)
  private val autopan = Utility.effectContainer(Autopan.name,
    autopanConfig.autopanConfiguration,
    v => effectControls.setEffect(AudioEffect.autopan(v, DefaultSampleRate)),
    v => f"${v}%.2f Hz")

  alignment = Pos.Center
  padding = Insets(10, 20, 10, 20)
  spacing = 50
  children = Seq(echo, autopan.panel)

  private def updateEcho(): Unit =
    effectControls.setEffect(
      AudioEffect.echo(
        alpha.slider.value.value.toFloat,
        delay.slider.value.value.toInt,
        DefaultSampleRate
      )
    )

  override def resetSlidersAndValues(): Unit =
    alpha.slider.value = echoConfig.alphaConfiguration.default
    delay.slider.value = echoConfig.delayConfiguration.default
    autopan.slider.value = autopanConfig.autopanConfiguration.default