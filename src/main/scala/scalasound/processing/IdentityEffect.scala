package scalasound.processing

import scalasound.domain.AudioChunk

/* *** IDENTITY EFFECT *** */

/*
 * Effect that does not do anything except
 * returning the input chunk as it is.
 * No effect applied on the chunk.
 */
private[processing] case class IdentityEffect() extends AudioEffect:
  def apply(chunk: AudioChunk): Either[AudioError, AudioChunk] =
    Right(chunk)