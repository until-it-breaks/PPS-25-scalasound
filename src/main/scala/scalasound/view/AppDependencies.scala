package scalasound.view

import scalasound.view.panels.effects.EffectsLogic
import scalasound.view.panels.loadsave.LoadSaveLogic
import scalasound.view.panels.playback.TrackBoxLogic

/** Everything the user interface needs from the rest of the application.
 *
 * @param effectsLogic
 *   handler that manages the operations on the effects
 * @param loadSaveLogic
 *   logic attached to the title pane to load and save tracks
 * @param trackBoxLogic
 *   logic attached to the track box
 */
final case class AppDependencies(
                                  effectsLogic: EffectsLogic,
                                  loadSaveLogic: LoadSaveLogic,
                                  trackBoxLogic: TrackBoxLogic
                                )
