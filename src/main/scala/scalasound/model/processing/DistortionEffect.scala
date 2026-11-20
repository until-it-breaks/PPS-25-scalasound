package scalasound.model.processing

import scalasound.model.domain.AudioChunk.{MonoChunk, StereoChunk}
import scalasound.model.domain.AudioChunk

/* *** DISTORTION EFFECT *** */
private[processing] object DistortionEffect extends AudioConfiguration:
  private val DefaultDistortion: Float = 1.0f
  private val MinDistortion: Float = 0.0f
  private val MaxDistortion: Float = 10.0f

  /**
   * Extracts the information related to the distortion parameter.
   *
   * @return a ParameterConfiguration containing the distortion configuration.
   */
  def distortionConfiguration: ParameterConfiguration =
    ParameterConfiguration(MinDistortion, MaxDistortion, DefaultDistortion)

  private def isDistortionValid(distortion: Float): Boolean =
    distortion >= DistortionEffect.MinDistortion &&
      distortion <= DistortionEffect.MaxDistortion

/*
 * Effect that applies the distortion effect on the input audio chunk.
 * Distortion defines the strength of the effect where values accepted are between [0.0f,10.0f].
 */
private[processing] final case class DistortionEffect(distortion: Float, isSoftClipping: Boolean) extends StatelessAudioEffect:

  import DistortionEffect.*

  override def apply(chunk: AudioChunk): Either[AudioError, AudioChunk] =
    checkConstraints(isDistortionValid(distortion),
      "Exceeded the range [" + MinDistortion + "," + MaxDistortion + "]!").
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
        MonoChunk(samples.map(function))
      case StereoChunk(left, right) =>
        StereoChunk(left.map(function), right.map(function))