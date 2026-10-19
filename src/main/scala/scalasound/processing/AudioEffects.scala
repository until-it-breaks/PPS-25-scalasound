package scalasound.processing

import scalasound.domain.AudioChunk

// TODO: Definire un file per l'approccio matematico.

/** Defines a contract that applies a transformation on an audio block. */
/* type AudioEffect = AudioChunk => AudioChunk */ // Così non si alloca niente
trait AudioEffect:
  def apply(chunk: AudioChunk): AudioChunk

/** Applies no effect on the given audio chunk. */
case class IdentityEffect() extends AudioEffect {
  def apply(chunk: AudioChunk): AudioChunk = chunk
}

val gainDefault = 1.0f;
/** Applies gain effect on the audio chunk. */
case class GainEffect(gain: Float = gainDefault) extends AudioEffect {
  // TODO: Vincoli gain appartiene [0.0, 2.0]
  def apply(chunk: AudioChunk): AudioChunk = {
    require(gain >= 0.0f && gain <= 2.0f, "Exceeded the limits for the Gain effect!")
    chunk
  }
}

val panDefault = 0.0f
/** Applies the pan effect  */
case class PanEffect(pan: Float = panDefault) extends AudioEffect {
  // TODO: Vincoli pan appartiene [-1.0, 1.0]
  def apply(chunk:  AudioChunk): AudioChunk = {
    require(pan >= -1.0f && pan <= 1.0f, "Exceeded the limits for the Pan effect!")
    chunk
  }
}

// TODO: il volume deve essere applicato come ultimo effetto quando si fa processing.
case class VolumeEffect() extends AudioEffect {
  def apply(chunk: AudioChunk): scalasound.domain.AudioChunk = ???
}