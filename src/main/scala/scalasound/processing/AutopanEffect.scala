package scalasound.processing

import scalasound.domain.AudioChunk

object AutopanEffect:
  private val DefaultFrequency: Float = 1.0f
  private val MinFrequency: Float = 0.0f
  private val MaxFrequency: Float = 2.0f
  
/* LFO (Low-Frequency-Oscillator) */  
case class AutopanEffect(frequency: Float = AutopanEffect.DefaultFrequency, sampleRate: Int) extends AudioEffect:
  private val isFrequencyValid: Boolean = frequency >= AutopanEffect.MinFrequency && frequency <= AutopanEffect.MaxFrequency
  
  override def apply(chunk: AudioChunk): Either[AudioError, AudioChunk] =
    checkConstraints(isFrequencyValid, "Exceeded the range [" + AutopanEffect.MinFrequency + "," + AutopanEffect.MaxFrequency + "]!").
      toLeft({
        chunk
      })
