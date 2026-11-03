package scalasound.gui.mainPanel

import scalafx.beans.binding.Bindings
import scalafx.beans.property.{BooleanProperty, FloatProperty}
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.{Cursor, Node}
import scalafx.scene.control.{Label, Slider, ToggleButton}
import scalafx.scene.layout.{HBox, VBox}
import scalasound.gui.mainPanel.EchoAutopanBox.{container, label, onOffSwitch}

private[mainPanel] object EchoAutopanBox:

  private val OnSwitchColor = "#4287f5"
  private val OffSwitchColor = "#7f8c8d"

  private def label(text: String): Label = new Label(text):
    style = """
      -fx-font-size: 13px;
    """

  private def onOffSwitch(autopanSelected: BooleanProperty): ToggleButton = new ToggleButton:
    cursor = Cursor.Hand
    selected <==> autopanSelected

    text <== Bindings.createStringBinding(
      () => if selected.value then "ON" else "OFF",
      selected
    )

    style <== Bindings.createStringBinding(
      () => s"""
         -fx-background-color: ${if selected.value then OnSwitchColor else OffSwitchColor};
         -fx-text-fill: white;
         -fx-font-size: 13px;
         -fx-font-weight: bold;
         -fx-padding: 8px 18px 8px 18px;
         -fx-background-radius: 10px;
         -fx-border-radius: 10px;
      """,
      selected
    )

  private def container(effectName: String,
                        effectProperty: FloatProperty,
                        minVal: Float,
                        maxVal: Float,
                        defaultVal: Float,
                        metrics: String): VBox = new VBox:

    private val slider: Slider = new Slider:
      min = minVal
      max = maxVal
      value = defaultVal
      prefWidth = 300

    private val valueLabel = label(defaultVal.toString)
    valueLabel.text <== slider.value.map(v =>
      if metrics.isEmpty then f"${v.floatValue()}%.2f" else f"${v.intValue()}" + metrics)
    effectProperty <== slider.value

    private val middle = new HBox:
      alignment = Pos.Center
      spacing = 10
      children = Seq(label(minVal.toString), slider, label(maxVal.toString))

    alignment = Pos.Center
    spacing = 5
    children = Seq(label(effectName), middle, valueLabel)
  end container

//TODO: VISTO CHE I VARI PANNELLI HANNO STESSO O SIMILE STYLE DEFINIRE UN TRAIT?
private[mainPanel] final class EchoAutopanBox extends VBox:

  private val autopanSelected = BooleanProperty(false)
  private val alphaValue: FloatProperty = FloatProperty(0)
  private val delayValue: FloatProperty = FloatProperty(0)

  private val alphaContainer = container("Alpha", alphaValue, 0, 0, 0, "")
  private val delayContainer = container("Delay", delayValue, 0, 0, 0, "ms")

  private def effectContainer(nodes: Seq[Node]) = new VBox:
    alignment = Pos.Center
    spacing = 20
    children = nodes

  private val echoContainer = effectContainer(Seq(label("Echo"), alphaContainer, delayContainer))
  private val autopanContainer = effectContainer(Seq(label("Autopan"), onOffSwitch(autopanSelected)))

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