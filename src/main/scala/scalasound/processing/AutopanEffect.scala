package scalasound.processing

import scalasound.domain.AudioChunk
import scalasound.domain.AudioChunk.{MonoChunk, StereoChunk}

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
        /*
         * The LFO position is defined by:
         * θ(t) = sin(2π · f · t + φ)
         * where:
         * - f is the frequency,
         * - t is the time,
         * - φ is the initial phase offset.
         */
        val halfPI = math.Pi / 2
        val phase = halfPI
        /* Values are between [-1.0f, 1.0f] where -1.0f is left and 1.0f is right. */
        val theta = math.sin(2 * math.Pi * frequency /* *chunk.index*/ + phase).toFloat

        /*
         * The Panning Law
         * α(t) = (θ(t) + 1) / 2
         * Left channel: L(t) = x(t) · cos((π / 2) · α(t))
         * Right channel: R(t) = x(t) · sin((π / 2) · α(t))
         * */
        val alpha = (theta + 1) / 2
        val leftCos = math.cos(halfPI * alpha).toFloat
        val rightSin = math.sin(halfPI * alpha).toFloat

        chunk match
          case MonoChunk(samples) =>
            StereoChunk(samples.map(l => l * leftCos), samples.map(r => r * rightSin))
          case StereoChunk(left, right) =>
            StereoChunk(left.map(l => l * leftCos), right.map(r => r * rightSin))
      })
