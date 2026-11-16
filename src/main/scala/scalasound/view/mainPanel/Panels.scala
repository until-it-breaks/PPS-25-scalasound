package scalasound.view.mainPanel

import scalafx.application.JFXApp3.PrimaryStage
import scalasound.controller.LoadSaveController
import scalasound.view.loadsave.LoadSaveControls
import scalasound.view.mainPanel.effectPanels.{EffectPanel, EffectsControls}
import scalasound.view.mainPanel.TitlePane
import scalasound.view.mainPanel.playback.TrackBox

/**
 * Single point of access for the different main panels
 * that appear on the screen.
 */
object Panels:

  // TODO: AGGIORNARE DOC.
  /**
   * Gets the Panel containing the upper section of the screen.
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
   * @param stage the primary stage of the ScalaFx app.
   * @param effectsControls handler that manages the operations on track.
   * @return the EffectPanel.
   */
  def effectPane(stage: PrimaryStage, effectsControls: EffectsControls): EffectPanel =
    new EffectPanel(stage, effectsControls)