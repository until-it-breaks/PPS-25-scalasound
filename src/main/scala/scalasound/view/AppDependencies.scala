package scalasound.view

import scalasound.controller.LoadSaveController
import scalasound.view.loadsave.LoadSaveControls
import scalasound.view.panels.effects.EffectsLogic
import scalasound.view.panels.playback.TrackBoxLogic

/** Everything the user interface needs from the rest of the application.
  *
  * @param effectsLogic
  *   handler that manages the operations on the effects
  * @param loadSaveController
  *   controller that loads and saves tracks
  * @param loadSaveControls
  *   controls used by the title pane to load and save tracks
  * @param trackBoxLogic
  *   logic attached to the track box
  */
final case class AppDependencies(
    effectsLogic: EffectsLogic,
    loadSaveController: LoadSaveController,
    loadSaveControls: LoadSaveControls,
    trackBoxLogic: TrackBoxLogic
)
