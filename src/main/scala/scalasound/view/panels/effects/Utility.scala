package scalasound.view.panels.effects

import scalafx.geometry.{Orientation, Pos}
import scalafx.scene.Node
import scalafx.scene.control.{Label, Slider}
import scalafx.scene.layout.{HBox, VBox}
import scalasound.model.processing.ParameterConfiguration

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/* Utility class that contains common method used in different effect panels. */
private[effects] object Utility:

  // TODO: Conviene lasciarla public?
  val BackgroundColor = "#4287f5"
  val TitleEffectSize = 20

  private val ThicknessThumb = 8
  private val HeightThumb = ThicknessThumb * 3

  /* Converting it in Data URI valid for the Wrapper ScalaFx. */
  private def sliderStyleDataUri(horizontal: Boolean): String =
    val widthSlider = if horizontal then ThicknessThumb else HeightThumb
    val heightSlider = if horizontal then HeightThumb else ThicknessThumb
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

  private lazy val HorizontalSlider: String = sliderStyleDataUri(true)
  private lazy val VerticalSlider: String = sliderStyleDataUri(false)

  /* Constructor for Slider Control. */
  private def slider(config: ParameterConfiguration, 
                     horizontal: Boolean, 
                     sliderSize: Int = 300): Slider = new Slider:
    private val sliderStyle = if horizontal then HorizontalSlider else VerticalSlider
    min = config.min
    max = config.max
    value = config.default
    /* Assigning the custom class to the slider. */
    styleClass += "custom-slider"
    /* Injection of the style on the stylesheets. */
    stylesheets += sliderStyle
    if horizontal then
      prefWidth = sliderSize
    else
      orientation = Orientation.Vertical
      prefHeight = sliderSize
  
  /**
   * Constructor for container of the effect in the GUI.
   * @param title the title of the container.
   * @param config the parameter configuration for the specific effect.
   * @param metrics the metric used for the specific parameter.
   * @param isHorizontal horizontal slider or vertical if it's set to false.
   * @param titleSize the size of the title container.
   * @param boldTitle the boldness of the font.
   * @param labels the labels that are at the sides of the slider.
   * @return a PanelComponents that contains the actual container and the slider.
   */
  def effectContainer(title: String,
                      config: ParameterConfiguration,
                      metrics: Float => String,
                      isHorizontal: Boolean = true,
                      titleSize: Int = TitleEffectSize,
                      boldTitle: Boolean = true,
                      labels: Option[(String, String)] = None): PanelComponents =
    val slide = slider(config, horizontal = isHorizontal)
    val valueLabel = label(metrics(config.default))
    valueLabel.text <== slide.value.map(v => metrics(v.floatValue()))

    val middle: Node =
      if isHorizontal then
        val (leftText, rightText) = labels.getOrElse((metrics(config.min), metrics(config.max)))
        new HBox:
          alignment = Pos.Center
          spacing = 10
          children = Seq(label(leftText), slide, label(rightText))
      else slide

    val vbox = new VBox:
      alignment = Pos.Center
      spacing = if boldTitle then 10 else 5
      children = Seq(label(title, boldTitle, titleSize), middle, valueLabel)

    PanelComponents(vbox, slide)
  
  /**
   * Constructor for the label component.
   * @param label the text.
   * @param boldFont true if the font is bold and false otherwise.
   * @param fontSize size of the font, with default size.
   * @return a new Label.
   */
  def label(label: String,
            boldFont: Boolean = false,
            fontSize: Int = 16,
            color: String = "black"): Label = new Label:
    text = label
    private val boldStyle = if boldFont then "-fx-font-weight: bold;" else ""
    style = s"""
      -fx-font-size: ${fontSize}px;
      -fx-text-fill: ${color};
      ${boldStyle}
    """