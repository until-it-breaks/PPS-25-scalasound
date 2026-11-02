package scalasound.io

import java.nio.file.Path
import scalasound.domain.AudioTrack

/** Exports an [[AudioTrack]] to a file. */
trait AudioWriter:

  /** Saves an audio track to the specified file, creating it or overwriting it.
    *
    * @param track the track to export
    * @param path  the destination file path
    * @return `Right(())` if the file was written, or a `Left` containing an
    *         [[AudioSaveError]] describing why the export failed
    */
  def save(track: AudioTrack, path: Path): Either[AudioSaveError, Unit]

object DummyAudioWriter extends AudioWriter:
  def save(track: AudioTrack, path: Path): Either[AudioSaveError, Unit] =
    Left(AudioSaveError.WriteFailure(path, "DummyAudioWriter does not write files"))