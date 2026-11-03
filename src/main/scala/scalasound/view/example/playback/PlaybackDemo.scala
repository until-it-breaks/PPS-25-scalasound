package scalasound.view.example.playback

import cats.effect.unsafe.IORuntime
import scalafx.application.JFXApp3
import scalafx.application.JFXApp3.PrimaryStage
import scalafx.scene.Scene
import scalasound.controller.PlaybackController
import scalasound.model.state.AppStateStore

/**
 * Standalone JavaFX application demonstrating basic audio playback.
 *
 * Assembles the playback controls and their state observer,
 * creates the application window, and starts observing application state.
 *
 * @param store the [[AppStateStore]] used to observe playback changes
 * @param controller the [[PlaybackController]] used by the playback controls
 */
class PlaybackDemo(store: AppStateStore, controller: PlaybackController) extends JFXApp3:

  given IORuntime = IORuntime.global

  override def start(): Unit =
    // Create the visual playback controls and connect them to the controller.
    val controls = new PlaybackControls(controller)
    // Create an observer that keeps the controls synchronized with the AppState.
    val observer = new PlaybackControlsObserver(
      store,
      controls,
      controller
    )
    // Create the main application window.
    stage = new PrimaryStage:
      title = "Playback Demo"
      width = 400
      height = 300
      scene = new Scene:
        root = controls.view
    // Start observing state changes in the background.
    observer.observe.unsafeRunAndForget()