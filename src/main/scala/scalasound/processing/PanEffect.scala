package scalasound.processing

import scalasound.domain.AudioChunk
import scalasound.domain.AudioChunk.{MonoChunk, StereoChunk}

/* *** PAN EFFECT *** */

/*
 * PanEffect Companion Object
 * it contains variables (constants) only accessible
 * by the Pan Effect case class.
 * Also, a static method is defined to compute
 * the pan factor for both left and right channels.
 */
object PanEffect:
  private val DefaultPan: Float = 0.0f
  private val MinPan: Float = -1.0f
  private val MaxPan: Float = 1.0f

  /*
   * Defined new method for the type float
   * to compute to corresponding radiant value.
   */
  extension (angle: Float)
    private def toRadiant: Float = (angle * (math.Pi / 180)).toFloat

  /*
   * The pan effect can be computed by using sin-cos functions.
   * Mapping the values in the domain [-45°,45°].
   */
  private def getPanFactors(radiant: Float): (left: Float, right: Float) =
    /*
     * leftPan = ((√2)/2) * (cos(θ) + sin(θ))
     * rightPan = ((√2)/2) * (cos(θ) − sin(θ))
     *
     * This approach is applied since typically,
     * θ ranges from [−45°, 45°] with 0° being the center.
     */
    val prefix = (math.sqrt(2) / 2.0).toFloat
    val leftPan = prefix * (math.cos(radiant) + math.sin(radiant)).toFloat
    val rightPan = prefix * (math.cos(radiant) - math.sin(radiant)).toFloat
    (leftPan, rightPan)

end PanEffect

/*
 * Effect that applies the pan effect on the input audio chunk.
 * Pan defines the strength of the pan with values are accepted between [-1.0f,1.0f].
 */
private[processing] case class PanEffect(pan: Float = PanEffect.DefaultPan) extends AudioEffect:
  private val isPanValid: Boolean = pan >= PanEffect.MinPan && pan <= PanEffect.MaxPan

  import PanEffect.toRadiant

  def apply(chunk:  AudioChunk): Either[AudioError, AudioChunk] =

    /*
     * Since the range of admissible angles is between [-45°,45°],
     * to compute the angle by using the pan parameter then the result
     * can be obtained by this ratio:
     * (-1)/(-45) = pan/x , as x the angle to find.
     */
    checkConstraints(isPanValid, "Exceeded the range [" + PanEffect.MinPan + "," + PanEffect.MaxPan + "]!").toLeft({
      val angle = pan * 45
      val (leftPan, rightPan) = PanEffect.getPanFactors(angle.toRadiant)

      chunk match
        case MonoChunk(samples) =>
          StereoChunk(samples.map(x => x * leftPan), samples.map(x => x * rightPan))
        case StereoChunk(left, right) =>
          StereoChunk(left.map(x => x * leftPan), right.map(x => x * rightPan))
    })

end PanEffect