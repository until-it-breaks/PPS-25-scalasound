package scalasound.view.mainPanel

import scalafx.beans.binding.Bindings
import scalafx.beans.property.{FloatProperty, ObjectProperty}
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.Cursor
import scalafx.scene.control.{Slider, ToggleButton, ToggleGroup}
import scalafx.scene.layout.{HBox, VBox}
import scalasound.model.processing.AudioConfiguration
import scalasound.model.processing.AudioEffectType.{Distortion, Pan}

/* Simplifies the management of the switch. */
private[mainPanel] enum DistortionEnum(val text: String):
  case Tanh extends DistortionEnum("Tanh")
  case Off extends DistortionEnum("OFF")
  case Sin extends DistortionEnum("Sin")

/* Companion Object containing constants and utility methods. */
private[mainPanel] object DistortionPanBox:

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

  private def styleButton(isSelected: Boolean): String =
    val bkg = if isSelected then BackgroundColor else "white"
    val frg = if isSelected then "white" else "black"
    s"""
        -fx-background-color: $bkg;
        -fx-text-fill: $frg;
        -fx-font-size: 16px;
        -fx-font-weight: bold;
        -fx-padding: 6px 15px 6px 15px;
        -fx-border-color: black;
      """

  private def getBorder(distType: DistortionEnum): String = distType match
    case DistortionEnum.Tanh => BorderLeft
    case DistortionEnum.Sin => BorderRight
    case _ => ""

  private def switch(selectedDistortion: ObjectProperty[DistortionEnum]): HBox = new HBox:
    private val group = new ToggleGroup()

    private val buttons =
      for
        dstEnum <- DistortionEnum.values
      yield
        new ToggleButton(dstEnum.text):
          toggleGroup = group
          cursor = Cursor.Hand
          userData = dstEnum
          selected = if dstEnum == DistortionEnum.Off then true else false
          style <== Bindings.createStringBinding(
            () => styleButton(selected.value) + getBorder(dstEnum),
            selected
          )

    group.selectedToggle.onChange { (_, oldToggle, newToggle) =>
      if newToggle == null then
        group.selectToggle(oldToggle)
        else
          val newVal = newToggle.getUserData.asInstanceOf[DistortionEnum]
          if selectedDistortion.value != newVal then
            selectedDistortion.value = newVal
    }
    alignment = Pos.Center
    children = buttons.toSeq

  private def container(effectName: String,
                        effectProperty: FloatProperty,
                        minVal: Float,
                        maxVal: Float,
                        defaultVal: Float,
                        labels: Option[(String, String)] = Option.empty): VBox = new VBox:

    private val slider: Slider = Utility.slider(minVal, maxVal, defaultVal)
    private val valueLabel = Utility.label(defaultVal.toString)
    valueLabel.text <== slider.value.map(v => f"${v.floatValue()}%.2f")
    effectProperty <== slider.value

    private val middle = new HBox:
      alignment = Pos.Center
      spacing = 10
      children = labels match
        case Some((left, right)) => Seq(Utility.label(left), slider, Utility.label(right))
        case _ => Seq(Utility.label(minVal.toString), slider, Utility.label(maxVal.toString))

    alignment = Pos.Center
    spacing = 10
    children = Seq(Utility.label(effectName, true, Utility.titleEffectSize), middle, valueLabel)

/* Box for the Distortion-Pan effects. */
private[mainPanel] final class DistortionPanBox extends VBox:
  import DistortionPanBox.*
  import DistortionEnum.Off

  /* Importing of the Pan parameter values. */
  private val panConfig = AudioConfiguration.parameterConfiguration(Pan)
  private val _panValue: FloatProperty = FloatProperty(panConfig.panConfiguration.min)
  private val panContainer = container(Pan.name,
    _panValue,
    panConfig.panConfiguration.min,
    panConfig.panConfiguration.max,
    panConfig.panConfiguration.default,
    Some("L", "R"))

  /* Importing of the Distortion parameter values. */
  private val distortionConfig = AudioConfiguration.parameterConfiguration(Distortion)
  private val _distortionValue: FloatProperty = FloatProperty(distortionConfig.distortionConfiguration.min)
  private val distortionContainer = container(Distortion.name,
    _distortionValue,
    distortionConfig.distortionConfiguration.min,
    distortionConfig.distortionConfiguration.max,
    distortionConfig.distortionConfiguration.default)

  /* Initialization of the switch for the distortion effect to be applied. */
  private val _selectedFilter: ObjectProperty[DistortionEnum] = ObjectProperty(Off)
  private val distortionSwitch = switch(_selectedFilter)
  private val distFinalContainer = new VBox:
    alignment = Pos.Center
    spacing = 15
    children = Seq(distortionContainer, distortionSwitch)

  alignment = Pos.Center
  padding = Insets(10, 20, 10, 20)
  spacing = 50
  style = """
        -fx-background-color: white;
        -fx-border-color: black;
        -fx-border-width: 2px;
        -fx-border-radius: 5px;
        -fx-background-radius: 5px;
        -fx-alignment: center;
      """
  children = Seq(panContainer, distFinalContainer)

  /**
   * @return the observer on distortion value.
   */
  def distortionValue: FloatProperty = _distortionValue

  /**
   * @return the observer on pan value.
   */
  def panValue: FloatProperty = _panValue

  /**
   * @return the observer on the filter selected for the distortion.
   */
  def selectedFilter: ObjectProperty[DistortionEnum] = _selectedFilter