package scalasound.view.panels.effects

import scalafx.beans.property.{ObjectProperty, ReadOnlyObjectProperty}
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.Cursor
import scalafx.scene.control.{Slider, ToggleButton, ToggleGroup}
import scalafx.scene.layout.{HBox, VBox}
import scalasound.model.processing.AudioConfiguration
import scalasound.model.processing.AudioEffectType.{Distortion, Pan}

/* Simplifies the management of the switch. */
private[effects] enum DistortionEnum(val text: String):
  case Tanh extends DistortionEnum("Tanh")
  case Sin extends DistortionEnum("Sin")

/* Companion Object containing constants and utility methods. */
private[effects] object DistortionPanBox:

  /* Constants for styling. */
  private val BackgroundColor = Utility.BackgroundColor
  private val BorderLeft =
    """
        -fx-background-radius: 15px 0px 0px 15px;
        -fx-border-radius: 15px 0px 0px 15px;
        -fx-border-width: 1px 0px 1px 1px;
      """
  private val BorderRight =
    """
      -fx-background-radius: 0px 15px 15px 0px;
      -fx-border-radius: 0px 15px 15px 0px;
      -fx-border-width: 1px 1px 1px 0px;
    """

  private def switch(selectedDistortion: ObjectProperty[DistortionEnum]): HBox = new HBox:
    private val group = new ToggleGroup()

    private val buttons =
      for
        dstEnum <- DistortionEnum.values
      yield
        new ToggleButton(dstEnum.text):
          toggleGroup = group
          cursor = Cursor.Hand
          selected = dstEnum == DistortionEnum.Tanh
          style <== selected.map(sel => styleButton(sel, dstEnum))
          onAction = _ =>
            if selected.value then
              selectedDistortion.value = dstEnum
            else
              selected = true

    alignment = Pos.Center
    children = buttons.toSeq

  private def styleButton(isSelected: Boolean, distType: DistortionEnum): String =
    val bkg = if isSelected then BackgroundColor else "white"
    val frg = if isSelected then "white" else "black"
    val border = distType match
      case DistortionEnum.Tanh => BorderLeft
      case DistortionEnum.Sin => BorderRight
    s"""
        -fx-background-color: $bkg;
        -fx-text-fill: $frg;
        -fx-font-size: 16px;
        -fx-font-weight: bold;
        -fx-padding: 6px 15px 6px 15px;
        -fx-border-color: black;
        $border
      """

/* Box for the Distortion-Pan effects. */
private[effects] final class DistortionPanBox extends VBox with EffectPane:

  import DistortionEnum.*
  import DistortionPanBox.*

  /* Importing of the Pan and Distortion parameter values. */
  private val panConfig = AudioConfiguration.parameterConfiguration(Pan)
  private val distortionConfig = AudioConfiguration.parameterConfiguration(Distortion)

  /* Initialization of the switch for the distortion effect to be applied. */
  private val defaultSwitchValue = Tanh
  private val _distortionFilter: ObjectProperty[DistortionEnum] = ObjectProperty(defaultSwitchValue)
  private val distortionSwitch = switch(_distortionFilter)

  private val panContainer = Utility.effectContainer(Pan.name,
    panConfig.panConfiguration,
    v => f"$v%.2f",
    labels = Some("L", "R"))

  private val distortionContainer = Utility.effectContainer(Distortion.name,
    distortionConfig.distortionConfiguration,
    v => f"$v%.2f")

  private val finalBoxDistortion = new VBox:
    alignment = Pos.Center
    spacing = 15
    children = Seq(distortionContainer.panel, distortionSwitch)

  alignment = Pos.Center
  padding = Insets(10, 20, 10, 20)
  spacing = 50
  children = Seq(panContainer.panel, finalBoxDistortion)

  override def resetSlidersAndValues(): Unit =
    panContainer.slider.value = panConfig.panConfiguration.default
    distortionContainer.slider.value = distortionConfig.distortionConfiguration.default
    _distortionFilter.value = defaultSwitchValue

  /**
   * @return the slider of Pan parameter.
   */
  def panSlider: Slider = panContainer.slider

  /**
   * @return the slider of Distortion parameter.
   */
  def distortionSlider: Slider = distortionContainer.slider

  /**
   * @return value of the filter for Distortion effect.
   */
  def distortionFilter: ReadOnlyObjectProperty[DistortionEnum] = _distortionFilter