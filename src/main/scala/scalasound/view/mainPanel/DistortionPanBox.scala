package scalasound.view.mainPanel

import scalafx.beans.binding.Bindings
import scalafx.beans.property.{FloatProperty, ObjectProperty}
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.Cursor
import scalafx.scene.control.{Label, Slider, ToggleButton, ToggleGroup}
import scalafx.scene.layout.{HBox, VBox}
import DistortionEnum.Off
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
  private val BackgroundColor = "#4287f5"
  private val BorderLeft =
    """
        -fx-background-radius: 15px 0px 0px 15px;
        -fx-border-radius: 15px 0px 0px 15px;
      """
  private val BorderRight =
    """
      -fx-background-radius: 0px 15px 15px 0px;
      -fx-border-radius: 0px 15px 15px 0px;
    """

  private def styleButton(isSelected: Boolean): String =
    val bkg = if isSelected then BackgroundColor else "gray"
    val frg = if isSelected then "white" else "#bdc3c7"
    s"""
        -fx-background-color: $bkg;
        -fx-text-fill: $frg;
        -fx-font-size: 12px;
        -fx-font-weight: bold;
        -fx-padding: 6px 15px 6px 15px;
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


    group.selectedToggle.onChange { (_, _, newToggle) =>
      if newToggle != null then
        selectedDistortion.value = newToggle.getUserData.asInstanceOf[DistortionEnum]
    }
    alignment = Pos.Center
    children = buttons.toSeq

  private def label(label: String): Label = new Label(label):
    style = """
      -fx-font-size: 14px;
    """

  private def container(effectName: String,
                        effectProperty: FloatProperty,
                        minVal: Float,
                        maxVal: Float,
                        defaultVal: Float,
                        labels: Option[(String, String)] = Option.empty): VBox = new VBox:

    private val slider: Slider = new Slider:
      min = minVal
      max = maxVal
      value = defaultVal
      prefWidth = 300

    private val valueLabel = label(defaultVal.toString)
    valueLabel.text <== slider.value.map(v => f"${v.floatValue()}%.2f")
    effectProperty <== slider.value

    private val middle = new HBox:
      alignment = Pos.Center
      spacing = 10
      children = labels match
        case Some((left, right)) => Seq(label(left), slider, label(right))
        case _ => Seq(label(minVal.toString), slider, label(maxVal.toString))

    alignment = Pos.Center
    spacing = 10
    children = Seq(label(effectName), middle, valueLabel)
  end container

end DistortionPanBox

/* Box for the Distortion-Pan effects. */
private[mainPanel] final class DistortionPanBox extends VBox:
  import DistortionPanBox.*

  private val panConfig = AudioConfiguration.parameterConfiguration(Pan)
  private val _panValue: FloatProperty = FloatProperty(panConfig.panConfiguration.min)
  private val panContainer = container(Pan.name,
    _panValue,
    panConfig.panConfiguration.min,
    panConfig.panConfiguration.max,
    panConfig.panConfiguration.default,
    Some("L", "R"))
  private val distortionConfig = AudioConfiguration.parameterConfiguration(Distortion)
  private val _distortionValue: FloatProperty = FloatProperty(distortionConfig.distortionConfiguration.min)
  private val distortionContainer = container(Distortion.name,
    _distortionValue,
    distortionConfig.distortionConfiguration.min,
    distortionConfig.distortionConfiguration.max,
    distortionConfig.distortionConfiguration.default)
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