package scalasound.model.io

import cats.effect.IO
import scalasound.model.domain.AudioTrack
import scalasound.model.io.error.AudioLoadError
import java.nio.file.Path

trait AudioReader:

  /** Loads an audio track from the specified file.
    *
    * @param path
    *   the path to the audio file
    * @return
    *   an effect producing a `Right` with the loaded audio track, if loading succeeds
    *   or a `Left` with an [[AudioLoadError]], if loading fails
    */
  def load(path: Path): IO[Either[AudioLoadError, AudioTrack]]
