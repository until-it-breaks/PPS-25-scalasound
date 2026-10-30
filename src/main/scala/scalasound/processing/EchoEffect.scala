package scalasound.processing

import scalasound.domain.AudioChunk
import scalasound.domain.AudioChunk.{MonoChunk, StereoChunk}

/* *** ECHO EFFECT *** */

/*
 * EchoEffect Companion Object
 * it contains variables (constants) only accessible
 * by the Echo Effect case class.
 */
object EchoEffect:
  private val DefaultDelay: Int = 100 //milliseconds
  private val MinDelay: Int = 50 //milliseconds
  private val MaxDelay: Int = 300 //milliseconds
  private val DefaultFrequency: Int = 44100
  private val MinFrequency: Int = 0
  private val DefaultAlpha: Float = 0.5f
  private val MinAlpha: Float = 0.0f
  private val MaxAlpha: Float = 1.0f

/*
 * Effect that applies the echo effect on the input audio chunk.
 * Implementation of the Infinite Impulse Response (IIR) where
 * - alpha is the parameter for attenuation of the echo response,
 * - delay refers to the gap of the original sound plus the previous ones,
 * - frequency refers to the sampling rate in Hz.
 */
private[processing] case class EchoEffect(
                                           alpha: Float = EchoEffect.DefaultAlpha,
                                           delay: Int = EchoEffect.DefaultDelay,
                                           frequency: Int = EchoEffect.DefaultFrequency) extends TimeAudioEffect:
  private val isAlphaValid: Boolean = alpha >= EchoEffect.MinAlpha && alpha <= EchoEffect.MaxAlpha
  private val isSamplesValid: Boolean = delay >= EchoEffect.MinDelay && delay <= EchoEffect.MaxDelay
  private val isFrequencyValid: Boolean = frequency >= EchoEffect.MinFrequency

  /* Number of samples = Delay (in milliseconds) * Frequency (in Hz) */
  private val nSamples: Int = ((delay.toFloat / 1000.0f) * frequency).toInt

  /* Buffers for containing the previous echo effects both for left and right channels. */
  private val echoBufferLeft: Array[Float] = Array.fill(nSamples)(0.0f)
  private val echoBufferRight: Array[Float] = Array.fill(nSamples)(0.0f)

  /*
     * Defines the "clamp" logic necessary to maintain
     * the values inside the domain of the chunk,
     * defined between [-1.0f,1.0f].
     */
  private inline def clamp(value: Float): Float =
    math.max(-1.0f, math.min(1.0f, value))

  /*
   * Applies the echo on the input vector of samples.
   * y(n) = x(n) + alpha * y(n - N)
   */
  private def echoChunk(samples: Vector[Float], chunkIndex: Int, echoBuffer: Array[Float]): Vector[Float] =
    var index = chunkIndex % nSamples
    for
      x <- samples
    yield
      val echo = echoBuffer(index)
      val output = x + alpha * echo
      echoBuffer(index) = output
      index = (index + 1) % nSamples
      clamp(output)

  override def apply(chunk: AudioChunk, chunkIndex: Int): Either[AudioError, AudioChunk] =
    val checkAlpha = checkConstraints(isAlphaValid,
      "Exceeded the range [" + EchoEffect.MinAlpha + "," + EchoEffect.MaxAlpha + "]!")
    val checkSamples = checkConstraints(isSamplesValid,
      "Exceeded the range [" + EchoEffect.MinDelay + "," + EchoEffect.MaxDelay + "]!")
    val checkFrequency = checkConstraints(isFrequencyValid,
      "Exceeded the range [" + EchoEffect.MinFrequency + ", ∞ )!")
    checkAlpha.orElse(checkSamples) match
      case Some(error) => Left(error)
      case None => checkFrequency.toLeft({
        chunk match
          case MonoChunk(samples) =>
            val index = chunkIndex * chunk.frameCount
            val result = echoChunk(samples, index, echoBufferLeft)
              /*samples.map { x =>
              val echo = echoBufferLeft(index)
              val output = x + alpha * echo
              echoBufferLeft(index) = output
              index = (index + 1) % nSamples
              output
              }*/
            MonoChunk(result)
          case StereoChunk(left, right) =>
            val index = chunkIndex * chunk.frameCount
            val leftCh = echoChunk(left, index, echoBufferLeft)
            val rightCh = echoChunk(right, index, echoBufferRight)
            StereoChunk(leftCh, rightCh)
      })