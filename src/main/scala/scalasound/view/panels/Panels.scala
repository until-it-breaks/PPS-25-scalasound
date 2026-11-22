package scalasound.view.panels

import scalasound.view.panels.effects.EffectsPanelContainer
import scalasound.view.panels.loadsave.TitlePane
import scalasound.view.panels.playback.TrackBox

/**
 * Single point of access for the different main panels
 * that appear on the screen.
 */
object Panels:

  /**
   * Gets the Panel containing the upper section of the screen.
   *
   * @return the TitlePane.
   */
  def titlePane(): TitlePane = TitlePane()

  /**
   * Gets the Box containing the middle section of the screen.
   *
   * @return the TrackBox.
   */
  def trackBox(): TrackBox = TrackBox()

  /**
   * Gets the Pane containing the lower section of the screen.
   *
   * @return the EffectsPanel.
   */
  def effectsPanel(): EffectsPanelContainer = EffectsPanelContainer()