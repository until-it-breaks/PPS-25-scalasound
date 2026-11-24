package scalasound.model.processing.effects

import scalasound.model.domain.AudioChunk
import scalasound.model.processing.{AudioConfiguration, AudioError, StatelessAudioEffect}

/* *** IDENTITY EFFECT *** */
private[processing] case object IdentityEffect extends StatelessAudioEffect, AudioConfiguration:
  override def apply(chunk: AudioChunk): Either[AudioError, AudioChunk] =
    Right(chunk)