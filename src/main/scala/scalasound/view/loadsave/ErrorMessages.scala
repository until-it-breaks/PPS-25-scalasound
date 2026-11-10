package scalasound.view.loadsave

import scalasound.model.io.{AudioLoadError, AudioSaveError}

/** Turns import and export errors into messages that can be shown to the user. */
object ErrorMessages:

  /** Describes why a file could not be imported.
   *
   *  @param error the error that occurred during import
   *  @return a message describing the error
   */
  def describe(error: AudioLoadError): String =
    error match
      case AudioLoadError.FileNotFound(path) => s"The file was not found: $path"
      case AudioLoadError.UnsupportedFormat(reason) => s"The file format is not supported: $reason"
      case AudioLoadError.CorruptedData(reason) => s"The file is corrupted: $reason"
      case AudioLoadError.ReadFailure(reason) => s"The file could not be read: $reason"

  /** Describes why the track could not be exported.
   *
   *  @param error the error that occurred during export
   *  @return a message describing the error
   */
  def describe(error: AudioSaveError): String =
    error match
      case AudioSaveError.WriteFailure(path, reason) => s"The file $path could not be written: $reason"
      case AudioSaveError.InvalidTrack(reason) => s"The track cannot be exported: $reason"
      case AudioSaveError.InvalidSample(reason) => s"The track contains an invalid sample: $reason"
      case AudioSaveError.FileTooLarge(reason) => s"The file would be too large: $reason"
      case AudioSaveError.NoTrackLoaded => "There is no track to export. Import a track first."
