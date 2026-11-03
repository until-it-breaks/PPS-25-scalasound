package scalasound.view.example.playback

import cats.effect.unsafe.IORuntime
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.control.{Button, Label, Slider}
import scalafx.scene.layout.{HBox, VBox}
import scalasound.controller.PlaybackController

/**
 * Provides the visual controls for audio playback.
 *
 * Creates the play, pause, and stop buttons, a seek slider, and a playback time label.
 *
 * Button actions are delegated to the supplied [[PlaybackController]].
 * The slider and time label can be updated externally by a [[PlaybackControlsObserver]].
 *
 * @param controller controller responsible for handling playback commands
 */
class PlaybackControls(controller: PlaybackController)(using IORuntime):
  val playButton = new Button("Play")
  val pauseButton = new Button("Pause")
  val stopButton = new Button("Stop")
  val seekSlider = new Slider(0, 1, 0)
  val timeLabel = new Label("--:-- / --:--")

  playButton.onAction = _ => controller.play.unsafeRunAndForget()
  pauseButton.onAction = _ => controller.pause.unsafeRunAndForget()
  stopButton.onAction = _ => controller.stop.unsafeRunAndForget()

  private val buttonBox = new HBox(10):
    alignment = Pos.Center
    children = Seq(playButton, pauseButton, stopButton)

  val view: VBox = new VBox(15):
    alignment = Pos.Center
    padding = Insets(20)
    children = Seq(buttonBox, seekSlider, timeLabel)