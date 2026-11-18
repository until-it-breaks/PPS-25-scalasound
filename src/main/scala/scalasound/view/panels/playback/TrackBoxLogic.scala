package scalasound.view.panels.playback

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import scalafx.application.Platform
import scalasound.controller.PlaybackController
import scalasound.model.domain.AudioTrack
import scalasound.model.state.{AppState, AppStateStore}

/** Keeps the track box (timeline and time labels) in sync with the [[AppState]], forwards the track
  * box buttons to the [[PlaybackController]], and forwards user seeks on the timeline to it as
  * well.
  *
  * Seeks are triggered when the slider value changes except for drags, and only once when the user
  * releases the slider after dragging.
  *
  * @param store
  *   source of [[AppState]] changes (current track and position)
  * @param controller
  *   receives the playback and seek requests made by the user
  */
class TrackBoxLogic(store: AppStateStore, controller: PlaybackController)(using IORuntime):

  private var trackBox: Option[TrackBox] = None

  // True while this class is setting the slider itself (e.g. playback progress).
  // The slider listeners ignore changes made while this is set, so that our own updates aren't mistaken for seeks.
  private var isProgrammaticUpdate = false

  private def isDragging(box: TrackBox): Boolean = box.timeLine.valueChanging.value

  /** Connects the buttons and the timeline of the given box to the controller. Must be called once,
    * on the JavaFX thread, before the box is shown.
    *
    * @param box
    *   the track box to drive
    */
  def attach(box: TrackBox): Unit =
    trackBox = Some(box)

    box.playButton.onAction = _ => controller.play.unsafeRunAndForget()
    box.pauseButton.onAction = _ => controller.pause.unsafeRunAndForget()
    box.stopButton.onAction = _ => controller.stop.unsafeRunAndForget()

    // Handle slider changes that don't happen during dragging.
    box.timeLine.value.onChange { (_, _, newValue) =>
      val isUserChange = !isProgrammaticUpdate && !isDragging(box)
      Option
        .when(isUserChange)(newValue.doubleValue)
        .foreach(controller.seek(_).unsafeRunAndForget())
    }

    // Seek when the user finishes dragging the slider.
    box.timeLine.valueChanging.onChange { (_, _, changing) =>
      val dragEnded = !changing && !isProgrammaticUpdate
      Option
        .when(dragEnded)(box.timeLine.value.value)
        .foreach(controller.seek(_).unsafeRunAndForget())
    }

  /** Subscribes to [[AppState]] changes and updates the [[TrackBox]] on the JavaFX thread for each
    * one.
    *
    * This runs for as long as the state stream is open, so it is typically started in the
    * background.
    *
    * @return
    *   an effect that completes when the state stream ends
    */
  def observe: IO[Unit] =
    store.changes
      .map(state => (state.track, state.currentPositionSeconds))
      .evalMap { case (track, position) =>
        IO(Platform.runLater(trackBox.foreach(update(_, track, position))))
      }
      .compile
      .drain

  private def update(box: TrackBox, track: Option[AudioTrack], position: Double): Unit =
    track match
      case None =>
        import TrackBox.*
        updateSlider(box, EmptyDuration, EmptyPosition)
        box.startTimeLabel.text = EmptyTime
        box.endTimeLabel.text = EmptyTime
      case Some(track) =>
        val duration = track.durationInSeconds
        isDragging(box) match
          case true  => ()
          case false => updateSlider(box, duration, position)
        box.startTimeLabel.text = formatTime(position)
        box.endTimeLabel.text = formatTime(duration)

  private def updateSlider(box: TrackBox, max: Double, position: Double): Unit =
    isProgrammaticUpdate = true
    box.timeLine.max = max
    box.timeLine.value = position
    isProgrammaticUpdate = false

  private def formatTime(seconds: Double): String =
    val totalSeconds     = seconds.toLong
    val minutes          = totalSeconds / 60
    val remainingSeconds = totalSeconds % 60
    f"$minutes%02d:$remainingSeconds%02d"
