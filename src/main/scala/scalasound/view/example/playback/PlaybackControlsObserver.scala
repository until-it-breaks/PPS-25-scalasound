package scalasound.view.example.playback

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import scalafx.application.Platform
import scalasound.controller.PlaybackController
import scalasound.model.domain.AudioTrack
import scalasound.model.state.AppStateStore

/**
 * Keeps the playback controls (seek slider and time label) in sync with the [[AppState]],
 * and forwards user seeks on the slider to the [[PlaybackController]].
 *
 * Seeks are triggered when the slider value changes except for drags,
 * and only once when the user releases the slider after dragging.
 *
 * @param store source of [[AppState]] changes (current track and position)
 * @param controls the playback controls to update and listen to
 * @param controller receives seek requests made by the user
 */
class PlaybackControlsObserver(
                                store: AppStateStore,
                                controls: PlaybackControls,
                                controller: PlaybackController
                              )(using IORuntime):

  // True while this class is setting the slider itself (e.g. playback progress).
  // The slider listeners ignore changes made while this is set, so that our own updates aren't mistaken for seeks.
  private var isProgrammaticUpdate = false

  private def isDragging: Boolean = controls.seekSlider.valueChanging.value

  // Handle slider changes that don't happen during dragging.
  controls.seekSlider.value.onChange { (_, _, newValue) =>
    val isUserChange = !isProgrammaticUpdate && !isDragging
    Option.when(isUserChange)(newValue.doubleValue).foreach(seek)
  }

  // Seek when the user finishes dragging the slider.
  controls.seekSlider.valueChanging.onChange { (_, _, changing) =>
    val dragEnded = !changing && !isProgrammaticUpdate
    Option.when(dragEnded)(controls.seekSlider.value.value).foreach(seek)
  }

  /**
   * Subscribes to app state changes and updates the controls on the JavaFX thread for each one.
   *
   * Only the current track and the current position in seconds are taken from each state, and
   * the controls are updated from those two values alone.
   *
   * This runs for as long as the state stream is open, so it is typically started in the background.
   *
   * @return an effect that completes when the state stream ends
   */
  def observe: IO[Unit] =
    store.changes
      .map(state => (state.track, state.currentPositionSeconds))
      .evalMap { case (track, position) =>
        IO(Platform.runLater(updateControls(track, position)))
      }
      .compile
      .drain

  private def seek(position: Double): Unit =
    controller.seek(position).unsafeRunAndForget()

  private def updateControls(track: Option[AudioTrack], position: Double): Unit =
    track match
      case None =>
        updateSlider(1, 0)
        controls.timeLabel.text = "--:-- / --:--"
      case Some(track) =>
        val duration = track.durationInSeconds
        isDragging match
          case true  => ()
          case false => updateSlider(duration, position)
        controls.timeLabel.text =
          s"${formatTime(position)} / ${formatTime(duration)}"

  private def updateSlider(max: Double, position: Double): Unit =
    isProgrammaticUpdate = true
    controls.seekSlider.max = max
    controls.seekSlider.value = position
    isProgrammaticUpdate = false

  private def formatTime(seconds: Double): String =
    val totalSeconds = seconds.toLong
    val minutes = totalSeconds / 60
    val remainingSeconds = totalSeconds % 60
    f"$minutes%02d:$remainingSeconds%02d"