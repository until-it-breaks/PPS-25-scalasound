package scalasound.model.processing

import scalasound.model.domain.AudioChunk.{MonoChunk, StereoChunk}
import scalasound.model.domain.AudioChunk

/* *** GAIN EFFECT *** */

/*
 * GainEffect Companion Object
 * it contains variables (constants) only accessible
 * by the Gain Effect case class.
 *
 */
private[processing] object GainEffect extends AudioConfiguration:
  private val DefaultGain: Float = 1.0f
  private val MinGain: Float = 0.0f
  private val MaxGain: Float = 2.0f

  /**
   * Extracts the information related to the gain parameter.
   * @return a ParameterConfiguration containing the gain configuration.
   */
  def gainConfiguration: ParameterConfiguration =
    ParameterConfiguration(MinGain, MaxGain, DefaultGain)

  private def isGainValid(gain: Float): Boolean = 
    gain >= GainEffect.MinGain && gain <= GainEffect.MaxGain

/*
 * Effect that applies the Gain effect on the input audio chunk.
 * Gain defines the strength of the gain where values are accepted between [0.0f,2.0f].
 */
private[processing] final case class GainEffect(gain: Float) extends AudioEffect:
  /*
   * Defines the "clamp" logic necessary to maintain
   * the values inside the domain of the chunk,
   * defined between [-1.0f,1.0f].
   */
  private inline def clamp(value: Float): Float =
    math.max(-1.0f, math.min(1.0f, value))

  /*
   * toLeft is the same to:
   * checkConstraints() match
   *  case Some(_) => AudioErrors
   *  case None => process(AudioChunk)
   *
   * If the Option of the checkConstraints
   * is empty then do something.
   */
  override def apply(chunk: AudioChunk): Either[AudioError, AudioChunk] =
    checkConstraints(GainEffect.isGainValid(gain), 
      "Exceeded the range [" + GainEffect.MinGain + "," + GainEffect.MaxGain + "]!").
      toLeft:
        chunk match
          case MonoChunk(samples) =>
            MonoChunk(samples.map(x => clamp(x * gain)))
          case StereoChunk(left, right) =>
            StereoChunk(left.map(l => clamp(l * gain)), right.map(r => clamp(r * gain)))