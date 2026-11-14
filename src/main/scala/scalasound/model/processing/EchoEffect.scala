package scalasound.model.processing

import scalasound.model.domain.AudioChunk.{MonoChunk, StereoChunk}
import scalasound.model.domain.AudioChunk

/* *** ECHO EFFECT *** */

/*
 * EchoEffect Companion Object
 * it contains variables (constants) only accessible
 * by the Echo Effect case class.
 */
private[processing] object EchoEffect extends AudioConfiguration:
  private val DefaultDelay: Int = 100 //milliseconds
  private val MinDelay: Int = 10 //milliseconds
  private val MaxDelay: Int = 600 //milliseconds
  private val DefaultSampleRate: Int = 44100
  private val MinSampleRate: Int = 0
  private val DefaultAlpha: Float = 0.5f
  private val MinAlpha: Float = 0.0f
  private val MaxAlpha: Float = 0.9f

  /**
   * Extracts the information related to the delay parameter.
   * @return a ParameterConfiguration containing the delay configuration.
   */
  def delayConfiguration: ParameterConfiguration =
    ParameterConfiguration(MinDelay.toFloat, MaxDelay.toFloat, DefaultDelay.toFloat)

  /**
   * Extracts the information related to the alpha parameter.
   * @return a ParameterConfiguration containing the alpha configuration.
   */
  def alphaConfiguration: ParameterConfiguration =
    ParameterConfiguration(MinAlpha, MaxAlpha, DefaultAlpha)

  private def isAlphaValid(alpha: Float): Boolean =
    alpha >= EchoEffect.MinAlpha &&
      alpha <= EchoEffect.MaxAlpha

  private def isDelayValid(delay: Int): Boolean =
    delay > EchoEffect.MinDelay &&
      delay <= EchoEffect.MaxDelay

  private def isSampleRateValid(sampleRate: Float): Boolean =
    sampleRate > EchoEffect.MinSampleRate

/*
 * Effect that applies the echo effect on the input audio chunk.
 * Implementation of the Infinite Impulse Response (IIR) where
 * - alpha is the parameter for attenuation of the echo response,
 * - delay refers to the gap of the original sound plus the previous ones,
 * - sampleRate refers to the sampling rate in Hz.
 */
private[processing] final case class EchoEffect(alpha: Float,
                                                delay: Int,
                                                sampleRate: Float) extends TimeAudioEffect:

  private val checkAlpha = checkConstraints(EchoEffect.isAlphaValid(alpha),
    "Exceeded the range [" + EchoEffect.MinAlpha + "," + EchoEffect.MaxAlpha + "]!")
  private val checkSamples = checkConstraints(EchoEffect.isDelayValid(delay),
    "Exceeded the range [" + EchoEffect.MinDelay + "," + EchoEffect.MaxDelay + "]!")
  private val checkFrequency = checkConstraints(EchoEffect.isSampleRateValid(sampleRate),
    "Exceeded the range [" + EchoEffect.MinSampleRate + ", ∞ )!")

  /* Number of samples = Delay (in milliseconds) * Frequency (in Hz) */
  private val nSamples: Int = math.max(1, ((delay.toFloat / 1000.0f) * sampleRate).toInt)

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
  private def echoEffectOnChunk(samples: Vector[Float],
                        chunkIndex: Int,
                        echoBuffer: Array[Float]): Vector[Float] =
    val startingIndex = chunkIndex % nSamples
    val (resultList, _) = samples.foldLeft((List.empty[Float], startingIndex)) {
      case ((accumulator, currentIndex), x) =>
        val echo = echoBuffer(currentIndex)
        val output = clamp(x + alpha * echo)
        echoBuffer(currentIndex) = output
        val nextIndex = (currentIndex + 1) % nSamples
        (output :: accumulator, nextIndex) // Prepend
    }
    // It is necessary to reverse since the first sample is now the last in the list.
    resultList.reverse.toVector

  override def apply(chunk: AudioChunk, chunkIndex: Int): Either[AudioError, AudioChunk] =
    checkAlpha.orElse(checkSamples).orElse(checkFrequency) match
      case Some(error) => Left(error)
      case None => Right(resultChunk(chunk, chunkIndex * chunk.frameCount))

  private def resultChunk(chunk: AudioChunk, index: Int): AudioChunk =
    chunk match
      case MonoChunk(samples) =>
        val result = echoEffectOnChunk(samples, index, echoBufferLeft)
        MonoChunk(result)
      case StereoChunk(left, right) =>
        val leftCh = echoEffectOnChunk(left, index, echoBufferLeft)
        val rightCh = echoEffectOnChunk(right, index, echoBufferRight)
        StereoChunk(leftCh, rightCh)