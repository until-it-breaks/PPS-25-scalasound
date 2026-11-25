package scalasound.view.panels.effects

import scalafx.geometry.Orientation.{Horizontal, Vertical}
import scalafx.geometry.{Orientation, Pos}
import scalafx.scene.Node
import scalafx.scene.control.{Label, Slider}
import scalafx.scene.layout.{HBox, VBox}
import scalasound.model.processing.ParameterConfiguration

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/* Utility class that contains common method used in different effect panels. */
private[effects] object Utility:

  private val ThicknessThumb = 8
  private val HeightThumb = ThicknessThumb * 3
  private val DefaultSliderSize = 300
  private val DefaultFontSize = 16
  private val DefaultHBoxSpacing = 10
  private val DefaultVBoxSpacing = 5
  private val BoldVBoxSpacing = 10
  private val DefaultFontColor = "black"

  val TitleEffectSize = 20

  private lazy val HorizontalSlider: String = sliderStyleDataUri(Horizontal)
  private lazy val VerticalSlider: String = sliderStyleDataUri(Vertical)

  /* Converting it in Data URI valid for the Wrapper ScalaFx. */
  private def sliderStyleDataUri(orientation: Orientation): String =
    val (widthSlider, heightSlider) = orientation match
      case Horizontal => (ThicknessThumb, HeightThumb)
      case Vertical => (HeightThumb, ThicknessThumb)
    val customSlider = s"""
    .custom-slider .track {
      -fx-background-color: gray;
      -fx-background-radius: 4px;
    }
    .custom-slider .thumb {
      -fx-background-color: red;
      -fx-pref-width: ${widthSlider}px;
      -fx-pref-height: ${heightSlider}px;
    }
  """
    "data:text/css," + URLEncoder.encode(customSlider, StandardCharsets.UTF_8).replace("+", "%20")

  /* Constructor for Slider Control. */
  private def slider(config: ParameterConfiguration,
                     sliderOrientation: Orientation,
                     sliderSize: Int = DefaultSliderSize): Slider =
    val sliderStyle = sliderOrientation match
      case Horizontal => HorizontalSlider
      case Vertical => VerticalSlider
    new Slider:
      min = config.min
      max = config.max
      value = config.default
      /* Assigning the custom class to the slider. */
      styleClass += "custom-slider"
      /* Injection of the style on the stylesheets. */
      stylesheets += sliderStyle
      sliderOrientation match
        case Horizontal => 
          prefWidth = sliderSize
        case Vertical =>
          orientation = Vertical
          prefHeight = sliderSize

  /**
   * Constructor for container of the effect in the GUI.
   * @param title the title of the container.
   * @param config the parameter configuration for the specific effect.
   * @param metrics the metric used for the specific parameter.
   * @param sliderOrientation horizontal slider or vertical orientation.
   * @param titleSize the size of the title container.
   * @param boldTitle the boldness of the font.
   * @param labels the labels that are at the sides of the slider.
   * @return a PanelComponents that contains the actual container and the slider.
   */
  def effectContainer(title: String,
                      config: ParameterConfiguration,
                      metrics: Float => String,
                      sliderOrientation: Orientation = Horizontal,
                      titleSize: Int = TitleEffectSize,
                      boldTitle: Boolean = true,
                      labels: Option[(String, String)] = None): PanelComponents =
    val slide = slider(config, sliderOrientation = sliderOrientation)
    val valueLabel = label(metrics(config.default))
    valueLabel.text <== slide.value.map(v => metrics(v.floatValue()))

    val middle: Node = sliderOrientation match
      case Horizontal => 
        val (leftText, rightText) = labels.getOrElse((metrics(config.min), metrics(config.max)))
        new HBox:
          alignment = Pos.Center
          spacing = DefaultHBoxSpacing
          children = Seq(label(leftText), slide, label(rightText))
      case Vertical => 
        slide

    val vbox = new VBox:
      alignment = Pos.Center
      spacing = if boldTitle then BoldVBoxSpacing else DefaultVBoxSpacing
      children = Seq(label(title, boldTitle, titleSize), middle, valueLabel)

    PanelComponents(vbox, slide)

  /**
   * Constructor for the label component.
   * @param content the text.
   * @param boldFont true if the font is bold and false otherwise.
   * @param fontSize size of the font, with default size.
   * @return a new Label.
   */
  def label(content: String,
            boldFont: Boolean = false,
            fontSize: Int = DefaultFontSize,
            color: String = DefaultFontColor): Label =
    val boldStyle = if boldFont then "-fx-font-weight: bold;" else ""
    new Label:
      text = content
      style = s"""
        -fx-font-size: ${fontSize}px;
        -fx-text-fill: $color;
        $boldStyle
      """