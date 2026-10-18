package scalasound.processing

import scalasound.domain.AudioChunk

trait AudioProcessor {
  def process(chunk: AudioChunk, effects: List[AudioEffect]): AudioChunk
}

object IdentityAudioProcessor extends AudioProcessor:
  def process(chunk: AudioChunk, effects: List[AudioEffect]): AudioChunk = chunk