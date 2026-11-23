package scalasound.model.io

import java.nio.file.Path
import java.util.Locale

/** Extraction and normalisation of file extensions. */
private[io] object FileExtensions:

  /** The normalised extension of the file, given its path.
    *
    * @return
    *   the extension of the file, if any, in lower case;
    *   or `None` if the file has no extension
    */
  def of(path: Path): Option[String] =
    Option(path.getFileName).map(_.toString).flatMap(ofFileName)

  /** The normalised extension of a file name.
    *
    * @param name
    *   the name of the file
    * @return
    *   the extension of the file, if any, in lower case;
    *   or `None` if the file has no extension
    */
  def ofFileName(name: String): Option[String] =
    val stem = name.dropWhile(_ == '.')
    stem.lastIndexOf('.') match
      case -1                            => None
      case dot if dot == stem.length - 1 => None
      case dot                           => Some(normalise(stem.substring(dot + 1)))

  /** The canonical form of an extension.
   *
   * @param extension
   *   the extension to normalise
   * @return
   *   the normalised extension
   */
  def normalise(extension: String): String = extension.toLowerCase(Locale.ROOT)

  /** Checks whether the given extension is well-formed.
   *
   * @param extension
   *   the extension to check
   * @return
   *   `true` if the extension is well-formed;
   *   `false` otherwise
   */
  def isWellFormed(extension: String): Boolean =
    extension.nonEmpty && extension.forall(c => c != '.' && c != '/' && c != '\\' && !c.isWhitespace)
