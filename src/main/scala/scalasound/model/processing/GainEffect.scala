package scalasound.model.processing

import scalasound.model.domain.AudioChunk

/* *** GAIN EFFECT *** */
private[processing] object GainEffect extends AudioConfiguration:
  private val DefaultGain: Float = 1.0f
  private val MinGain: Float = 0.0f
  private val MaxGain: Float = 2.0f

  /**
   * Extracts the information related to the gain parameter.
   *
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
private[processing] final case class GainEffect(gain: Float) extends StatelessAudioEffect:

  import GainEffect.*

  private val checkGain = checkConstraints(isGainValid(gain),
    "Exceeded the range [" + MinGain + "," + MaxGain + "]!")
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
    checkGain.toLeft:
      applyTransformation(chunk, x => clamp(x * gain))

  /*
   * Defines the "clamp" logic necessary to maintain
   * the values inside the domain of the chunk,
   * defined between [-1.0f,1.0f].
   */
  private inline def clamp(value: Float): Float =
    math.max(-1.0f, math.min(1.0f, value))