package scalasound.view.panels.playback

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import scalafx.application.Platform
import scalasound.controller.PlaybackController
import scalasound.model.domain.AudioTrack
import scalasound.model.state.AppStateStore

import scala.concurrent.duration.{FiniteDuration, NANOSECONDS, SECONDS}

/** Keeps the [[TrackBox]] (timeline and time labels) in sync with the AppState, and forwards the
  * user's actions on it (the playback buttons and the seeks on the timeline) to the
  * [[PlaybackController]].
  *
  * A seek is requested when the user clicks on the timeline or releases it after a drag, but not
  * while dragging, so that each drag results in a single seek.
  */
trait TrackBoxLogic:

  /** Connects the buttons and the timeline of the given box to the controller.
    *
    * @param box
    *   the track box to drive
    */
  def attach(box: TrackBox): Unit

  /** Subscribes to AppState changes and updates the [[TrackBox]].
    *
    * @return
    *   an effect that completes when the state stream ends
    */
  def observe: IO[Unit]

object TrackBoxLogic:

  /** Creates a [[TrackBoxLogic]].
    *
    * @param store
    *   source of AppState changes (current track and position)
    * @param controller
    *   receives the playback and seek requests made by the user
    */
  def apply(store: AppStateStore, controller: PlaybackController)(using IORuntime): TrackBoxLogic =
    TrackBoxLogicImpl(store, controller)

private final class TrackBoxLogicImpl(store: AppStateStore, controller: PlaybackController)(using
    IORuntime
) extends TrackBoxLogic:

  private var trackBox: Option[TrackBox] = None

  // True while this class is setting the slider itself (e.g. playback progress).
  // The slider listeners ignore changes made while this is set, so that our own updates aren't mistaken for seeks.
  private var isProgrammaticUpdate = false

  override def attach(box: TrackBox): Unit =
    trackBox = Some(box)

    box.playButton.onAction = _ => controller.play.unsafeRunAndForget()
    box.pauseButton.onAction = _ => controller.pause.unsafeRunAndForget()
    box.stopButton.onAction = _ => controller.stop.unsafeRunAndForget()

    // Handle slider changes that don't happen during dragging.
    box.timeLine.value.onChange { (_, _, newValue) =>
      val isUserChange = !isProgrammaticUpdate && !isDragging(box)
      Option
        .when(isUserChange)(newValue.doubleValue)
        .map(toDuration)
        .foreach(controller.seek(_).unsafeRunAndForget())
    }

    // Seek when the user finishes dragging the slider.
    box.timeLine.valueChanging.onChange { (_, _, changing) =>
      val dragEnded = !changing && !isProgrammaticUpdate
      Option
        .when(dragEnded)(box.timeLine.value.value)
        .map(toDuration)
        .foreach(controller.seek(_).unsafeRunAndForget())
    }

  private def toDuration(seconds: Double): FiniteDuration =
    FiniteDuration((seconds * 1e9).toLong, NANOSECONDS)

  private def isDragging(box: TrackBox): Boolean = box.timeLine.valueChanging.value

  override def observe: IO[Unit] =
    store.playheadChanges
      .evalMap { case (track, chunkIndex) =>
        IO(Platform.runLater(trackBox.foreach(update(_, track, chunkIndex))))
      }
      .compile
      .drain

  private def update(box: TrackBox, track: Option[AudioTrack], chunkIndex: Int): Unit =
    track match
      case None =>
        import TrackBox.*
        updateSlider(box, EmptyDuration, EmptyPosition)
        box.startTimeLabel.text = EmptyTime
        box.endTimeLabel.text = EmptyTime
      case Some(track) =>
        val duration = track.duration
        val position = track.positionAt(chunkIndex)
        isDragging(box) match
          case true  => ()
          case false => updateSlider(box, duration.toUnit(SECONDS), position.toUnit(SECONDS))
        box.startTimeLabel.text = formatTime(position)
        box.endTimeLabel.text = formatTime(duration)

  private def updateSlider(box: TrackBox, max: Double, position: Double): Unit =
    isProgrammaticUpdate = true
    box.timeLine.max = max
    box.timeLine.value = position
    isProgrammaticUpdate = false

  private def formatTime(time: FiniteDuration): String =
    val totalSeconds     = time.toSeconds
    val minutes          = totalSeconds / 60
    val remainingSeconds = totalSeconds % 60
    f"$minutes%02d:$remainingSeconds%02d"
