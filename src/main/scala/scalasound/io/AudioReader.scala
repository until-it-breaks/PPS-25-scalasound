package scalasound.io

import scalasound.domain.AudioTrack

trait AudioReader {
  def load(filePath: String): AudioTrack
}

object DummyAudioReader extends AudioReader:
  def load(filePath: String): AudioTrack = ???