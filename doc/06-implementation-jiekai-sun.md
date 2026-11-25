## Jiekai Sun

### Panoramica dei contributi

Il mio contributo al progetto si è focalizzato nelle seguenti aree:

- **Stato**: definizione e implementazione di `AppState` e `AppStateStore`, principalmente per quanto concerne il controllo di riproduzione.
- **Motore di riproduzione**: definizione di `AudioPlayer` e l'implementazione `StreamingAudioPlayer`.
- **Output audio**: definizione di `AudioOutput` e l'implementazione `SpeakerAudioOutput`.
- **Controller**: implementazione di `PlaybackController`.
- **AudioADT**: definizione della conversione tra istante temporale e chunkIndex in `AudioTrack`.
- **Interfaccia grafica**: raffinamento del riquadro della traccia (`TrackBox`) e la logica associata (`TrackBoxLogic`).
- **Testing**: definizione e implementazione dei test di questi componenti e della demo di riproduzione senza GUI (`AudioPlaybackDemo`).

### Stato

`AppState` è una `case class` immutabile che raccoglie lo stato dell'applicazione. Per il controllo di riproduzione sono rilevanti la traccia caricata (`track`), lo stato di riproduzione (`playbackState`) e l'indice del chunk corrente (`chunkIndex`). Il punto di riproduzione corrente è un indice di chunk e non un valore temporale, che se necessario è possibile ricavare dalla traccia stessa.

```scala
final case class AppState(
    playbackState: PlaybackState,
    importState: ImportState,
    track: Option[AudioTrack],
    effects: List[AudioEffect],
    chunkIndex: Int,
    exportState: ExportState
)

enum PlaybackState:
  case Stopped, Playing, Paused
```

L'`AppState` non si modifica direttamente. I comandi disponibili sono rappresentati dall'enumerazione Scala 3 `Command`, un tipo algebrico chiuso come le altre componenti dello stato (`PlaybackState`, `ImportState`, `ExportState`). `AppState.handle` accetta in input un comando e, in base a questo, decide la transizione e restituisce il nuovo stato. È un pattern matching esaustivo su `Command` e per qualsiasi `case` caso delega a un metodo privato che restituisce una copia dello stato oppure lo stesso stato quando il comando non è applicabile.

```scala
def handle(command: Command): AppState =
  command match
    case Play                => handlePlay()
    case Pause               => handlePause()
    case Stop                => handleStop()
    case NextChunk           => handleNextChunk()
    case RemoveAllEffects    => handleRemoveAllEffects()
    case AddEffect(effect)   => handleAddEffect(effect)
    case SeekToIndex(index)  => handleSeek(index)
    case UpdateImport(state) => handleImport(state)
    case UpdateExport(state) => handleExport(state)

private def handlePause(): AppState =
  playbackState match
    case Playing => copy(playbackState = Paused)
    case _       => this
```

Lo stato iniziale è definito nel companion object `AppState`:

```scala
object AppState:
  val initial: AppState = AppState(
    playbackState = Stopped,
    importState = ImportState.Idle,
    track = None,
    effects = List.empty,
    chunkIndex = 0,
    exportState = ExportState.Idle
  )
```

### Store

`AppStateStore` è un trait con due operazioni primitive, `current` e `dispatch`, e un flusso `changes`; i flussi derivati sono metodi con implementazione di default costruiti sopra `changes`.

```scala
trait AppStateStore:
  def current: IO[AppState]
  def dispatch(command: Command): IO[AppState]
  def changes: Stream[IO, AppState]

  def playbackStateChanges: Stream[IO, PlaybackState] =
    changes.map(_.playbackState).changes

  ...
```

L'implementazione non è nel trait ma nel companion object, dentro `make`, e si appoggia a un `SignallingRef` di FS2, un riferimento mutabile che, oltre a supportare la lettura e la scrittura atomica rende possibile esporre i cambiamenti come flusso.

```scala
object AppStateStore:
  def make: IO[AppStateStore] =
    SignallingRef[IO, AppState](AppState.initial).map { sigRef =>
      new AppStateStore:
        def current: IO[AppState] = sigRef.get

        def dispatch(command: Command): IO[AppState] =
          sigRef.updateAndGet(_.handle(command))

        def changes: Stream[IO, AppState] = sigRef.discrete
    }
```

Il `SignallingRef` è inizializzato con `AppState.initial`. `current` ne legge il valore attuale, in modo atomico. `dispatch` usa `updateAndGet`, che applica `handle` in modo atomico e restituisce il nuovo stato. `changes` usa `discrete`, che emette lo stato attuale e poi ogni cambiamento successivo. Può tuttavia saltare valori intermedi se il consumatore è lento.

I flussi derivati si basano su `changes`, già definito nel trait dello store: ne estraggono la parte che interessa e, con l'operatore omonimo `.changes`, emettono solo quando quella parte cambia. `playbackStateChanges`, per esempio, emette solo quando cambia il `PlaybackState`, anche se l'`AppState` nel suo complesso è cambiato per altri motivi, come la modifica del `chunkIndex`. L'operatore confronta ogni valore con il precedente e richiede un'istanza di `Eq`. Per `PlaybackState`, che è un'enumerazione, basta l'uguaglianza standard `==`. L'istanza è quindi dichiarata come `given` con `Eq.fromUniversalEquals`, così che `.changes` la trovi in modo implicito:

```scala
given Eq[PlaybackState] = Eq.fromUniversalEquals
```

### Player

`AudioPlayer` è un `trait` con il solo metodo `run(store)` e si crea con `AudioPlayer.streaming`: il nome descrive la variante, perché un'altra strategia di riproduzione, per esempio con un buffer, ne avrebbe uno diverso. L'implementazione `StreamingAudioPlayer` è privata e trasforma lo stream degli stati in uno stream di azioni sull'audio:

```scala
store.playbackProgressChanges
  .switchMap { state =>
    state.playbackState match
      case PlaybackState.Playing =>
        Stream.eval(playChunk(state, store).handleErrorWith(stopAfterFailure(store)))
      case _ => Stream.empty
  }
```

`playbackProgressChanges` è il flusso derivato dello store costruito con `changesBy`: lascia passare lo stato solo quando cambia la coppia (stato di riproduzione, indice), quindi la modifica degli effetti non fa ripartire la riproduzione. `switchMap` annulla il flusso interno all'arrivo di un nuovo stato: se l'utente mette in pausa o sposta la timeline mentre un chunk è in scrittura, l'operazione in corso viene cancellata. Il ciclo non ha un `while`: ogni chunk scritto invia `NextChunk`, il che cambia l'indice e produce lo stato successivo.

Il chunk da suonare è cercato con una for-comprehension su `Option`, che si interrompe da sola se manca la traccia o se l'indice è fuori dai limiti, e il risultato è poi trattato con un pattern matching:

```scala
val current =
  for
    track <- state.track
    chunk <- track.chunks.lift(state.chunkIndex)
  yield (track, chunk)

current match
  case Some((track, chunk)) => ...   // elabora, scrive e invia NextChunk
  case None                 => store.dispatch(Command.Stop).void
```

Gli errori sono gestiti in due punti. L'elaborazione è eseguita dentro `IO(...)`, così un'eccezione imprevista del processore diventa un errore dell'`IO`, mentre un `AudioError` restituito dal processore è un normale valore `Left`: si stampa un messaggio e si riproduce il chunk originale. Un fallimento dell'uscita audio è intercettato da `handleErrorWith`, che stampa l'errore e invia `Stop`; il player resta in ascolto dello store e può ripartire.

### Uscita audio

`AudioOutput` ha un solo metodo astratto (`write`), quindi `AudioOutput.console` è definito come una semplice lambda. `paced` è un extension method che decora un qualunque output con un'attesa pari alla durata del chunk, calcolata in nanosecondi per non perdere precisione:

```scala
extension (out: AudioOutput)
  def paced: AudioOutput = (chunk, sampleRate) =>
    for
      _ <- out.write(chunk, sampleRate)
      _ <- IO.sleep(playbackDuration(chunk, sampleRate))
    yield ()
```

In `SpeakerAudioOutput` gli extension method servono a tenere vicino al tipo le operazioni che lo riguardano senza modificarlo: `AudioChunk` ottiene `channels` e `toPcmBytes`, e `Float` ottiene `toPcm16`, che satura il campione in [-1, 1] e lo scala a 16 bit. `toPcmBytes` scrive in un `ByteBuffer` little-endian, alternando campione sinistro e destro per lo stereo, con un pattern matching su `MonoChunk` e `StereoChunk`:

```scala
extension (sample: Float)
  private def toPcm16: Short =
    (sample.max(-1f).min(1f) * MaxAmplitude).toShort
```

La scrittura sulla linea è bloccante, quindi è racchiusa in `IO.blocking`, che la sposta sul pool dei compiti bloccanti e lascia libero quello di calcolo. La linea è aperta una sola volta e riusata finché formato e numero di canali restano uguali, perché riaprirla a ogni chunk produrrebbe interruzioni udibili; il formato è un `case class` privato usato come chiave di confronto. Il buffer è di 200 ms, un valore che assorbe le piccole irregolarità dei tempi di elaborazione senza ritardare troppo la risposta ai comandi. Il riferimento alla linea attiva è un `var` privato, l'unico stato mutabile del mio codice: è sicuro perché `write` è chiamata solo dalla fibra del player, una alla volta, ma non lo sarebbe con più fibre sullo stesso output.

Anche per l'uscita il nome della factory descrive la variante (`AudioOutput.console` e `AudioOutput.speakers`). L'uscita sugli altoparlanti è una `Resource`: `AudioOutput.speakers` la crea con `Resource.make` (con `make` si indicano le creazioni con effetto, e il tipo restituito dice se c'è anche da rilasciare qualcosa), che alla chiusura dell'applicazione chiama `close`, il quale riproduce l'audio rimasto nel buffer (`drain`) e chiude la linea. In `AppSetup` l'uscita è acquisita prima del player e quindi rilasciata dopo, così la linea non viene chiusa mentre il player sta ancora scrivendo.

```scala
def speakers: Resource[IO, AudioOutput] = SpeakerAudioOutput.make

def make: Resource[IO, AudioOutput] =
  Resource.make[IO, SpeakerAudioOutput](IO(SpeakerAudioOutput()))(_.close)
```

### Controller

`PlaybackController` è un `trait` con un'implementazione privata, creata da un `apply` nel companion object: la creazione non ha effetti, quindi `apply` è il nome adatto. I metodi sono implementati con `override`, così il compilatore segnala un'implementazione che non corrisponde più al trait.

```scala
object PlaybackController:
  def apply(store: AppStateStore): PlaybackController = PlaybackControllerImpl(store)

private final class PlaybackControllerImpl(store: AppStateStore) extends PlaybackController:
  override def play: IO[Unit] = store.dispatch(Play).void
```

I metodi restituiscono `IO[Unit]` e non eseguono nulla da soli: quando eseguire spetta a chi lo chiama, che nella view usa `unsafeRunAndForget`. Ogni metodo invia un comando allo store. `seek` riceve la posizione come `FiniteDuration`, la converte nell'indice del chunk con `AudioTrack.chunkIndexAt` e invia `SeekToIndex`; senza una traccia caricata non fa nulla:

```scala
override def seek(position: FiniteDuration): IO[Unit] =
  for
    state <- store.current
    _     <- state.track match
      case None        => IO.unit
      case Some(track) => store.dispatch(SeekToIndex(track.chunkIndexAt(position))).void
  yield ()
```

### Traccia audio

La conversione tra tempo e chunk sta in `AudioTrack`, che conosce i chunk e la frequenza di campionamento. `chunkIndexAt` trova il chunk che contiene una posizione con una funzione ricorsiva in coda:

```scala
def chunkIndexAt(position: FiniteDuration): Int =
  val targetFrame = (position.toNanos * sampleRate.value / 1_000_000_000L).max(0L)
  @tailrec
  def loop(index: Int, framesBefore: Long): Int =
    chunks.lift(index) match
      case None => (chunks.length - 1).max(0)
      case Some(chunk) if targetFrame < framesBefore + chunk.frameCount => index
      case Some(chunk) => loop(index + 1, framesBefore + chunk.frameCount)
  loop(0, 0L)
```

L'annotazione `@tailrec` fa verificare al compilatore che la ricorsione sia in coda, quindi non consuma stack nemmeno su tracce con migliaia di chunk. Una posizione oltre la fine dà l'ultimo chunk e una negativa il primo. La guardia `if` nel pattern distingue il chunk che contiene il frame da quelli precedenti.

L'operazione inversa, da indice a tempo, è `positionAt`: somma i frame dei chunk precedenti con una `view`, che evita di creare collezioni intermedie, e li converte in nanosecondi con aritmetica intera. `duration` è semplicemente `positionAt(chunks.length)`.

### Logica della traccia

`TrackBoxLogic` segue lo stesso schema del controller: un `trait` con `attach` e `observe`, creato da un `apply`, con un'implementazione privata. Collega i widget allo stato e ha due responsabilità: inviare ai controller le azioni dell'utente e riflettere lo stato nella timeline e nelle etichette. Il punto delicato è che il programma stesso muove lo slider durante la riproduzione, e un listener ingenuo scambierebbe ogni avanzamento per un seek dell'utente. Il flag `isProgrammaticUpdate` viene alzato mentre il programma aggiorna lo slider (e abbassato in un `finally`, anche se l'aggiornamento fallisce) e fa ignorare i listener; durante il trascinamento (`valueChanging`) l'aggiornamento automatico è sospeso e il seek parte solo al rilascio. Il controllo "il cambiamento è dell'utente" è espresso con `Option.when`, che rende il seek una catena di operazioni sul valore e non un blocco `if`:

```scala
box.timeLine.value.onChange { (_, _, newValue) =>
  val isUserChange = !isProgrammaticUpdate && !isDragging(box)
  Option
    .when(isUserChange)(newValue.doubleValue)
    .map(toDuration)
    .foreach(controller.seek(_).unsafeRunAndForget())
}
```

`observe` è uno `Stream` che, a ogni cambiamento della traccia o della posizione (`playheadChanges`), pianifica l'aggiornamento della finestra con `Platform.runLater`, perché i widget possono essere modificati solo dal thread JavaFX.

\newpage
