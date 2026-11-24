package scalasound.model.io

/** An audio file format, with its name, extensions and strategies to read and write it.
  *
  * @param name
  *   the name of the format
  * @param primaryExtension
  *   the extension that identifies the format, without the dot (`wav`)
  * @param aliases
  *   other extensions of the same format
  * @param reader
  *   the reader that imports the format, if it can be imported
  * @param writer
  *   the writer that exports the format, if it can be exported
  */
final case class AudioFileType(
    name: String,
    primaryExtension: String,
    aliases: Set[String] = Set.empty,
    reader: Option[AudioReader] = None,
    writer: Option[AudioWriter] = None
):

  private[io] def allExtensions: List[String] = (primaryExtension :: aliases.toList).distinct
