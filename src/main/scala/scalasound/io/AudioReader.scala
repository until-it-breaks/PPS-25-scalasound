package scalasound.io

import scalasound.domain.AudioTrack

trait AudioReader:

  /** Loads an audio track from the specified file.
   *
   *  @param filePath the path to the audio file
   *  @return a `Right` containing the loaded audio track if loading succeeds,
   *          or a `Left` containing an [[AudioLoadError]] if loading fails
   */
  def load(filePath: String): Either[AudioLoadError, AudioTrack]

object DummyAudioReader extends AudioReader:
  def load(filePath: String): Either[AudioLoadError, AudioTrack] =
    Left(AudioLoadError.UnsupportedFormat("DummyAudioReader does not load files"))