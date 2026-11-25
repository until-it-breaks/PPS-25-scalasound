package scalasound.model.io.error

import java.nio.file.Path

/** Represents the errors that can occur when selecting a format for a file. */
enum FormatResolutionError:

  /** The file name has no extension, so no format can be selected.
    *
    * @param path
    *   the path without an extension
    */
  case MissingExtension(path: Path)

  /** No registered format uses the extension.
    *
    * @param extension
    *   the normalised extension of the path
    */
  case UnsupportedExtension(extension: String)

  /** The format is registered, but it can only be written.
    *
    * @param format
    *   the name of the format
    */
  case ReadingNotSupported(format: String)

  /** The format is registered, but it can only be read.
    *
    * @param format
    *   the name of the format
    */
  case WritingNotSupported(format: String)
