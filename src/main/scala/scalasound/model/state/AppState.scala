package scalasound.model.state

import scalasound.model.domain.{AudioChunk, AudioTrack}

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
  case SeekToSeconds(seconds: Double)
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
 *     state. Stopping halts playback and resets the playhead (`chunkIndex = 0`).
 *   - Position & Seeking (`SeekToIndex`, `SeekToSeconds`, `NextChunk`): The playhead position
 *     (`chunkIndex`) is maintained as a top-level property, allowing seeking at any time (whether playing,
 *     paused, or stopped). Index boundaries are automatically clamped between `0` and `track.chunks.length - 1`.
 *   - Track Import (`UpdateImport`): When an audio track is successfully imported (`Imported(track)`),
 *     the state automatically locks in the new track, resets playback to `Stopped`, and repositions the
 *     playhead to `0`.
 *   - Track Export (`UpdateExport`): Tracks the progress and lifecycle of
 *     file export tasks independently of audio playback.
 *
 * @param playbackState current playback state (`Stopped`, `Playing`, `Paused`)
 * @param importState   current track import state
 * @param track         the successfully loaded audio track, if any
 * @param chunkIndex    the current active audio chunk position (playhead index)
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
      case SeekToSeconds(sec)   => handleSeek(sec)
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

  private def handleSeek(seconds: Double): AppState =
    track.fold(this) { track =>
      val targetFrame = math.max(0L, (seconds * track.sampleRate).toLong)
      handleSeek(findChunkIndexForFrame(track.chunks, targetFrame))
    }

  private def findChunkIndexForFrame(chunks: Vector[AudioChunk], targetFrame: Long): Int =
    import scala.annotation.tailrec
    @tailrec
    def loop(index: Int, accumulatedFrames: Long): Int =
      index >= chunks.length match
        case true =>
          math.max(0, chunks.length - 1)
        case false =>
          val chunk = chunks(index)
          val nextAccumulated = accumulatedFrames + chunk.frameCount
          targetFrame < nextAccumulated match
            case true => index
            case false => loop(index + 1, nextAccumulated)
    loop(0, 0L)

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