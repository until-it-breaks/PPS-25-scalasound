package scalasound.processing

import scalasound.domain.AudioChunk
import scalasound.domain.AudioChunk.*

// TODO: Scrivere meglio la documentazione con @param, @throws, @return etc...
// TODO: Eliminare i require e gestire in modo diverso.
/**
 * Defines a contract that applies a transformation on an audio block.
 */
trait AudioEffect:
  def apply(chunk: AudioChunk): AudioChunk

/* *** IDENTITY EFFECT *** */
  
/**
 * Effect that does not do anything except
 * returning the input chunk as it is.
 * No effect applied on the chunk.
 */
case class IdentityEffect() extends AudioEffect:
  def apply(chunk: AudioChunk): AudioChunk = chunk

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

/**
 * Effect that applies the Gain effect on the input audio chunk.
 * @param gain defines the strength of the gain.
 *             Values accepted between [0.0f,2.0f].
 * @throws IllegalArgumentException if the gain values is not in the admissible range.
 */
case class GainEffect(gain: Float = GainEffect.DefaultGain) extends AudioEffect:
  require(gain >= GainEffect.MinGain && gain <= GainEffect.MaxGain, "Exceeded the limits for the Gain effect!")

  /*
   * Defines the "clamp" logic necessary to maintain
   * the values inside the domain of the chunk,
   * defined between [-1.0f,1.0f].
   */
  private def clamp(value: Float): Float =   // TODO: Farlo inline il metodo? per evitare l'overhead della chiamata e "inserire" direttamente nel map
    math.max(-1.0f, math.min(1.0f, value))

  def apply(chunk: AudioChunk): AudioChunk =
    chunk match
      case MonoChunk(samples) =>
        MonoChunk(samples.map(x => clamp(x * gain)))
      case StereoChunk(left, right) =>
        StereoChunk(left.map(l => clamp(l * gain)), right.map(r =>  clamp(r * gain)))
        
end GainEffect

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

  // TODO: potrebbe non essere private se viene acceduto anche con l'autopan.
  /*
   * The pan effect can be computed by using sin-cos functions.
   * Mapping the values in the domain [-45°,45°].
   */
  def getPanFactors(radiant: Float): (left: Float, right: Float) =
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
 * Defined new method for the type float
 * to compute to corresponding radiant value.
 */
extension (angle: Float)
  private def toRadiant: Float = (angle * (math.Pi / 180)).toFloat

/**
 * Effect that applies the pan effect on the input audio chunk.
 * @param pan defines the strength of the pan.
 *            Values accepted between [-1.0f,1.0f].
 * @throws IllegalArgumentException if the pan values is not in the admissible range.
 */
case class PanEffect(pan: Float = PanEffect.DefaultPan) extends AudioEffect:
  require(pan >= PanEffect.MinPan && pan <= PanEffect.MaxPan, "Exceeded the limits for the Pan effect!")

  def apply(chunk:  AudioChunk): AudioChunk =

    /*
     * Since the range of admissible angles is between [-45°,45°],
     * to compute the angle by using the pan parameter then the result
     * can be obtained by this ratio:
     * (-1)/(-45) = pan/x , as x the angle to find.
     */
    val angle = pan * 45
    val (leftPan, rightPan) = PanEffect.getPanFactors(angle.toRadiant)

    chunk match
      case MonoChunk(samples) =>
        StereoChunk(samples.map(x => x * leftPan), samples.map(x => x * rightPan))
      case StereoChunk(left, right) =>
        StereoChunk(left.map(x => x * leftPan), right.map(x => x * rightPan))
  
end PanEffect

/* *** VOLUME EFFECT *** */

/*
 * VolumeEffect Companion Object
 * it contains variables (constants) only accessible
 * by the Volume Effect case class.
 */
object VolumeEffect:
  private val DefaultVolume: Float = 1.0f
  private val MinVolume: Float = 0.0f
  private val MaxVolume: Float = 1.0f

// TODO: il volume deve essere applicato come ultimo effetto quando si fa processing.
/**
 * Effect that applies the volume effect on the input audio chunk.
 * @param volume defines the volume of the output.
 *            Values accepted between [0.0f,1.0f].
 * @throws IllegalArgumentException if the volume values is not in the admissible range.
 */
case class VolumeEffect(volume: Float = VolumeEffect.DefaultVolume) extends AudioEffect:
  require(volume >= VolumeEffect.MinVolume && volume <= VolumeEffect.MaxVolume, "Exceeded the limits for the Volume effect!")

  def apply(chunk: AudioChunk): AudioChunk =
    if volume == VolumeEffect.DefaultVolume then chunk
    else
      chunk match
        case MonoChunk(samples) =>
          MonoChunk(samples.map(x => x * volume))
        case StereoChunk(left, right) =>
          StereoChunk(left.map(l => l * volume), right.map(r => r * volume))
          
end VolumeEffect


// TODO: aggiungere anche distorsione??

// TODO: Autopan: θ una funzione del tempo θ(t)= A * sin(2 * pi * f * t)), dove f è la frequenza dell'oscillazione.