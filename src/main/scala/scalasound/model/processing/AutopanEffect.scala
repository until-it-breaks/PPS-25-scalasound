package scalasound.model.processing

import scalasound.model.domain.AudioChunk.{MonoChunk, StereoChunk}
import AutopanEffect.panning
import scalasound.model.domain.AudioChunk

/* *** AUTOPAN EFFECT *** */

/*
 * AutopanEffect Companion Object
 * it contains variables (constants) only accessible
 * by the Autopan Effect case class.
 */
object AutopanEffect extends AudioConfiguration:
  private val DefaultFrequency: Float = 1.0f //HZ for Oscillation Frequency
  private val MinFrequency: Float = 0.0f
  private val MaxFrequency: Float = 20.0f

  /**
   * Extracts the information related to the autopan parameter.
   * @return a ParameterConfiguration containing the autopan configuration.
   */
  def autopanConfiguration: ParameterConfiguration =
    ParameterConfiguration(MinFrequency, MaxFrequency, DefaultFrequency)

  /* Checks if the value is in admissible range. */
  private def isFrequencyValid(frequency: Float): Boolean =
    frequency >= AutopanEffect.MinFrequency &&
      frequency <= AutopanEffect.MaxFrequency

  /* Defines the panning factor both for left and right channels. */
  private def panning(frequency: Float, sampleRate: Float, indexSample: Int): (left: Float, right: Float) =
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
    val t = indexSample / sampleRate
    /* Values are between [-1.0f, 1.0f] where -1.0f is left and 1.0f is right. */
    val theta = math.sin(2 * math.Pi * frequency * t + phase).toFloat

    /*
     * The Panning Law
     * α(t) = (θ(t) + 1) / 2
     * Left channel: L(t) = x(t) · cos((π / 2) · α(t))
     * Right channel: R(t) = x(t) · sin((π / 2) · α(t))
     * */
    val alpha = (theta + 1) / 2
    val leftCos = math.cos(halfPI * alpha).toFloat
    val rightSin = math.sin(halfPI * alpha).toFloat
    (leftCos, rightSin)
  
/*
 * Effect that applies the autopan effect on the input audio chunk.
 * Implementation of the LFO (Low-Frequency-Oscillator) where frequency specifies
 * how fast the sound move from right to left channel.
 */
private[processing] final case class AutopanEffect(frequency: Float, sampleRate: Float) extends TimeAudioEffect:
  override def apply(chunk: AudioChunk, chunkIndex: Int): Either[AudioError, AudioChunk] =
    checkConstraints(AutopanEffect.isFrequencyValid(frequency), 
      "Exceeded the range [" + AutopanEffect.MinFrequency + ", " + AutopanEffect.MaxFrequency + ")!").
      toLeft:
        val initialSampleIndex = chunkIndex * chunk.frameCount
        val (lch, rch) = chunk match
          case MonoChunk(samples) => (samples, samples)
          case StereoChunk(left, right) => (left, right)

        /* The factor change in time so it varies for each sample. */
        val result = for
          i <- lch.indices
          panningFactor = panning(frequency, sampleRate, initialSampleIndex + i)
        yield
          (lch(i) * panningFactor.left, rch(i) * panningFactor.right)

        StereoChunk(result.map(_._1).toVector, result.map(_._2).toVector)