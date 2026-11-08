package scalasound.view.mainPanel.effectPanels

import scalafx.beans.binding.Bindings
import scalafx.beans.property.{BooleanProperty, FloatProperty, ReadOnlyBooleanProperty, ReadOnlyFloatProperty}
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.control.{Label, Slider, ToggleButton}
import scalafx.scene.layout.{HBox, VBox}
import scalafx.scene.{Cursor, Node}
import scalasound.model.processing.AudioConfiguration
import scalasound.model.processing.AudioEffectType.{Autopan, Echo}

private[effectPanels] object EchoAutopanBox:

  private val OnSwitchColor = Utility.BackgroundColor
  private val OffSwitchColor = "white"
  private val FontSizeParameter = 18

  private def onOffSwitch(autopanSelected: BooleanProperty): ToggleButton = new ToggleButton:
    cursor = Cursor.Hand
    autopanSelected <== selected

    text <== Bindings.createStringBinding(
      () => if selected.value then "ON" else "OFF",
      selected
    )

    style <== Bindings.createStringBinding(
      () => s"""
         -fx-background-color: ${if selected.value then OnSwitchColor else OffSwitchColor};
         -fx-text-fill: ${if selected.value then "white" else "black"};
         -fx-font-size: 16px;
         -fx-font-weight: bold;
         -fx-padding: 8px 18px 8px 18px;
         -fx-background-radius: 10px;
         -fx-border-radius: 10px;
         -fx-border-color: black;
         -fx-border-width: 1px;
      """,
      selected
    )

  private def container(effectName: String,
                        effectProperty: FloatProperty,
                        minVal: Float,
                        maxVal: Float,
                        defaultVal: Float,
                        metrics: Float => String): VBox = new VBox:

    private val slider: Slider = Utility.slider(minVal, maxVal, defaultVal)
    private val valueLabel = Utility.label(metrics(defaultVal))
    valueLabel.text <== slider.value.map(v => metrics(v.floatValue()))
    effectProperty <== slider.value

    private val middle = new HBox:
      alignment = Pos.Center
      spacing = 10
      children = Seq(Utility.label(minVal.toString), slider, Utility.label(maxVal.toString))

    alignment = Pos.Center
    spacing = 5
    children = Seq(Utility.label(effectName, fontSize = FontSizeParameter), middle, valueLabel)

//TODO: VISTO CHE I VARI PANNELLI HANNO STESSO O SIMILE STYLE DEFINIRE UN TRAIT?

/* Box fot the Echo-Autopan effects. */
private[effectPanels] final class EchoAutopanBox extends VBox:
  import EchoAutopanBox.*

  private val _autopanSelected = BooleanProperty(false)

  /* Importing of the Echo parameters values. */
  private val echoConfig = AudioConfiguration.parameterConfiguration(Echo)
  private val _alphaValue: FloatProperty = FloatProperty(echoConfig.alphaConfiguration.default)
  private val alphaContainer = container("Alpha",
    _alphaValue,
    echoConfig.alphaConfiguration.min,
    echoConfig.alphaConfiguration.max,
    echoConfig.alphaConfiguration.default,
    v => f"${v}%.2f")
  private val _delayValue: FloatProperty = FloatProperty(echoConfig.delayConfiguration.default)
  private val delayContainer = container("Delay",
    _delayValue,
    echoConfig.delayConfiguration.min,
    echoConfig.delayConfiguration.max,
    echoConfig.delayConfiguration.default,
    v => s"${v.intValue()}ms")

  private def effectContainer(nodes: Seq[Node]) = new VBox:
    alignment = Pos.Center
    spacing = 20
    children = nodes

  private val echoContainer = effectContainer(
    Seq(Utility.label(Echo.name, true, Utility.TitleEffectSize), alphaContainer, delayContainer))
  private val autopanContainer = effectContainer(
    Seq(Utility.label(Autopan.name, true, Utility.TitleEffectSize), onOffSwitch(_autopanSelected)))

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
  children = Seq(echoContainer, autopanContainer)

  /**
   * @return the observer on alpha value for the echo.
   */
  def alphaValue: ReadOnlyFloatProperty = _alphaValue

  /**
   * @return the observer on delay value for the echo.
   */
  def delayValue: ReadOnlyFloatProperty = _delayValue

  /**
   * @return the observer on the switch for the autopan value.
   */
  def autopanSelected: ReadOnlyBooleanProperty = _autopanSelected