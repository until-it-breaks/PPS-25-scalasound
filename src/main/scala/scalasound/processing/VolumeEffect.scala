package scalasound.processing

import scalasound.domain.AudioChunk
import scalasound.domain.AudioChunk.{MonoChunk, StereoChunk}

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
/*
 * Effect that applies the volume effect on the input audio chunk.
 * volume defines the strength of the effect where values accepted are between [0.0f,1.0f].
 */
private[processing] case class VolumeEffect(volume: Float = VolumeEffect.DefaultVolume) extends AudioEffect:
  private val isVolumeValid: Boolean = volume >= VolumeEffect.MinVolume && volume <= VolumeEffect.MaxVolume

  override def apply(chunk: AudioChunk): Either[AudioError, AudioChunk] =
    checkConstraints(isVolumeValid, "Exceeded the range [" + VolumeEffect.MinVolume + "," + VolumeEffect.MaxVolume + "]!").
      toLeft({
        if volume == VolumeEffect.DefaultVolume then chunk
        else
          chunk match
            case MonoChunk(samples) =>
              MonoChunk(samples.map(x => x * volume))
            case StereoChunk(left, right) =>
              StereoChunk(left.map(l => l * volume), right.map(r => r * volume))
      })

end VolumeEffect