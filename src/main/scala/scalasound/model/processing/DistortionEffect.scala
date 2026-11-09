package scalasound.model.processing

import scalasound.model.domain.AudioChunk.{MonoChunk, StereoChunk}
import scalasound.model.domain.AudioChunk

/* *** DISTORTION EFFECT *** */

/*
 * DistortionEffect Companion Object
 * it contains variables (constants) only accessible
 * by the Distortion Effect case class.
 */
object DistortionEffect extends AudioConfiguration:
  private val DefaultDistortion: Float = 0.0f
  private val MinDistortion: Float = 0.0f
  private val MaxDistortion: Float = 2.0f

  /**
   * Extracts the information related to the distortion parameter.
   * @return a ParameterConfiguration containing the distortion configuration.
   */
  def distortionConfiguration: ParameterConfiguration =
    ParameterConfiguration(MinDistortion, MaxDistortion, DefaultDistortion)

/*
 * Effect that applies the distortion effect on the input audio chunk.
 * Distortion defines the strength of the effect where values accepted are between [0.0f,2.0f].
 */
private[processing] final case class DistortionEffect(distortion: Float = DistortionEffect.DefaultDistortion, isSoftClipping: Boolean = true) extends AudioEffect:
  private val isDistortionValid: Boolean = distortion >= DistortionEffect.MinDistortion && distortion <= DistortionEffect.MaxDistortion

  override def apply(chunk: AudioChunk): Either[AudioError, AudioChunk] =
    checkConstraints(isDistortionValid, "Exceeded the range [" + DistortionEffect.MinDistortion + "," + DistortionEffect.MaxDistortion + "]!").
      toLeft:
        applyDistortion(chunk, if isSoftClipping then tanh else sinWave)

  /*
   * Hyperbolic-tangent based formula to apply the
   * SoftClipping Distortion effect.
   */
  private inline def tanh(x: Float): Float =
    math.tanh(x * distortion).toFloat

  /*
   * Sin-based formula to apply the
   * WaveFolding Distortion effect.
   */
  private inline def sinWave(x: Float): Float =
    math.sin(x * distortion).toFloat

  private def applyDistortion(chunk: AudioChunk, function: Float => Float): AudioChunk =
    chunk match
      case MonoChunk(samples) =>
        MonoChunk(samples.map(x => function(x)))
      case StereoChunk(left, right) =>
        StereoChunk(left.map(l => function(l)), right.map(r => function(r)))