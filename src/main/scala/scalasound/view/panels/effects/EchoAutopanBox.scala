package scalasound.view.panels.effects

import scalafx.geometry.{Insets, Pos}
import scalafx.scene.control.Slider
import scalafx.scene.layout.VBox
import scalasound.model.processing.AudioConfiguration
import scalasound.model.processing.AudioEffectType.{Autopan, Echo}

/* Box fot the Echo-Autopan effects. */
private[effects] final class EchoAutopanBox extends VBox with EffectPane:

  private val FontSizeParameter = 18

  /* Importing of the Echo and Autopan parameters values. */
  private val echoConfig = AudioConfiguration.parameterConfiguration(Echo)
  private val autopanConfig = AudioConfiguration.parameterConfiguration(Autopan)

  private val alphaContainer = Utility.effectContainer("Alpha",
    echoConfig.alphaConfiguration,
    v => f"$v%.2f",
    titleSize = FontSizeParameter)

  private val delayContainer = Utility.effectContainer("Delay",
    echoConfig.delayConfiguration,
    v => s"${v.intValue()}ms",
    titleSize = FontSizeParameter)

  private val echo = new VBox:
    alignment = Pos.Center
    spacing = 20
    children = Seq(
      Utility.label(Echo.name, true, Utility.TitleEffectSize),
      alphaContainer.panel, delayContainer.panel)

  private val autopanContainer = Utility.effectContainer(Autopan.name,
    autopanConfig.autopanConfiguration,
    v => f"$v%.2f Hz")

  alignment = Pos.Center
  padding = Insets(10, 20, 10, 20)
  spacing = 50
  children = Seq(echo, autopanContainer.panel)

  override def resetSlidersAndValues(): Unit =
    alphaContainer.slider.value = echoConfig.alphaConfiguration.default
    delayContainer.slider.value = echoConfig.delayConfiguration.default
    autopanContainer.slider.value = autopanConfig.autopanConfiguration.default

  /**
   * @return the slider of Alpha parameter.
   */
  def alphaSlider: Slider = alphaContainer.slider

  /**
   * @return the slider of Delay parameter.
   */
  def delaySlider: Slider = delayContainer.slider

  /**
   * @return the slider of Autopan parameter.
   */
  def autopanSlider: Slider = autopanContainer.slider