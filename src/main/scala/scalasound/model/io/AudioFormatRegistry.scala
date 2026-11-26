package scalasound.model.io

import scalasound.model.io.error.FormatResolutionError
import scalasound.model.io.error.FormatResolutionError.*

import java.nio.file.Path

/** The writer selected for a destination, with the path to write to.
  *
  * @param writer
  *   the writer that exports the format of the destination
  * @param path
  *   the requested path, with the default extension added, if it was missing
  */
final case class ResolvedWriter(writer: AudioWriter, path: Path)

/** Represents the errors that can occur when building an [[AudioFormatRegistry]]. */
enum AudioFormatRegistryError:

  /** The extension of a format is malformed.
   *
   *  @param format
   *    the name of the format that claims it
   *  @param extension
   *    the malformed extension
   */
  case MalformedExtension(format: String, extension: String)

  /** Several formats claim the same extension, compared ignoring the case.
    *
    * @param extension
    *   the normalised extension
    * @param formats
    *   the names of the formats that claim it
    */
  case DuplicateExtension(extension: String, formats: List[String])

/** Selects the reader or writer for a file from the extension of its path.
  *
  * The registry is immutable. The extension only selects the reader or writer,
  * which has to validate the actual content respects the format.
  *
  * A new format is added by registering one more [[AudioFileType]].
  */
final class AudioFormatRegistry private (
    typesByExtension: Map[String, AudioFileType],
    defaultForSaving: Option[AudioFormatRegistry.SaveDefaultFormat]
):

  /** The reader of the format of the file, given its path.
    *
    * @return
    *   the reader or why no reader was selected
    */
  def readerFor(path: Path): Either[FormatResolutionError, AudioReader] =
    for
      extension <- FileExtensions.of(path).toRight(MissingExtension(path))
      fileType  <- typeOf(extension)
      reader    <- fileType.reader.toRight(ReadingNotSupported(fileType.name))
    yield reader

  /** The writer of the format of the file, given its path.
    *
    * A destination without an extension is not an error: it is saved in the default format, which is
    * the first registered one that has a writer and its extension is appended to the path.
    *
    * @return
    *   the writer with the path to write to or why no writer was selected
    */
  def writerFor(path: Path): Either[FormatResolutionError, ResolvedWriter] =
    FileExtensions.of(path) match
      case Some(extension) =>
        for
          fileType <- typeOf(extension)
          writer   <- fileType.writer.toRight(WritingNotSupported(fileType.name))
        yield ResolvedWriter(writer, path)
      case None => withDefaultFormat(path)

  private def typeOf(extension: String): Either[FormatResolutionError, AudioFileType] =
    typesByExtension.get(extension).toRight(UnsupportedExtension(extension))

  private def withDefaultFormat(path: Path): Either[FormatResolutionError, ResolvedWriter] =
    (for
      default  <- defaultForSaving
      fileName <- Option(path.getFileName)
    yield ResolvedWriter(default.writer, path.resolveSibling(s"$fileName.${default.extension}")))
      .toRight(MissingExtension(path))

object AudioFormatRegistry:

  private[io] final case class SaveDefaultFormat(extension: String, writer: AudioWriter)

  /** Creates a registry of audio formats from the given file types.
    *
    * @return
    *   the registry or the first error found when validating the file types
    */
  def make(fileTypes: AudioFileType*): Either[AudioFormatRegistryError, AudioFormatRegistry] =
    val formats = fileTypes.toList
    val claims =
      for
        fileType  <- formats
        extension <- fileType.allExtensions.map(FileExtensions.normalise).distinct
      yield extension -> fileType

    val malformed =
      for
        fileType  <- formats
        extension <- fileType.allExtensions
        if !FileExtensions.isWellFormed(extension)
      yield AudioFormatRegistryError.MalformedExtension(fileType.name, extension)

    val duplicated = claims
      .groupMap((extension, _) => extension)((_, fileType) => fileType.name)
      .toList
      .sortBy((extension, _) => extension)
      .collect:
        case (extension, names) if names.lengthIs > 1 =>
          AudioFormatRegistryError.DuplicateExtension(extension, names)

    malformed.headOption.orElse(duplicated.headOption).toLeft:
      AudioFormatRegistry(claims.toMap, defaultFormatOf(formats))

  private def defaultFormatOf(formats: List[AudioFileType]): Option[SaveDefaultFormat] =
    formats.collectFirst:
      case AudioFileType(_, primaryExtension, _, _, Some(writer)) =>
        SaveDefaultFormat(FileExtensions.normalise(primaryExtension), writer)
