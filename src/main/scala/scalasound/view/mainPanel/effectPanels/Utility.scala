package scalasound.view.mainPanel.effectPanels

import scalafx.geometry.Orientation
import scalafx.scene.control.{Label, Slider}

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

// TODO: DOC
private[effectPanels] object Utility:

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

  /**
   * Constructor for Slider Control.
   * @param minVal minimum value for the slider.
   * @param maxVal maximum value for the slider.
   * @param defaultVal default value for the slider.
   * @param horizontal true if horizontal slider is needed false otherwise.
   * @param sliderSize size of the slider, with a default size.
   * @return a new Slider.
   */
  def slider(minVal: Float,
             maxVal: Float,
             defaultVal: Float,
             horizontal: Boolean = true,
             sliderSize: Int = 300): Slider = new Slider:
    private val sliderStyle = if horizontal then HorizontalSlider else VerticalSlider
    min = minVal
    max = maxVal
    value = defaultVal
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