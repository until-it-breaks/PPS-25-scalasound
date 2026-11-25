package scalasound.view.panels.effects

import scalafx.geometry.Insets
import scalafx.scene.layout.{HBox, Pane, VBox}

private[effects] object EffectPane:
  private val PaneStyle =
    """
      -fx-background-color: white;
      -fx-border-color: black;
      -fx-border-width: 2px;
      -fx-border-radius: 5px;
      -fx-background-radius: 5px;
      -fx-alignment: center;
    """

  private val DefaultPaddingHorizontal = Insets(10, 20, 10, 20)
  private val DefaultPaddingVertical = Insets(10, 30, 10, 30)
  private val DefaultSpacing = 50

  val BackgroundColor = "#4287f5"

/* Contract shared by the effects panels. */
private[effects] trait EffectPane extends Pane:

  import EffectPane.*

  style = PaneStyle

  this match
    case hBox: HBox =>
      hBox.padding = DefaultPaddingHorizontal
      hBox.spacing = EffectPane.DefaultSpacing
    case vBox: VBox =>
      vBox.padding = DefaultPaddingVertical
      vBox.spacing = EffectPane.DefaultSpacing

  /**
    * Sets to default the various sliders.
    */
  def resetSlidersAndValues(): Unit
