## Simone Carpi

### Panoramica dei contributi

Il mio contributo al progetto si è focalizzato nelle seguenti aree:

- **Effetti**: definizione e implementazione degli effetti applicabili ai vari *chunk* e alla singola *traccia*, nello
  specifico:
  `Identity`, `Gain`, `Distortion`, `Echo`, `Volume`, `Pan` e `Autopan`.
- **Processori Audio**: definizione e implementazione di `AudioProcessor`, componente dedito alla gestione e alla
  applicazione sequenziale degli effetti sull'input ricevuto.
- **Controller**: implementazione di `EffectsController` che espone le azioni che l'utente esegue durante l'interazione
  con la traccia: `addEffect` e `removeAll`.
- **AudioADT**: parziale definizione e implementazione degli aspetti principali legati al dominio (`AudioChunk` e 
  `AudioTrack`).
- **Interfaccia grafica**: realizzazione completa dei pannelli che costituiscono la GUI e gestione della logica
  associata agli effetti.
- **Testing**: definizione e implementazione di tutti i test relativi agli effetti, ai processori e al controller.


### Effetti

Gli effetti audio sono trasformazioni matematiche applicate alla traccia presa in carico (costituita da un insieme
di chunk).
Al fine di garantire uniformità all'interno della pipeline di elaborazione gestita da `AudioProcessor`, la gerarchia è
stata definita su un `sealed trait` denominato `AudioEffect`, evitando che venga esteso al di fuori del package `processing`.

```scala
sealed trait AudioEffect:
  protected def checkConstraints(isValid: Boolean, 
                                 msg: String): Option[AudioError] =
    if isValid then Option.empty
    else Some(AudioError.AudioEffectsError(this, msg))
```
\
Vista la necessità di avere effetti legati al tempo, si è considerato di specializzare `AudioEffect` in due
contratti, `trait` separati (applicando l'*Interface Segregation Principle*, **ISP**, e favorendo il *pattern matching* 
in fase di elaborazione audio):

- `StatelessAudioEffect`: definisce l'interfaccia per gli effetti indipendenti dal tempo (`Identity`, `Gain`,
  `Distortion`, `Volume` e `Pan`), esponendo un metodo `apply` che richiede il chunk da elaborare. Inoltre, per
  evitare duplicazione di logica negli effetti, mette a disposizione la *Higher-Order function* `applyTransformation`, 
  che riceve in ingresso una trasformazione (realizzando lo *Strategy Pattern* in FP) e la applica al chunk.
- `TimeAudioEffect`: definisce l'interfaccia per gli effetti temporali (`Echo` e `Autopan`), fornendo un metodo `apply`
  che prende in ingresso il chunk e il suo indice. Quest'ultimo agisce come punto di riferimento
  temporale necessario al calcolo della trasformazione.

```scala
trait StatelessAudioEffect extends AudioEffect:
  def apply(chunk: AudioChunk): Either[AudioError, AudioChunk]

  protected def applyTransformation(chunk: AudioChunk)
                                   (transformation: Float => Float): AudioChunk =
    ...

trait TimeAudioEffect extends AudioEffect:
  def apply(chunk: AudioChunk, chunkIndex: Int): Either[AudioError, AudioChunk]
```

Per evitare di lanciare eccezioni a *runtime*, ogni effetto restituisce un tipo `Either[AudioError, AudioChunk]`. In questo
modo, in caso di eventuali situazioni di errore causate dall'immisione di parametri non validi o al di fuori degli
intervalli consentiti, il calcolo dell'effetto (trasformazione) verrà interrotto tramite il ritorno di un `Left` 
contenente un `AudioError` (modellato tramite gli `enum` di Scala 3). Questo approccio uniforma la gestione degli errori 
tra i singoli effetti e i processori audio, e favorendo distinzione e analisi più approfondita tramite *pattern matching*.

```scala
enum AudioError:
  
  case AudioEffectError(effect: AudioEffect, msg: String)

  case AudioProcessorError(processor: AudioProcessor, msg: String)
```

\
Per poter gestire al meglio la creazione degli effetti (disaccoppiandola dall'effettiva implementazione), si è deciso di applicare il **Factory Pattern** all'interno del
companion object di `AudioEffect`. Questo approccio fornisce un unico punto di ingresso per le chiamate e la relativa istanziazione, riducendo il numero di possibili
dipendenze. Inoltre, questo approccio garantisce che gli unici effetti applicabili sono quelli messi a disposizione da `AudioEffect`. 

```scala
object AudioEffect:

  def identity: StatelessAudioEffect =
    IdentityEffect

  def gain(gainValue: Float): StatelessAudioEffect =
    GainEffect(gainValue)

  def distortion(distortionValue: Float, 
                 isSoftClipping: Boolean = true): StatelessAudioEffect =
    DistortionEffect(distortionValue, isSoftClipping)

  def echo(alpha: Float, 
           delay: Int, 
           sampleRate: Float = DefaultSampleRate): TimeAudioEffect =
    EchoEffect(alpha, delay, sampleRate)

  def pan(panValue: Float): StatelessAudioEffect =
    PanEffect(panValue)

  def autopan(autopanValue: Float, 
              sampleRate: Float = DefaultSampleRate): TimeAudioEffect =
    AutopanEffect(autopanValue, sampleRate)

  def volume(volumeValue: Float): StatelessAudioEffect =
    VolumeEffect(volumeValue)
```

Ogni effetto viene implementato tramite un `case class` (oppure un `case object` per l'`IdentityEffect`), il cui costruttore prende i parametri necessari a modulare la
trasformazione. Di seguito viene mostrata l'implementazione dell'effetto `Gain`.

```scala
private[processing] final case class GainEffect(gain: Float) 
        extends StatelessAudioEffect:

  override def apply(chunk: AudioChunk): Either[AudioError, AudioChunk] =
    checkGain.toLeft:
      applyTransformation(chunk)(x => clamp(x * gain))
  
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

Per preservare la *type-safety* a ciascun effetto, evitando l'uso dei cast espliciti (`.asInstanceOf[...]`),
è stato definito un **enum parametrico**, il cui tipo `+T` lo rende convariante con **Upper Type Bound** `AudioConfiguration`. Siccome
ogni companion object degli effetti estende *AudioConfiguration*, i singoli `case` dell'enum fanno riferimento ai relativi singleton. 
Questo meccanismo viene utilizzato per poter estrarre in modo più diretto e sicuro i
vincoli per i relativi parametri che compongono la trasformazione.

```scala
enum AudioEffectType[+T <: AudioConfiguration](
    val name: String,
    private[processing] val instance: T
):
  case Identity   extends AudioEffectType("Identity", IdentityEffect)
  case Gain       extends AudioEffectType("Gain", GainEffect)
  case Distortion extends AudioEffectType("Distortion", DistortionEffect)
  case Echo       extends AudioEffectType("Echo", EchoEffect)
  case Pan        extends AudioEffectType("Pan", PanEffect)
  case Autopan    extends AudioEffectType("Autopan", AutopanEffect)
  case Volume     extends AudioEffectType("Volume", VolumeEffect)
```

Infine, per evitare di fare *pattern matching* ogni volta che occorre determinare il tipo dell'effetto,
è stato introdotto un *extension method* opportuno su `AudioEffect`, che restituisce il `AudioEffectType` appropriato. 
Sfruttando `PartialFunction.condOpt`, l'effetto in ingresso viene racchiuso in un `Some(AudioEffectType)` se corrisponde ad uno
dei casi noti, mentre restituirà un `None` per tutti gli effetti non specificati.

```scala
  extension (effect: AudioEffect)
    def effectType: Option[AudioEffectType[?]] =
      PartialFunction.condOpt(effect):
        case IdentityEffect      => AudioEffectType.Identity
        case _: GainEffect       => AudioEffectType.Gain
        case _: DistortionEffect => AudioEffectType.Distortion
        case _: EchoEffect       => AudioEffectType.Echo
        case _: PanEffect        => AudioEffectType.Pan
        case _: AutopanEffect    => AudioEffectType.Autopan
        case _: VolumeEffect     => AudioEffectType.Volume
```

### Processori Audio
Il processore audio è il componente responsabile dell'applicazione sequenziale degli effetti sui singoli *chunk* e
viene modellato tramite il `trait` `AudioProcessor`. Il contratto contiene due implementazioni disitinte del metodo `apply`, 
tramite overloading del metodo, per gestire sia singoli chunk sia intere tracce.
A seguito dei controlli preliminari sugli input, la logica di gestione degli effetti sul chunk, risulta la seguente:

```scala
protected def processChunk(chunk: AudioChunk,
                           effects: List[AudioEffect],
                           chunkIndex: Int): Either[AudioError, AudioChunk] =
    effects.foldLeft[Either[AudioError, AudioChunk]](Right(chunk)):
      (currentChunk, effect) =>
        currentChunk.flatMap:
          chunky =>
            effect match
              case timeEffect: TimeAudioEffect => timeEffect(chunky, chunkIndex)
              case standardEffect: StatelessAudioEffect => standardEffect(chunky)
```
Tramite il `foldLeft` vengono applicati in sequenza gli effetti sul *chunk* ricevuto in ingresso e, con il supporto
dell'approccio *monadico* degli `Either`, l'elaborazione si interrompe immediatamente al primo errore propagando un `Left(AudioError)`.
Inoltre, applicando l'exhaustive *pattern matching*, gli effetti vengono distinti in base alla necessità o meno di usare l'indice per
avere il riferimento temporale, permettendo di applicare la trasformazione evitando problemi legati al tipo.


Per l'elaborazione della traccia (`AudioTrack`) è stato applicato un approccio simile a quello precedentemente mostrato, iterando sulla collezione di *chunk* che
compongono la traccia. Tramite una operazione di riduzione con accumulatore (`foldLeft`), su ogni elemento viene invocato il metodo protetto `processChunk` per applicare i filtri. 
Questo procedimento permette di creare una nuova lista inserendo i chunk in testa (per mezzo dell'operatore *cons* `::`), riducendone il costo computazionale con inserimento a complessità costante,
e sfrutta le *for-comprehension* per ottenere un approccio più dichiarativo ed incisivo. Questo zucchero sintattico permette di gestire gli `Either`, estraendo in modo sicuro i `Right(AudioChunk)`
e propagando eventuali `Left(AudioError)`.

```scala
private def processedAudioTrack(track: AudioTrack, 
                                effects: List[AudioEffect]
                               ): Either[AudioError, (List[AudioChunk], Int)] =
  track.chunksAsList.
    foldLeft[Either[AudioError, (List[AudioChunk], Int)]](Right((Nil, 0))):
      (accumulator, currentChunk) =>
        for
          (acc, idx) <- accumulator
          chunk <- processChunk(currentChunk, effects, idx)
        yield
          (chunk :: acc, idx + 1)
```

\
Per quanto riguarda il `trait` `AudioProcessor`, questo viene specializzato in due sottotipi distinti: 
`SimpleAudioProcessor` e `AudioProcessorWithRules`, entrambi definiti come implementazioni concrete stateless modellate 
come *singleton* (`object`).

Nel processore `AudioProcessorWithRules`, al fine di applicare secondo una logica gli effetti selezionati dall'utente, è stato
definito un ordinamento interno sugli effetti disponibili. Per realizzare questo meccanismo, è stato introdotto un `enum` privato (`EffectPriority`), utilizzando la proprietà `.ordinal` (che identifica l'indice
numerico dell'effetto all'interno dell'enum), creando una associazione effetto-priorità. Inoltre, con la *contextual abstraction* di Scala 3, ovvero
il meccanismo dei `given`, è stata fornita una istanza di `Ordering[AudioEffect]` che viene passata implicitamente dal compilatore al metodo `.sorted` delle liste per ordinare gli effetti ricevuti.

```scala
private enum EffectPriority:
  case IdentityEffect
  case GainEffect
  ...

private[processing] object AudioProcessorWithRules extends AudioProcessor:

  given Ordering[AudioEffect] = Ordering.by(orderingByPriority)

  private def orderingByPriority(effect: AudioEffect): Int = effect match
    case IdentityEffect => EffectPriority.IdentityEffect.ordinal
    case _: GainEffect => EffectPriority.GainEffect.ordinal
    ...
  
  private def sortEffects(effects: List[AudioEffect]): List[AudioEffect] =
    if effects.length <= 1 then effects else effects.sorted
  
  override protected def processChunk(chunk: AudioChunk,
                                      effects: List[AudioEffect],
                                      chunkIndex: Int): Either[AudioError, AudioChunk] =
    super.processChunk(chunk, sortEffects(effects), chunkIndex)
  
  override protected def processTrack(track: AudioTrack,
                                      effects: List[AudioEffect]
                                     ): Either[AudioError, AudioTrack] =
    super.processTrack(track, sortEffects(effects))
```

Infine, come già fatto con `AudioEffect`, è stato introdotto un *companion object* per `AudioProcessor` così da fornire
un unico punto di accesso per la creazione delle sue istanze: `SimpleAudioProcessor` e `AudioProcessorWithRules`.

```scala
object AudioProcessor:
  def simple: AudioProcessor = SimpleAudioProcessor
  def withRules: AudioProcessor = AudioProcessorWithRules
```

### Controller
`EffectsController` è un `trait` che definisce due metodi: `addEffect` e `removeAll`, per la gestione della traccia da parte
dell'utente. Al fine di nascondere eventuali dettagli implementativi, il controller viene creato esclusivamente tramite 
una chiamata al metodo `apply` del *companion object* che restituisce un'implementazione privata (`EffectsControllerImpl`).
Il controller agisce come mediatore verso l'`AppStateStore`, delegando le operazioni al metodo `dispatch`.

```scala
trait EffectsController:
  def addEffect(effect: AudioEffect): IO[Unit]
  def removeAll(): IO[Unit]
  
object EffectsController:
  def apply(store: AppStateStore): EffectsController =
    EffectsControllerImpl(store)
  
private final class EffectsControllerImpl(store: AppStateStore) extends EffectsController:

  override def addEffect(effect: AudioEffect): IO[Unit] =
    store.dispatch(AddEffect(effect)).void

  override def removeAll(): IO[Unit] =
    store.dispatch(RemoveAllEffects).void
```

### Logica degli effetti (GUI)
`EffectsLogic` si occupa di gestire i widget, nello specifico sliders e bottoni, degli effetti che l'utente può applicare in tempo reale all'interno dell'applicazione.
Il compito di questa componente è di inviare al relativo controller (`EffectsController`) le azioni dell'utente e adattare i cambiamenti di stato nelle *label* dei widget.
Data la massiccia presenza di *slider* nel pannello, per evitare duplicazioni di codice è stato aggiunto un **extension method** incapsulato nel *companion object* di `EffectsLogic`:

```scala
  extension (slider: Slider)
    private def sliderValue: Float = slider.value.value.toFloat

    private def sliderListener(onUpdate: Float => Unit): Unit =
      slider.valueChanging.onChange { (_, wasChanging, isChanging) =>
        if wasChanging && !isChanging then onUpdate(slider.sliderValue)
      }
      slider.onMouseReleased = _ =>
        if !slider.valueChanging.value then onUpdate(slider.sliderValue)
```
Il metodo `sliderListener`, definito come una **Higher-Order functions**, accetta una funzione di callback (`onUpdate: Float => Unit`).
Questo approccio permette ai singoli componenti di poter definire l'azione da eseguire quando un evento viene intercettato. Per evitare di inviare un segnale
continuo durante il movimento cursore, è stata combinata la proprietà `valueChanging` che  si occupa di analizzare `wasChanging && !isChanging` al termine di un trascinamento,
con l'evento `onMouseReleased` e il controllo di `!slider.valueChanging.value` per gestire i clic diretti. In questo modo, l'invio del comando al controller *wrapped* viene attivato **una sola volta** a interazione completata.

Inoltre, per rendere più comprensibile la presenza del filtro di *SoftClipping*, è stata introdotta un'ulteriore *extension* denominata `isSoftClipping`,
la quale mappa il filtro `DistortionType` di input in un valore booleano.

```scala
  extension (filter: DistortionType)
    private def isSoftClipping: Boolean = filter == Tanh
```

\newpage