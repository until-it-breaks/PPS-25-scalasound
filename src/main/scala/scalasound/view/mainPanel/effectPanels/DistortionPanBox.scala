package scalasound.view.mainPanel.effectPanels

import scalafx.beans.property.ObjectProperty
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.Cursor
import scalafx.scene.control.{ToggleButton, ToggleGroup}
import scalafx.scene.layout.{HBox, VBox}
import scalasound.model.processing.{AudioConfiguration, AudioEffect}
import scalasound.model.processing.AudioEffectType.{Distortion, Pan}

/* Simplifies the management of the switch. */
private[effectPanels] enum DistortionEnum(val text: String):
  case Tanh extends DistortionEnum("Tanh")
  case Sin extends DistortionEnum("Sin")

/* Companion Object containing constants and utility methods. */
private[effectPanels] object DistortionPanBox:

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

/* Box for the Distortion-Pan effects. */
private[effectPanels] final class DistortionPanBox(using effectControls: EffectsControls) extends VBox with EffectPane:
  import DistortionEnum.*
  import DistortionPanBox.*

  /* Importing of the Pan parameter values. */
  private val panConfig = AudioConfiguration.parameterConfiguration(Pan)
  private val pan = Utility.effectContainer(Pan.name,
    panConfig.panConfiguration,
    v => effectControls.setEffect(AudioEffect.pan(v)),
    v => f"$v%.2f",
    labels = Some("L", "R"))

  private val defaultSwitchValue = Tanh
  /* Initialization of the switch for the distortion effect to be applied. */
  private val _selectedFilter: ObjectProperty[DistortionEnum] = ObjectProperty(defaultSwitchValue)
  private val distortionSwitch = switch(_selectedFilter)

  /* Importing of the Distortion parameter values. */
  private val distortionConfig = AudioConfiguration.parameterConfiguration(Distortion)
  private val distortion = Utility.effectContainer(Distortion.name,
    distortionConfig.distortionConfiguration,
    v => updateDistortion(v, _selectedFilter.value),
    v => f"$v%.2f")

  _selectedFilter.onChange { (_, _, newFilter) =>
    updateDistortion(distortion.slider.value.value.toFloat, newFilter)
  }

  private def updateDistortion(value: Float, filter: DistortionEnum): Unit =
    val isSoft = filter match
      case Tanh => true
      case Sin => false
    effectControls.setEffect(AudioEffect.distortion(value, isSoftClipping = isSoft))

  private val distortionContainer = new VBox:
    alignment = Pos.Center
    spacing = 15
    children = Seq(distortion.panel, distortionSwitch)

  alignment = Pos.Center
  padding = Insets(10, 20, 10, 20)
  spacing = 50
  children = Seq(pan.panel, distortionContainer)

  override def resetSlidersAndValues(): Unit =
    pan.slider.value = panConfig.panConfiguration.default
    distortion.slider.value = distortionConfig.distortionConfiguration.default
    _selectedFilter.value = defaultSwitchValue