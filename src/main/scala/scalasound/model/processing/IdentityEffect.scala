package scalasound.model.processing

import scalasound.model.domain.AudioChunk

/* *** IDENTITY EFFECT *** */

/*
 * Effect that does not do anything except
 * returning the input chunk as it is.
 * No effect applied on the chunk.
 */
private[processing] case object IdentityEffect extends AudioEffect, AudioConfiguration:
  override def apply(chunk: AudioChunk): Either[AudioError, AudioChunk] =
    Right(chunk)