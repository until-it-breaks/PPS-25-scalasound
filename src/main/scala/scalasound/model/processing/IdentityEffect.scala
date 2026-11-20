package scalasound.model.processing

import scalasound.model.domain.AudioChunk

/* *** IDENTITY EFFECT *** */
private[processing] case object IdentityEffect extends StatelessAudioEffect, AudioConfiguration:
  override def apply(chunk: AudioChunk): Either[AudioError, AudioChunk] =
    Right(chunk)