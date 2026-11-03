package scalasound.model.state

import scalasound.model.domain.AudioTrack

/** Represents the current playback lifecycle status. */
enum PlaybackState:
  case Stopped
  case Playing
  case Paused

/** Represents the track import lifecycle status. */
enum ImportState:
  case Idle
  case Importing
  case Imported(track: AudioTrack)
  case Failed(reason: String)

/** Represents the track export lifecycle status. */
enum ExportState:
  case Idle
  case Exporting
  case Exported
  case Failed(reason: String)

/** Represents all discrete commands that can mutate application state. */
enum Command:
  case Play
  case Pause
  case Stop
  case NextChunk
  case SeekToIndex(index: Int)
  case UpdateImport(state: ImportState)
  case UpdateExport(state: ExportState)

import PlaybackState.*
import ExportState.*
import ImportState.*
import Command.*

/** Core immutable application state and state machine for the audio system.
 *
 *   - Playback Control (`Play`, `Pause`, `Stop`): Playback can only transition to `Playing` if a
 *     track is successfully loaded (`track = Some(...)`). Pausing works exclusively from a `Playing`
 *     state. Stopping halts playback and resets the play head (`chunkIndex = 0`).
 *   - Position & Seeking (`SeekToIndex`, NextChunk`): The play head position
 *     (`chunkIndex`) is maintained as a top-level property, allowing seeking at any time (whether playing,
 *     paused, or stopped). Index boundaries are automatically clamped between `0` and `track.chunks.length - 1`.
 *   - Track Import (`UpdateImport`): When an audio track is successfully imported (`Imported(track)`),
 *     the state automatically locks in the new track, resets playback to `Stopped`, and repositions the
 *     play head to `0`.
 *   - Track Export (`UpdateExport`): Tracks the progress and lifecycle of
 *     file export tasks independently of audio playback.
 *
 * @param playbackState current playback state (`Stopped`, `Playing`, `Paused`)
 * @param importState   current track import state
 * @param track         the successfully loaded audio track, if any
 * @param chunkIndex    the current active audio chunk position (play head index)
 * @param exportState   current track export state
 */
final case class AppState(
                           playbackState: PlaybackState,
                           importState: ImportState,
                           track: Option[AudioTrack],
                           chunkIndex: Int,
                           exportState: ExportState
                         ):

  def handle(command: Command): AppState =
    command match
      case Play                 => handlePlay()
      case Pause                => handlePause()
      case Stop                 => handleStop()
      case NextChunk            => handleNextChunk()
      case SeekToIndex(index)   => handleSeek(index)
      case UpdateImport(state)  => handleImport(state)
      case UpdateExport(state)  => handleExport(state)

  /** Command router mapping a [[Command]] to designated state transitions. */
  private def handlePlay(): AppState =
    track match
      case None => this
      case Some(_) => copy(playbackState = Playing)

  private def handlePause(): AppState =
    playbackState match
      case Playing => copy(playbackState = Paused)
      case _ => this

  private def handleStop(): AppState =
    copy(playbackState = Stopped, chunkIndex = 0)

  private def handleNextChunk(): AppState =
    playbackState match
      case Playing => copy(chunkIndex = chunkIndex + 1)
      case _ => this

  private def handleSeek(index: Int): AppState =
    track match
      case None => this
      case Some(track) =>
        val maxIndex = math.max(0, track.chunks.length - 1)
        val clamped = math.max(0, math.min(index, maxIndex))
        copy(chunkIndex = clamped)

  def currentPositionSeconds: Double =
    track.fold(0.0) { track =>
      val framesBeforeCurrent = track.chunks.view
        .take(chunkIndex)
        .map(_.frameCount.toLong)
        .sum
      framesBeforeCurrent.toDouble / track.sampleRate
    }

  private def handleImport(state: ImportState): AppState =
    state match
      case Imported(track) =>
        copy(
          importState = state,
          track = Some(track),
          playbackState = Stopped,
          chunkIndex = 0
        )
      case _ =>
        copy(importState = state)

  private def handleExport(state: ExportState): AppState =
    copy(exportState = state)

object AppState:
  /** Initial application state. */
  val initial: AppState = AppState(
    playbackState = Stopped,
    importState = ImportState.Idle,
    track = None,
    chunkIndex = 0,
    exportState = ExportState.Idle
  )