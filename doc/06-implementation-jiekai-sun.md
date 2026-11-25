## Jiekai Sun

### Panoramica dei contributi

Il mio contributo al progetto si è focalizzato nelle seguenti aree:

- **Stato**: definizione e implementazione di `AppState` e `AppStateStore`, principalmente per quanto concerne il controllo di riproduzione.
- **AudioADT**: definizione della conversione tra istante temporale e chunkIndex in `AudioTrack`.
- **Motore di riproduzione**: definizione di `AudioPlayer` e l'implementazione `StreamingAudioPlayer`.
- **Output audio**: definizione di `AudioOutput` e l'implementazione `SpeakerAudioOutput`.
- **Controller**: implementazione di `PlaybackController`.
- **Interfaccia grafica**: raffinamento del riquadro della traccia (`TrackBox`) e implementazione della logica associata (`TrackBoxLogic`).
- **Testing**: definizione e implementazione dei test di questi componenti e della demo di riproduzione senza GUI (`AudioPlaybackDemo`).

### Stato

`AppState` è una `case class` immutabile che rappresenta lo stato dell'applicazione. Per il controllo di riproduzione sono rilevanti la traccia caricata (`track`), lo stato di riproduzione (`playbackState`) e l'indice del chunk corrente (`chunkIndex`).

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
  case Stopped
  case Playing
  case Paused
```

L'`AppState` non si modifica direttamente. I comandi disponibili sono rappresentati dall'`enum` `Command`, un tipo algebrico chiuso come le altre componenti dello stato (`PlaybackState`, `ImportState`, `ExportState`). `AppState.handle` accetta in input un comando e, in base a questo, decide la transizione e restituisce il nuovo stato. È un pattern matching esaustivo su `Command` e ogni caso delega a un metodo privato che restituisce una copia dello stato oppure lo stesso stato quando il comando non è applicabile.

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

### Store dello stato

`AppStateStore` è un `trait` con due operazioni primitive, `current` e `dispatch`, e un flusso `changes`; i flussi derivati sono metodi con implementazione di default costruiti sopra `changes`.

```scala
trait AppStateStore:
  def current: IO[AppState]
  def dispatch(command: Command): IO[AppState]
  def changes: Stream[IO, AppState]

  def playbackStateChanges: Stream[IO, PlaybackState] =
    changes.map(_.playbackState).changes
  ...
```

L'implementazione non è nel `trait` ma nel companion object, dentro `make`, e si appoggia a un `SignallingRef` di FS2, un riferimento mutabile che, oltre a supportare la lettura e la scrittura atomica, rende possibile esporre i cambiamenti come flusso:

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

Il `SignallingRef` è inizializzato con `AppState.initial`. `current` ne legge il valore attuale, in modo atomico. `dispatch` usa `updateAndGet`, che applica `handle` in modo atomico e restituisce il nuovo stato. `changes` usa `discrete`, che emette lo stato attuale e poi ogni cambiamento successivo. Quest'ultimo può tuttavia saltare valori intermedi se il consumatore è lento.

I flussi derivati si basano su `changes`, già definito nel ``trait` dello store: ne estraggono la parte che interessa e, con l'operatore omonimo `.changes`, emettono solo quando quella porzione cambia. `playbackStateChanges`, per esempio, emette solo quando cambia il `PlaybackState`, anche se l'`AppState` nel suo complesso è cambiato per altri motivi, come la modifica del `chunkIndex`. L'operatore confronta ogni valore con il precedente e richiede un'istanza di `Eq`. Per `PlaybackState`, che è un'`enum`, basta l'uguaglianza standard `==`. L'istanza è quindi dichiarata come `given`, in modo che `.changes` la trovi in modo implicito:

```scala
given Eq[PlaybackState] = Eq.fromUniversalEquals
```

### AudioADT

La conversione tra tempo e chunk sta in `AudioTrack`, che conosce i chunk e la frequenza di campionamento. `chunkIndexAt` trova il chunk corrispondente a un dato valore temporale con una funzione ricorsiva in coda:

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

L'operazione inversa, da indice a tempo, è `positionAt`. Essa somma i frame dei chunk che precedono l'indice e li divide per la frequenza di campionamento della traccia, ottenendo un valore temporale.

### Motore di riproduzione

`AudioPlayer` è un `trait` con il solo metodo `run(store)`, che restituisce uno `Stream[IO, Unit]`: il flusso descrive il lavoro del player, cioè osservare lo store e agire di conseguenza. I side effect, cioè l'elaborazione e la riproduzione dell'audio, avvengono solo quando il flusso viene eseguito. Tale flusso va lanciato in background e rimane aperto per l'intero ciclo di vita dell'applicazione.

```scala
trait AudioPlayer:
  def run(store: AppStateStore): Stream[IO, Unit]

object AudioPlayer:
  def streaming(processor: AudioProcessor, out: AudioOutput): AudioPlayer =
    StreamingAudioPlayer(processor, out)
```

Il player si crea con `AudioPlayer.streaming`, definito nel companion object, e richiede in input un `AudioProcessor` e un `AudioOutput`. Poiché sono astrazioni, il player può lavorare con implementazioni diverse del processore e dell'uscita audio senza essere modificato. Il nome `streaming` descrive la variante, trattandosi di un'implementazione che processa i chunk appena prima della loro riproduzione. Un'altra strategia di riproduzione, per esempio che processa in anticipo una serie di chunk, avrebbe un nome diverso.

L'implementazione `StreamingAudioPlayer` è privata e trasforma lo stream degli stati in uno stream di azioni da effettuare:

```scala
def run(store: AppStateStore): Stream[IO, Unit] =
  store.playbackProgressChanges
    .switchMap { state =>
      state.playbackState match
        case PlaybackState.Playing =>
          Stream.eval(playChunk(state, store).handleErrorWith(stopAfterFailure(store)))
        case _ => Stream.empty
    }
```

`playbackProgressChanges` è il flusso derivato dello store costruito con `changesBy`. Lascia passare l'intero `AppState`, poiché al player serve uno snapshot della traccia, gli effetti correnti e il `chunkIndex`, ma lo fa solo quando cambia la chiave, cioè la coppia (`playbackState`, `chunkIndex`).

`switchMap` associa a ogni stato un flusso interno: con `Playing` è quello creato da `Stream.eval`, che esegue l'`IO` di elaborazione e riproduzione del chunk. Se `playbackState` diventa `Stopped` o `Paused`, il nuovo flusso interno è vuoto e la riproduzione non procede più; se invece cambia il `chunkIndex` mantenendo `Playing`, si continua a suonare a partire dal nuovo chunk indicato. La riproduzione prosegue in modo reattivo: quando il chunk è stato processato e suonato, il player invia `NextChunk` allo store, che incrementa il `chunkIndex`; la chiave (`playbackState`, `chunkIndex`) cambia e `playbackProgressChanges` emette il nuovo stato, che fa riprendere la riproduzione dal chunk successivo. Il player non regola il ritmo della riproduzione poiché tale aspetto dipende da `AudioOutput`.

Il metodo `playChunk` cerca il chunk da riprodurre con una for-comprehension su `Option`. Se manca la traccia o l'indice è fuori dai limiti il risultato è `None` e il player invia `Stop`; altrimenti elabora e suona il chunk e poi invia `NextChunk`:

```scala
private def playChunk(state: AppState, store: AppStateStore): IO[Unit] =
  val playable =
    for
      track <- state.track
      chunk <- track.chunks.lift(state.chunkIndex)
    yield (track, chunk)

  playable match
    case Some((track, chunk)) => ... // elabora, suona e invia NextChunk
    case None                 => store.dispatch(Command.Stop).void
```

Gli errori sono gestiti in due modi. Se l'`AudioProcessor` restituisce un `AudioError`, il player stampa un messaggio in console e riproduce il chunk originale. Per qualunque altro errore, come un fallimento dell'`AudioOutput`, `handleErrorWith` stampa l'errore in console e invia il comando `Stop` allo store. Il player resta comunque in ascolto sullo store e può ripartire qualora `playbackState` diventi di nuovo `Playing`.

### Output audio

`AudioOutput` è un `trait` con un solo metodo, `write`, che restituisce un `IO[Unit]` completato quando l'uscita ha accettato il chunk. Le implementazioni si ottengono dal companion object: `AudioOutput.console` stampa in console le informazioni del chunk e `AudioOutput.speakers` lo riproduce sugli altoparlanti.

```scala
trait AudioOutput:
  def write(chunk: AudioChunk, sampleRate: Int): IO[Unit]

object AudioOutput:
  val console: AudioOutput = (chunk, sampleRate) => IO.println(...)
  def speakers: Resource[IO, AudioOutput] = SpeakerAudioOutput.make
```

Trattandosi di un `trait` con un solo metodo, `console` è definito come una semplice lambda. Poiché la scrittura in console termina subito, è stato aggiunto l'extension method `paced` che decora un qualunque `AudioOutput` con un'attesa pari alla durata del chunk, in modo tale che la riproduzione avanzi in tempo reale:

```scala
extension (out: AudioOutput)
  def paced: AudioOutput = (chunk, sampleRate) =>
    for
      _ <- out.write(chunk, sampleRate)
      _ <- IO.sleep(playbackDuration(chunk, sampleRate))
    yield ()
```

`SpeakerAudioOutput` usa la Java Sound API. La scrittura sulla linea è racchiusa in `IO.blocking`, perché può fermarsi se il buffer non ha spazio. Per questo il ritmo è quello dell'audio, senza bisogno di `paced`. La linea attiva è un `var` privato, aperta alla prima scrittura e riaperta solo se cambiano frequenza di campionamento o numero di canali:

```scala
private var active: Option[ActiveLine] = None

override def write(chunk: AudioChunk, sampleRate: Int): IO[Unit] =
  for
    bytes <- IO(chunk.toPcmBytes)
    _     <- IO.blocking {
      val line = lineFor(Format(sampleRate, chunk.channelCount))
      line.write(bytes, 0, bytes.length)
    }
  yield ()
```

Per codificare un chunk in byte PCM richiesti dalla libreria si usa un extension method su `AudioChunk`:

```scala
extension (chunk: AudioChunk)
  private def toPcmBytes: Array[Byte] =
    val buffer = ByteBuffer
      .allocate(chunk.frameCount * chunk.channelCount * BytesPerSample)
      .order(ByteOrder.LITTLE_ENDIAN)
    chunk match
      case AudioChunk.MonoChunk(samples) =>
        samples.foreach(sample => buffer.putShort(sample.toPcm16))
      case AudioChunk.StereoChunk(left, right) =>
        left
          .zip(right)
          .foreach: (l, r) =>
            buffer.putShort(l.toPcm16)
            buffer.putShort(r.toPcm16)
    buffer.array()
```

`SpeakerAudioOutput` è creato come `Resource`, poiché la linea audio deve essere chiusa alla terminazione dell'applicazione. La `Resource` è costruita con `Resource.make` e, come rilascio, chiama `close`, che riproduce l'audio rimasto nel buffer e chiude la linea.

```scala
def make: Resource[IO, AudioOutput] =
  Resource.make[IO, SpeakerAudioOutput](IO(SpeakerAudioOutput()))(_.close)
```

### Controller

`PlaybackController` è un `trait` con le operazioni `play`, `pause`, `stop` e `seek`, e un'implementazione privata creata da un `apply` nel companion object.

```scala
object PlaybackController:
  def apply(store: AppStateStore): PlaybackController =
    PlaybackControllerImpl(store)

private final class PlaybackControllerImpl(store: AppStateStore)
    extends PlaybackController:
  override def play: IO[Unit] = store.dispatch(Play).void
  ...
```

`play`, `pause` e `stop` si limitano a inviare il comando corrispondente allo store. `seek` è l'operazione più complessa, perché riceve un valore temporale come `FiniteDuration` e deve convertirlo nel `chunkIndex` corrispondente, usando la traccia corrente e `AudioTrack.chunkIndexAt`, prima di impartire il comando `SeekToIndex`.

```scala
override def seek(position: FiniteDuration): IO[Unit] =
  for
    state <- store.current
    _     <- state.track match
      case None        => IO.unit
      case Some(track) =>
        store.dispatch(SeekToIndex(track.chunkIndexAt(position))).void
  yield ()
```

### Interfaccia grafica

`TrackBoxLogic` connette il riquadro della traccia (`TrackBox`) ai cambiamenti di stato e al `PlaybackController`. Segue lo stesso schema di quest'ultimo: un `trait` con due metodi, creato da un `apply`, con un'implementazione privata. `attach` collega i pulsanti e la timeline al `PlaybackController`, mentre `observe` riflette lo stato nella timeline e nelle due etichette che mostrano il tempo trascorso e la durata della traccia.

Il punto delicato è che l'applicazione stessa muove la timeline durante la riproduzione, e un listener ingenuo scambierebbe ogni avanzamento per un seek dell'utente. Il flag `isProgrammaticUpdate` viene impostato a `true` mentre l'applicazione aggiorna la timeline e permette ai listener di ignorare gli eventi generati da tali aggiornamenti. Il seek viene impartito solo con un click sulla timeline o al rilascio dopo un trascinamento, non durante.

Le azioni dell'utente sono gestite sul thread JavaFX, che deve avviare le `IO` del controller con `unsafeRunAndForget`; per questo `TrackBoxLogic` riceve un `IORuntime` come parametro `using`:

```scala
box.playButton.onAction = _ => controller.play.unsafeRunAndForget()
box.pauseButton.onAction = _ => controller.pause.unsafeRunAndForget()
box.stopButton.onAction = _ => controller.stop.unsafeRunAndForget()
```

`observe`, invece, è un'`IO` eseguita da una fibra di Cats Effect, non dal thread JavaFX. Le modifiche agli elementi grafici in base ai cambiamenti dello stato devono quindi essere effettuate con `Platform.runLater`.

\newpage
