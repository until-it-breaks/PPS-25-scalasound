package scalasound.io

import scalasound.domain.AudioTrack

trait AudioWriter {
  def save(track: AudioTrack, filePath: String): Unit
}

object DummyAudioWriter extends AudioWriter:
  def save(track: AudioTrack, filePath: String): Unit = ???