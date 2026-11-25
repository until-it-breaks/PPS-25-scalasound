package scalasound.model.io

import cats.effect.IO
import scalasound.model.domain.AudioTrack
import scalasound.model.io.error.AudioLoadError

trait AudioReader:

  /** Loads an audio track from the specified file.
    *
    * @param filePath
    *   the path to the audio file
    * @return
    *   an effect producing a `Right` with the loaded audio track, if loading succeeds
    *   or a `Left` with an [[AudioLoadError]], if loading fails
    */
  def load(filePath: String): IO[Either[AudioLoadError, AudioTrack]]
