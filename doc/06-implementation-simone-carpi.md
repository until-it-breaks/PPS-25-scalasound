## Implementazione - Simone Carpi

### Panoramica dei contributi

Il mio contributo al progetto si è focalizzato nelle seguenti aree:

- **Effetti**: definizione e implementazione degli effetti applicabili ai vari *chunk* e alla singola *traccia*, nello
  specifico:
  `Identity`, `Gain`, `Distortion`, `Echo`, `Volume`, `Pan` e `Autopan`.
- **Processori Audio**: definizione e implementazione di `AudioProcessor`, componente dedito alla gestione e alla
  applicazione sequenziale degli effetti sull'input ricevuto.
- **Controller**: implementazione di `EffectsController` che espone le azione che l'utente esegue durante l'interazione
  con la traccia.
- **AudioADT**: parziale definizione e implementazione degli aspetti principali legati al dominio.
- **Interfaccia grafica**: realizzazione completa dei pannelli che costituiscono la GUI e gestione della logica
  associata agli effetti.
- **Testing**: definizione e implmentazione di tutti i test relativi agli effetti, ai processori e al controller.

---

### Effetti

Gli effetti audio trasformazioni matematiche applicate alla traccia presa in carico (costituita da un insieme
di chunk).
Al fine di garantire uniformità all'interno della pipeline di elaborazione gestita da `AudioProcessor`, la gerarchia è
stata definita su un `sealed trait` denominato `AudioEffect`,
consentendo il *pattern matching* in fase di elaborazione, applicando l'**Interface Segregation Principle**.
Infatti, a causa della presenza di effetti legati al tempo, si è considerato di specializzare ulteriormente `AudioEffect` in due
contratti, `trait` separati:

- `StatelessAudioEffect`: definisce l'interfaccia per gli effetti indipendenti dal tempo (`Identity`, `Gain`,
  `Distortion`, `Volume` e `Pan`), esponendo un metodo `apply` che richiede il chunk da elaborare.
- `TimeAudioEffect`: definisce l'interfaccia per gli effetti temporali (`Echo` e `Autopan`) e fornendo un metodo `apply`
  che prende in ingresso il chunk e il suo indice. Quest'ultimo agisce come punto di riferimento
  temporale necessario al calcolo della trasformazione.

Per evitare di lanciare eccezioni a *runtime* ogni effetto restituisce un tipo `Either[AudioError, AudioChunk]`. In questo
modo, in caso di eventuali situazioni di errore causate dall'immisione di parametri non validi o al di fuori degli
intervalli
consentiti, il calcolo dell'effetto (trasformazione) verrà interrotto tramite il ritorno di un `Left` contenente un
`AudioError` (modellato tramite gli `enum` di Scala 3).

```scala
sealed trait AudioEffect:
  protected def checkConstraints(isValid: Boolean, msg: String): Option[AudioError] =
    if isValid then Option.empty
    else Some(AudioError.AudioEffectsError(this, msg))

trait StatelessAudioEffect extends AudioEffect:
  def apply(chunk: AudioChunk): Either[AudioError, AudioChunk]

trait TimeAudioEffect extends AudioEffect:
  def apply(chunk: AudioChunk, chunkIndex: Int): Either[AudioError, AudioChunk]
```

Per poter gestire al meglio la creazione degli effetti (disaccoppiandola dall'effettiva implementazione, si è deciso di applicare il **Factory Pattern** all'interno del
companion object di `AudioEffect`. Questo approccio fornisce un unico punto di ingresso per le chiamate e la relativa istanziazione, riducendo il numero di possibili
dipendenze.

```scala
object AudioEffect:

  def identity: StatelessAudioEffect =
    IdentityEffect

  def gain(gainValue: Float): StatelessAudioEffect =
    GainEffect(gainValue)

  def distortion(distortionValue: Float, isSoftClipping: Boolean = true): StatelessAudioEffect =
    DistortionEffect(distortionValue, isSoftClipping)

  def echo(alpha: Float, delay: Int, sampleRate: Float = 44100f): TimeAudioEffect =
    EchoEffect(alpha, delay, sampleRate)

  def pan(panValue: Float): StatelessAudioEffect =
    PanEffect(panValue)

  def autopan(autopanValue: Float, sampleRate: Float = 44100f): TimeAudioEffect =
    AutopanEffect(autopanValue, sampleRate)

  def volume(volumeValue: Float): StatelessAudioEffect =
    VolumeEffect(volumeValue)
```

Ogni effetto viene implementato tramite un `case class` (oppure un `case object` per l'`IdentityEffect`), il cui costruttore prende i parametri necessari a modulare la
trasformazione. Di seguito viene mostrata l'implementazione dell'effetto `Gain`.

```scala
private[processing] final case class GainEffect(gain: Float) extends StatelessAudioEffect:

  override def apply(chunk: AudioChunk): Either[AudioError, AudioChunk] =
    checkGain.toLeft:
      applyTransformation
  (chunk, x => clamp(x * gain))
  
  ...
```

A fianco di ogni `case class` è stato definito un proprio **Companion Object**, incaricato di contenere costanti, metodi
di utilità, e il range accettabile dei parametri inseriti per il corrispettivo effetto. Grazie all'adozione del
*Factory Pattern* e al modificatore di visibilità dei relativi effetti `private[processing]`, i dettagli
implementativi risultano incapsulati all'interno del package *processing*.

```scala
private[processing] object GainEffect extends AudioConfiguration:
  private val DefaultGain: Float = 1.0f
  private val MinGain: Float = 0.0f
  private val MaxGain: Float = 2.0f

  def gainConfiguration: ParameterConfiguration =
    ParameterConfiguration(MinGain, MaxGain, DefaultGain)

  ...
```

Infine, per preservare la *type-safety* a ciascun effetto, evitando l'uso dei cast espliciti (`.asInstanceOf[...]`),
è stato definito un **enum parametrico**, il cui tipo `+T` lo rende convariante con **Upper Type Bound** `AudioConfiguration`. Siccome
ogni companion object degli effetti estende *AudioConfiguration*, i singoli `case` dell'enum fanno riferimento ai relativi singleton. 
Questo meccanismo viene utilizzato per poter estrarre in modo più diretto e sicuro i
vincoli per i relativi parametri che compongono la trasformazione.

```scala
enum AudioEffectType[+T <: AudioConfiguration](val name: String, val instance: T):
  case Identity extends AudioEffectType("Identity", IdentityEffect)
  case Gain extends AudioEffectType("Gain", GainEffect)
  case Distortion extends AudioEffectType("Distortion", DistortionEffect)
  case Echo extends AudioEffectType("Echo", EchoEffect)
  case Pan extends AudioEffectType("Pan", PanEffect)
  case Autopan extends AudioEffectType("Autopan", AutopanEffect)
  case Volume extends AudioEffectType("Volume", VolumeEffect)
```

### Processori Audio
I processori audio sono stati definiti in due modalità: `SimpleAudioProcessor` e `AudioProcessorWithRules`

### Controller degli Effetti

### Logica degli Effetti (GUI)