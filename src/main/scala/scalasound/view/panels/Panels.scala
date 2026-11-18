package scalasound.view.panels

import scalafx.application.JFXApp3.PrimaryStage
import scalasound.controller.LoadSaveController
import scalasound.view.loadsave.LoadSaveControls
import scalasound.view.panels.effects.EffectsPanelContainer
import scalasound.view.panels.TitlePane
import scalasound.view.panels.playback.TrackBox

/**
 * Single point of access for the different main panels
 * that appear on the screen.
 */
object Panels:

  // TODO: AGGIORNARE DOC.

  /**
   * Gets the Panel containing the upper section of the screen.
   *
   * @param stage the primary stage of the ScalaFx app.
   * @return the TitlePane.
   */
  def titlePane(
                 stage: PrimaryStage,
                 loadSaveController: LoadSaveController,
                 loadSaveControls: LoadSaveControls
               ): TitlePane =
    new TitlePane(stage, loadSaveController, loadSaveControls)

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