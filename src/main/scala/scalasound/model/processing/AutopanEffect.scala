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
  private val DefaultFrequency: Int = 0 //HZ for Oscillation Frequency
  private val MinFrequency: Int = 0
  private val MaxFrequency: Int = 20

  // TODO: Fare un solo valore di default?
  /**
   * Extracts the information related to the autopan parameter.
   * @return a ParameterConfiguration containing the autopan configuration.
   */
  def autopanConfiguration: ParameterConfiguration =
    ParameterConfiguration(MinFrequency, MaxFrequency, DefaultFrequency)

  /* Defines the panning factor both for left and right channels. */
  private def panning(frequency: Float, indexSample: Int): (left: Float, right: Float) =
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
    val t = indexSample / frequency
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
private[processing] final case class AutopanEffect(frequency: Int = AutopanEffect.DefaultFrequency) extends TimeAudioEffect:
  private val isFrequencyValid: Boolean = frequency > AutopanEffect.MinFrequency && frequency < AutopanEffect.MaxFrequency
  // TODO: Il check constraint farlo fare al companion object?
  override def apply(chunk: AudioChunk, chunkIndex: Int): Either[AudioError, AudioChunk] =
    checkConstraints(isFrequencyValid, "Exceeded the range [" + AutopanEffect.MinFrequency + ", ∞ )!").
      toLeft({
        val initialSampleIndex = chunkIndex * chunk.frameCount
        val (lch, rch) = chunk match
          case MonoChunk(samples) => (samples, samples)
          case StereoChunk(left, right) => (left, right)

        /* The factor change in time so it varies for each sample. */
        val result = lch.indices.map { i =>
          val panningFactor = panning(frequency.toFloat, initialSampleIndex + i)
          (lch(i) * panningFactor.left, rch(i) * panningFactor.right)
        }

        StereoChunk(result.map(_._1).toVector, result.map(_._2).toVector)
      })