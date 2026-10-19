package scalasound.processing

import scalasound.domain.AudioChunk

//trait AudioProcessor:
//  def process(chunk: AudioChunk, effects: List[AudioEffect]): AudioChunk
//
//object IdentityAudioProcessor extends AudioProcessor:
//  def process(chunk: AudioChunk, effects: List[AudioEffect]): AudioChunk = chunk

//given Ordering[AudioEffect] = Ordering.by(effect => effect)

object AudioProcessor:
  def process(chunk: AudioChunk, effects: List[AudioEffect]): AudioChunk =
    chunk
//    if effects.isEmpty then
//      chunk 
//    else
//      effects.sorted.foldLeft(chunk)()