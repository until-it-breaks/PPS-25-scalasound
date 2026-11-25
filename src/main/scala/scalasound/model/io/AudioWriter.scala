package scalasound.model.io

import cats.effect.IO
import scalasound.model.domain.AudioTrack
import scalasound.model.io.error.AudioSaveError
import java.nio.file.Path

/** Exports an [[AudioTrack]] to a file. */
trait AudioWriter:

  /** Saves an audio track to the specified file, creating it or overwriting it.
    *
    * @param track
    *   the track to export
    * @param path
    *   the destination file path
    * @return
    *   an effect producing a `Right`, if the file was successfully exported
    *   or a `Left` with an [[AudioSaveError]], if exporting fails
    */
  def save(track: AudioTrack, path: Path): IO[Either[AudioSaveError, Unit]]
