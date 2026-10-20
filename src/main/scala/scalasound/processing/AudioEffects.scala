package scalasound.processing

import scalasound.domain.AudioChunk
import scalasound.domain.AudioChunk.*

// TODO: Scrivere meglio la documentazione con @param, @throws, @return etc...

/** Defines a contract that applies a transformation on an audio block. */
// type AudioEffect = AudioChunk => AudioChunk
// In questo caso posso concatenare gli audioeffect con and then.
trait AudioEffect extends (AudioChunk => AudioChunk):
  def apply(chunk: AudioChunk): AudioChunk

/** Applies no effect on the given audio chunk. */
case class IdentityEffect() extends AudioEffect:
  def apply(chunk: AudioChunk): AudioChunk = chunk

// Companion Object
object GainEffect:
  private val DefaultGain: Float = 1.0f
  private val MinGain: Float = 0.0f
  private val MaxGain: Float = 2.0f

/** Applies gain effect on the audio chunk. */
case class GainEffect(gain: Float = GainEffect.DefaultGain) extends AudioEffect:
  // TODO: Vincoli gain appartiene [0.0, 2.0]
  require(gain >= GainEffect.MinGain && gain <= GainEffect.MaxGain, "Exceeded the limits for the Gain effect!")

  // Definisce la logica di "clamp", nel caso in cui i valori ottenuti
  // siano fuori dal dominio [-1,1] del chunk.
  private def clamp(value: Float): Float =   // TODO: Farlo inline il metodo? per evitare l'overhead della chiamata e "inserire" direttamente nel map
    math.max(-1.0f, math.min(1.0f, value))

  def apply(chunk: AudioChunk): AudioChunk =
    chunk match
      case MonoChunk(samples) =>
        MonoChunk(samples.map(x => clamp(x * gain)))
      case StereoChunk(left, right) =>
        StereoChunk(left.map(l => clamp(l * gain)), right.map(r =>  clamp(r * gain)))

// Companion Object
object PanEffect:
  private val DefaultPan: Float = 0.0f
  private val MinPan: Float = -1.0f
  private val MaxPan: Float = 1.0f

  // TODO: potrebbe non essere private se viene acceduto anche con l'autopan.
  def getPanFactors(radiant: Float): (left: Float, right: Float) =
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
    (leftPan, rightPan)

extension (angle: Float)
  private def toRadiant: Float = (angle * (math.Pi / 180)).toFloat

/** Applies the pan effect  */
case class PanEffect(pan: Float = PanEffect.DefaultPan) extends AudioEffect:
  // TODO: Vincoli pan appartiene [-1.0, 1.0]
  require(pan >= PanEffect.MinPan && pan <= PanEffect.MaxPan, "Exceeded the limits for the Pan effect!")

  // Visto che il pan si può applicare tramite funzione seno coseno
  // mappare il dominio nell'intervallo [-45,45]°.
  def apply(chunk:  AudioChunk): AudioChunk = {

    // L'intervallo di angoli scelto è tra [-45°,45°].
    // Il rapporto è: (-1)/(-45) = pan/x con x l'angolo da trovare.
    val angle = pan * 45
    val (leftPan, rightPan) = PanEffect.getPanFactors(angle.toRadiant)

    chunk match
      case MonoChunk(samples) =>
        StereoChunk(samples.map(x => x * leftPan), samples.map(x => x * rightPan))
      case StereoChunk(left, right) =>
        StereoChunk(left.map(x => x * leftPan), right.map(x => x * rightPan))
  }

object VolumeEffect:
  private val DefaultVolume: Float = 1.0f
  private val MinVolume: Float = 0.0f
  private val MaxVolume: Float = 1.0f

// TODO: il volume deve essere applicato come ultimo effetto quando si fa processing.
case class VolumeEffect(volume: Float = VolumeEffect.DefaultVolume) extends AudioEffect:
  require(volume >= VolumeEffect.MinVolume && volume <= VolumeEffect.MaxVolume, "Exceeded the limits for the Gain effect!")

  def apply(chunk: AudioChunk): AudioChunk = {
    if volume == VolumeEffect.DefaultVolume then chunk
    else
      chunk match
        case MonoChunk(samples) =>
          MonoChunk(samples.map(x => x * volume))
        case StereoChunk(left, right) =>
          StereoChunk(left.map(l => l * volume), right.map(r => r * volume))
  }

// TODO: aggiungere anche distorsione??

// TODO: Autopan: θ una funzione del tempo θ(t)= A * sin(2 * pi * f * t)), dove f è la frequenza dell'oscillazione.