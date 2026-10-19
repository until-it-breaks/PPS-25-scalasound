package scalasound.processing

import scalasound.domain.AudioChunk
import scalasound.domain.AudioChunk.*

/** Defines a contract that applies a transformation on an audio block. */
// type AudioEffect = AudioChunk => AudioChunk
// In questo caso posso concatenare gli audioeffect con and then.
trait AudioEffect extends (AudioChunk => AudioChunk):
  def apply(chunk: AudioChunk): AudioChunk

/** Applies no effect on the given audio chunk. */
case class IdentityEffect() extends AudioEffect:
  def apply(chunk: AudioChunk): AudioChunk = chunk


private object GainConfig:
  val gainDefault: Float = 1.0f
  val minGain: Float = 0.0f
  val maxGain: Float = 2.0f

/** Applies gain effect on the audio chunk. */
case class GainEffect(gain: Float = GainConfig.gainDefault) extends AudioEffect:
  // TODO: Vincoli gain appartiene [0.0, 2.0]
  require(gain >= GainConfig.minGain && gain <= GainConfig.maxGain, "Exceeded the limits for the Gain effect!")

  // Definisce la logica di "clamp" nel caso in cui i valori ottenuti siano fuori dal
  // dominio [-1,1] dei chunk.
  private def clamp(value: Float): Float =
    math.max(-1.0f, math.min(1.0f, value))

  def apply(chunk: AudioChunk): AudioChunk =
    chunk match
      case MonoChunk(samples) =>
        MonoChunk(samples.map(x => clamp(x * gain)))
      case StereoChunk(left, right) =>
        StereoChunk(left.map(l => clamp(l * gain)), right.map(r =>  clamp(r * gain)))


private object PanConfig:
  val panDefault: Float = 0.0f
  val minPan: Float = -1.0f
  val maxPan: Float = 1.0f

extension (angle: Float)
  private def toRadiant: Float = (angle * (math.Pi / 180)).toFloat

/** Applies the pan effect  */
case class PanEffect(pan: Float = PanConfig.panDefault) extends AudioEffect:
  // TODO: Vincoli pan appartiene [-1.0, 1.0]
  require(pan >= PanConfig.minPan && pan <= PanConfig.maxPan, "Exceeded the limits for the Pan effect!")

  // Visto che il pan si può applicare tramite funzione seno coseno
  // mappare il dominio nell'intervallo [-45,45]°.
  def apply(chunk:  AudioChunk): AudioChunk = {

    // L'intervallo di angoli scelto è tra [-45°,45°].
    // Il rapporto è: (-1)/(-45) = pan/x con x l'angolo da trovare.
    val angle = pan * 45
    val radiant = angle.toRadiant

    /*
     * left=((√2)/2)(cosθ+sinθ)
     * right=((√2)/2)(cosθ−sinθ)
     *
     * Typically, θ ranges from [−45°, 45°]
     * with 0° being the center.
     */
    val prefix = (math.sqrt(2) / 2.0).toFloat
    val leftPan = prefix * (math.cos(radiant) + math.sin(radiant)).toFloat
    val rightPan = prefix * (math.cos(radiant) - math.sin(radiant)).toFloat

    chunk match
      case MonoChunk(samples) =>
        StereoChunk(samples.map(x => x * leftPan), samples.map(x => x * rightPan))
      case StereoChunk(left, right) =>
        StereoChunk(left.map(x => x * leftPan), right.map(x => x * rightPan))
  }

// TODO: il volume deve essere applicato come ultimo effetto quando si fa processing.
case class VolumeEffect() extends AudioEffect:
  def apply(chunk: AudioChunk): scalasound.domain.AudioChunk = ???


// TODO: aggiungere anche distorsione??

// TODO: Autopan: θ una funzione del tempo θ(t)= A * sin(2 * pi * f * t)), dove f è la frequenza dell'oscillazione.