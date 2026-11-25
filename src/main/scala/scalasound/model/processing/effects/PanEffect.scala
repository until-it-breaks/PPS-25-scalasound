package scalasound.model.processing.effects

import scalasound.model.domain.AudioChunk
import scalasound.model.domain.AudioChunk.{MonoChunk, StereoChunk}
import scalasound.model.processing.*

/* *** PAN EFFECT *** */
private[processing] object PanEffect extends AudioConfiguration:
  private val DefaultPan: Float = 0.0f
  private val MinPan: Float = -1.0f
  private val MaxPan: Float = 1.0f

  /**
   * Extracts the information related to the pan parameter.
   *
   * @return a ParameterConfiguration containing the pan configuration.
   */
  def panConfiguration: ParameterConfiguration =
    ParameterConfiguration(MinPan, MaxPan, DefaultPan)

  private def isPanValid(pan: Float): Boolean =
    pan >= PanEffect.MinPan && pan <= PanEffect.MaxPan

  /*
   * Defined new method for the type float
   * to compute to corresponding radiant value.
   */
  extension (angle: Float)
    inline private def toRadiant: Float = (angle * (math.Pi / 180)).toFloat

  /*
   * The pan effect can be computed by using sin-cos functions.
   * Mapping the values in the domain [-45°,45°].
   */
  private def panFactors(radiant: Float): (left: Float, right: Float) =
    /*
     * leftPan = ((√2)/2) * (cos(θ) - sin(θ))
     * rightPan = ((√2)/2) * (cos(θ) + sin(θ))
     *
     * This approach is applied since typically,
     * θ ranges from [−45°, 45°] with 0° being the center.
     */
    val prefix = (math.sqrt(2) / 2.0).toFloat
    val leftPan = prefix * (math.cos(radiant) - math.sin(radiant)).toFloat
    val rightPan = prefix * (math.cos(radiant) + math.sin(radiant)).toFloat
    (valueRounding(leftPan), valueRounding(rightPan))

  /*
   * Avoid using infinitesimal values obtained during
   * the operations with math.Pi, math.Sin and math.Cos
   * caused by Float conversion from Double.
   */
  private def valueRounding(value: Float): Float =
    if math.abs(value) < 1e-6 then 0.0f else value

/*
 * Effect that applies the pan effect on the input audio chunk.
 * Pan defines the strength of the pan with values are accepted between [-1.0f,1.0f].
 */
private[processing] final case class PanEffect(pan: Float) extends StatelessAudioEffect:

  import PanEffect.*

  private val checkPan = checkConstraints(isPanValid(pan),
    "Exceeded the range [" + MinPan + "," + MaxPan + "]!")

  private val angle = pan * 45
  private val (leftPan, rightPan) = PanEffect.panFactors(angle.toRadiant)

  override def apply(chunk: AudioChunk): Either[AudioError, AudioChunk] =

    /*
     * Since the range of admissible angles is between [-45°,45°],
     * to compute the angle by using the pan parameter then the result
     * can be obtained by this ratio:
     * (-1)/(-45) = pan/x , as x the angle to find.
     */

    checkPan.toLeft:
      chunk match
        case MonoChunk(samples) =>
          StereoChunk(samples.map(_ * leftPan), samples.map(_ * rightPan))
        case StereoChunk(left, right) =>
          StereoChunk(left.map(_ * leftPan), right.map(_ * rightPan))