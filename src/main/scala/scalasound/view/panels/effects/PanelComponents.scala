package scalasound.view.panels.effects

import scalafx.scene.control.Slider
import scalafx.scene.layout.Pane

/*
 * Container pairing an effect panel with its specific control slider,
 * allowing assignment of listeners that react the user performs an action.
 */
private[effects] final case class PanelComponents(panel: Pane, slider: Slider)