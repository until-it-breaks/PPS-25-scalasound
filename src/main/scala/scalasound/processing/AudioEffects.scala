package scalasound.processing

import scalasound.domain.AudioChunk

trait AudioEffect:
  def apply(chunk: AudioChunk): AudioChunk

case class IdentityEffect() extends AudioEffect {
  def apply(chunk: AudioChunk): AudioChunk = chunk
}