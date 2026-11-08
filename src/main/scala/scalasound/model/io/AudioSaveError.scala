package scalasound.model.io

import java.nio.file.Path

/** Represents errors that can occur while exporting an audio track to a file. */
enum AudioSaveError:

  /** The file could not be created or written.
    *
    * @param path   the destination path
    * @param reason a description of the I/O failure
    */
  case WriteFailure(path: Path, reason: String)

  /** The track cannot be represented in the target format.
    *
    * @param reason a description of why the track is not exportable
    */
  case InvalidTrack(reason: String)

  /** A sample cannot be converted to the target sample format.
    *
    * @param reason a description of the offending sample
    */
  case InvalidSample(reason: String)

  /** The resulting file would exceed the size limits of the target format.
    *
    * @param reason a description of the exceeded limit
    */
  case FileTooLarge(reason: String)

  /** There is no track to export. */
  case NoTrackLoaded
