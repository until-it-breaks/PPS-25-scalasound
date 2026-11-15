package scalasound.view.mainPanel.effectPanels

import scalafx.scene.control.Slider
import scalafx.scene.layout.Pane

/* Container for each effect. */
private[effectPanels] final case class PanelComponents(panel: Pane, slider: Slider)

private[effectPanels] trait EffectPane extends Pane:
  style = """
    -fx-background-color: white;
    -fx-border-color: black;
    -fx-border-width: 2px;
    -fx-border-radius: 5px;
    -fx-background-radius: 5px;
    -fx-alignment: center;
  """

  /**
   * Sets to default the various sliders.
   */
  def resetSlidersAndValues(): Unit