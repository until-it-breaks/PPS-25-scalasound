package scalasound.view.loadsave

import scalasound.model.domain.AudioTrack
import scalasound.model.io.{AudioLoadError, AudioSaveError}

/** What the user sees of the import and export operations. */
trait LoadSaveView:

  /** No import is in progress. */
  def importIdle(): Unit

  /** An import has started. */
  def importStarted(): Unit

  /** An import has completed.
   *
   *  @param track the loaded track
   */
  def importCompleted(track: AudioTrack): Unit

  /** An import has failed.
   *
   *  @param error the reason for the failure
   */
  def importFailed(error: AudioLoadError): Unit

  /** No export is in progress. */
  def exportIdle(): Unit

  /** An export has started. */
  def exportStarted(): Unit

  /** An export has completed. */
  def exportCompleted(): Unit

  /** An export has failed.
   *
   *  @param error the reason for the failure
   */
  def exportFailed(error: AudioSaveError): Unit
