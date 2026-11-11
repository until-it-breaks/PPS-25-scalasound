package scalasound.model.processing

import scalasound.model.domain.AudioChunk.{MonoChunk, StereoChunk}
import scalasound.model.domain.AudioChunk

/* *** VOLUME EFFECT *** */

/*
 * VolumeEffect Companion Object
 * it contains variables (constants) only accessible
 * by the Volume Effect case class.
 */
object VolumeEffect extends AudioConfiguration:
  private val DefaultVolume: Float = 1.0f
  private val MinVolume: Float = 0.0f
  private val MaxVolume: Float = 1.0f

  /**
   * Extracts the information related to the volume parameter.
   * @return a ParameterConfiguration containing the volume configuration.
   */
  def volumeConfiguration: ParameterConfiguration =
    ParameterConfiguration(MinVolume, MaxVolume, DefaultVolume)

  private def isVolumeValid(volume: Float): Boolean = 
    volume >= VolumeEffect.MinVolume && 
      volume <= VolumeEffect.MaxVolume

/*
 * Effect that applies the volume effect on the input audio chunk.
 * Volume defines the strength of the effect where values accepted are between [0.0f,1.0f].
 */
private[processing] final case class VolumeEffect(volume: Float) extends AudioEffect:

  override def apply(chunk: AudioChunk): Either[AudioError, AudioChunk] =
    checkConstraints(VolumeEffect.isVolumeValid(volume), 
      "Exceeded the range [" + VolumeEffect.MinVolume + "," + VolumeEffect.MaxVolume + "]!").
      toLeft:
        if volume == VolumeEffect.DefaultVolume then chunk
        else
          chunk match
            case MonoChunk(samples) =>
              MonoChunk(samples.map(x => x * volume))
            case StereoChunk(left, right) =>
              StereoChunk(left.map(l => l * volume), right.map(r => r * volume))