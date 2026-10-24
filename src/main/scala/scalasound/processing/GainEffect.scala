package scalasound.processing

import scalasound.domain.AudioChunk
import scalasound.domain.AudioChunk.{MonoChunk, StereoChunk}

/* *** GAIN EFFECT *** */

/*
 * GainEffect Companion Object
 * it contains variables (constants) only accessible
 * by the Gain Effect case class.
 *
 */
object GainEffect:
  private val DefaultGain: Float = 1.0f
  private val MinGain: Float = 0.0f
  private val MaxGain: Float = 2.0f

/*
 * Effect that applies the Gain effect on the input audio chunk.
 * Gain defines the strength of the gain where values are accepted between [0.0f,2.0f].
 */
private[processing] case class GainEffect(gain: Float = GainEffect.DefaultGain) extends AudioEffect:
  private val isGainValid: Boolean = gain >= GainEffect.MinGain && gain <= GainEffect.MaxGain

  /*
   * Defines the "clamp" logic necessary to maintain
   * the values inside the domain of the chunk,
   * defined between [-1.0f,1.0f].
   */
  private def clamp(value: Float): Float =   // TODO: Farlo inline il metodo? per evitare l'overhead della chiamata e "inserire" direttamente nel map
    math.max(-1.0f, math.min(1.0f, value))

  def apply(chunk: AudioChunk): Either[AudioError, AudioChunk] =
    checkConstraints(isGainValid, "Exceeded the range [" + GainEffect.MinGain + "," + GainEffect.MaxGain + "]!").toLeft({
      chunk match
        case MonoChunk(samples) =>
          MonoChunk(samples.map(x => clamp(x * gain)))
        case StereoChunk(left, right) =>
          StereoChunk(left.map(l => clamp(l * gain)), right.map(r => clamp(r * gain)))
    })

  /*
   * toLeft is the same to:
   * checkConstraints() match
   *  case Some(_) => AudioErrors
   *  case None => process(AudioChunk)
   */
  end apply

end GainEffect