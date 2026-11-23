package scalasound.model.processing

import scalasound.model.domain.AudioChunk

/* *** VOLUME EFFECT *** */
private[processing] object VolumeEffect extends AudioConfiguration:
  private val DefaultVolume: Float = 1.0f
  private val MinVolume: Float = 0.0f
  private val MaxVolume: Float = 1.0f

  /**
   * Extracts the information related to the volume parameter.
   *
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
private[processing] final case class VolumeEffect(volume: Float) extends StatelessAudioEffect:

  import VolumeEffect.*

  private val checkVolume = checkConstraints(isVolumeValid(volume),
    "Exceeded the range [" + MinVolume + "," + MaxVolume + "]!")

  override def apply(chunk: AudioChunk): Either[AudioError, AudioChunk] =
    checkVolume.toLeft:
      if volume == DefaultVolume then chunk
      else applyTransformation(chunk, x => x * volume)