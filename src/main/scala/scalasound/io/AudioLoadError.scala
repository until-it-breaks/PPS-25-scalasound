package scalasound.io

/** Represents errors that can occur while importing an audio file. */
enum AudioLoadError:

  /** The specified file does not exist or cannot be opened.
   *
   *  @param path the path to the file that could not be opened
   */
  case FileNotFound(path: String)

  /** The file is readable, but its format or encoding is not supported.
   *
   *  @param reason a description of why the format or encoding is unsupported
   */
  case UnsupportedFormat(reason: String)

  /** The file is damaged or its audio data is inconsistent.
   *
   *  @param reason a description of the corruption or inconsistency
   */
  case CorruptedData(reason: String)

  /** An I/O error occurred while reading the file.
   *
   *  @param reason a description of the I/O failure
   */
  case ReadFailure(reason: String)