## Jiekai Sun

Mi sono occupato del package `model.state` (stato dell'applicazione e store), del package `model.player` (player e uscita audio), di `PlaybackController` e della logica del riquadro della traccia (`TrackBoxLogic`). Insieme formano il percorso che porta un comando dell'utente alla riproduzione: la view invia una richiesta, il controller la traduce in un comando, lo store aggiorna lo stato e il player reagisce.

`AppState` e `AppStateStore` sono nati da me per la parte di riproduzione (comandi Play, Pause, Stop, NextChunk e Seek, calcolo della posizione, `SignallingRef` e flussi sullo stato di riproduzione e sulla traccia). Gli altri due studenti vi hanno aggiunto la propria parte: Simone Carpi i comandi sugli effetti (`AddEffect`, `RemoveAll`) e Martin Tomassi gli stati di importazione ed esportazione con i relativi flussi. `StreamingAudioPlayer`, `AudioOutput`, `SpeakerAudioOutput`, `PlaybackController` e `TrackBoxLogic` sono interamente miei, così come i test di questi componenti.

### Enumerazioni e pattern matching per lo stato

Lo stato è modellato con enumerazioni Scala 3 (`PlaybackState`, `Command`, `ImportState`, `ExportState`), che sono tipi algebrici chiusi. `AppState.handle` è un pattern matching esaustivo su `Command`: il compilatore segnala un comando non gestito, e ogni caso delega a un metodo privato che restituisce una copia dello stato (`copy`) oppure lo stesso stato quando il comando non è applicabile.

```scala
def handle(command: Command): AppState =
  command match
    case Play                => handlePlay()
    case Pause               => handlePause()
    case Stop                => handleStop()
    case NextChunk           => handleNextChunk()
    case SeekToIndex(index)  => handleSeek(index)
    case AddEffect(effect)   => handleAddEffect(effect)
    ...

private def handlePause(): AppState =
  playbackState match
    case Playing => copy(playbackState = Paused)
    case _       => this
```

Una funzione totale e senza effetti collaterali si verifica confrontando due valori. La posizione in secondi non è memorizzata: `currentPositionSeconds` la ricava sommando i frame dei chunk già riprodotti, così lo stato contiene un solo indice e non può diventare incoerente.

### Store con `SignallingRef` e istanze `given`

`AppStateStore` è un trait con due operazioni primitive, `current` e `dispatch`, e un flusso `changes`; i flussi derivati sono metodi con implementazione di default costruiti sopra `changes`. L'implementazione usa un `SignallingRef` di fs2:

```scala
def dispatch(command: Command): IO[AppState] =
  sigRef.updateAndGet(_.handle(command))

def changes: Stream[IO, AppState] = sigRef.discrete
```

`updateAndGet` applica `handle` in modo atomico, quindi comandi inviati da fibre diverse (view, controller, player) non si sovrascrivono. `discrete` emette il valore corrente e poi ogni cambiamento, ma può saltare valori intermedi se il consumatore è lento: per la view e per il player conta lo stato più recente, non la sequenza completa. I flussi derivati usano `.changes` di fs2, che emette solo quando il valore cambia e richiede un'istanza di `Eq`. Le istanze sono dichiarate come `given` basate sull'uguaglianza universale, perché stati e tracce si confrontano per valore:

```scala
given Eq[PlaybackState]      = Eq.fromUniversalEquals
given Eq[Option[AudioTrack]] = Eq.fromUniversalEquals

def playbackChanges: Stream[IO, PlaybackState] =
  changes.map(_.playbackState).changes
```

### Player: `changesBy`, `switchMap` e for-comprehension

`StreamingAudioPlayer` trasforma lo stream degli stati in uno stream di azioni sull'audio:

```scala
store.changes
  .changesBy(state => (state.playbackState, state.chunkIndex))
  .switchMap { state =>
    state.playbackState match
      case PlaybackState.Playing =>
        Stream.eval(playChunk(state, store).handleErrorWith(stopAfterFailure(store)))
      case _ => Stream.empty
  }
```

`changesBy` lascia passare lo stato solo quando cambia la coppia (stato di riproduzione, indice), quindi la modifica degli effetti non fa ripartire la riproduzione. `switchMap` annulla il flusso interno all'arrivo di un nuovo stato: se l'utente mette in pausa o sposta la timeline mentre un chunk è in scrittura, l'operazione in corso viene cancellata. Il ciclo non ha un `while`: ogni chunk scritto invia `NextChunk`, il che cambia l'indice e produce lo stato successivo.

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

### Uscita audio: lambda, extension method e `IO.blocking`

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

### Controller: extension method privato e ricorsione in coda

`PlaybackController` restituisce `IO[Unit]` e non esegue nulla da solo: quando eseguire spetta a chi lo chiama, che nella view usa `unsafeRunAndForget`. `seek` converte i secondi scelti dall'utente nell'indice del chunk con un extension method privato su `AudioTrack` che contiene una funzione ricorsiva in coda:

```scala
extension (track: AudioTrack)
  private def chunkIndexAt(positionSeconds: Double): Int =
    val targetFrame = (positionSeconds * track.sampleRate).toLong.max(0L)
    @tailrec
    def loop(index: Int, framesBefore: Long): Int =
      track.chunks.lift(index) match
        case None => (track.chunks.length - 1).max(0)
        case Some(chunk) if targetFrame < framesBefore + chunk.frameCount => index
        case Some(chunk) => loop(index + 1, framesBefore + chunk.frameCount)
    loop(0, 0L)
```

L'annotazione `@tailrec` fa verificare al compilatore che la ricorsione sia in coda, quindi non consuma stack nemmeno su tracce con migliaia di chunk. Una posizione oltre la fine dà l'ultimo chunk e una negativa il primo. La guardia `if` nel pattern distingue il chunk che contiene il frame da quelli precedenti.

### Logica del riquadro della traccia

`TrackBoxLogic` collega i widget allo stato e ha due responsabilità: inviare ai controller le azioni dell'utente e riflettere lo stato nella timeline e nelle etichette. Il punto delicato è che il programma stesso muove lo slider durante la riproduzione, e un listener ingenuo scambierebbe ogni avanzamento per un seek dell'utente. Il flag `isProgrammaticUpdate` viene alzato mentre il programma aggiorna lo slider e fa ignorare i listener; durante il trascinamento (`valueChanging`) l'aggiornamento automatico è sospeso e il seek parte solo al rilascio. Il controllo "il cambiamento è dell'utente" è espresso con `Option.when`, che rende il seek una catena di operazioni sul valore e non un blocco `if`:

```scala
box.timeLine.value.onChange { (_, _, newValue) =>
  val isUserChange = !isProgrammaticUpdate && !isDragging(box)
  Option
    .when(isUserChange)(newValue.doubleValue)
    .foreach(controller.seek(_).unsafeRunAndForget())
}
```

`observe` è uno `Stream` che a ogni cambiamento dello store pianifica l'aggiornamento della finestra con `Platform.runLater`, perché i widget possono essere modificati solo dal thread JavaFX.

\newpage
