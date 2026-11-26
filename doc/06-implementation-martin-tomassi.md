## Martin Tomassi

### Panoramica dei contributi

Il mio contributo al progetto si è focalizzato nelle seguenti aree:

- **Astrazioni I/O, registro dei formati e gestione degli errori**: i trait `AudioReader` e `AudioWriter`, il descrittore `AudioFileType`, l'`AudioFormatRegistry` e la gerarchia degli errori (`AudioLoadError`, `AudioSaveError`, `FormatResolutionError`, `AudioFormatRegistryError`).
- **Implementazione del formato WAV**: `WavAudioReader` e `WavAudioWriter`, basati sulla *Java Sound API*, insieme ai componenti ausiliari `WavFormat`, `ChannelLayout`, `Pcm16`, `PcmChunks` ed al descrittore `WavAudioFormat`.
- **Controller di importazione ed esportazione**: `LoadSaveController`, che coordina in background le operazioni di caricamento e salvataggio aggiornando lo stato applicativo.
- **Interfaccia grafica e logica Load/Save**: `TitlePane`, `LoadSaveLogic` ed `ErrorMessages`.
- **Modello di dominio**: l'`opaque type` `SampleRate`, gli errori di dominio (`AudioDomainError`) ed i costruttori di `MonoTrack` e `StereoTrack`.
- **Testing**: definizione ed implementazione dei test relativi alla parte I/O ed al relativo controller, oltre ai test di integrazione del flusso di importazione ed esportazione.

## Astrazioni I/O, gestione degli errori e registro dei formati

L'importazione e l'esportazione sono modellate da due trait distinti, ciascuno con un solo metodo ed un tipo di ritorno `IO[Either[E, A]]`:

```scala
trait AudioReader:
  def load(path: Path): IO[Either[AudioLoadError, AudioTrack]]

trait AudioWriter:
  def save(track: AudioTrack, path: Path): IO[Either[AudioSaveError, Unit]]

final case class AudioFileType(
    name: String,
    primaryExtension: String,
    aliases: Set[String] = Set.empty,
    reader: Option[AudioReader] = None,
    writer: Option[AudioWriter] = None
)
```

`IO` incapsula l'operazione di I/O come descrizione dell'effetto, delegandone l'esecuzione al chiamante, mentre `Either` rappresenta i fallimenti previsti come valori tipizzati, riservando la gestione degli errori interna di `IO` (`Throwable`) alle sole eccezioni impreviste. La separazione dei due contratti applica l'*Interface Segregation Principle (ISP)*: importazione ed esportazione hanno errori diversi ed un formato può supportare una sola delle due operazioni. Il descrittore `AudioFileType` riflette questa scelta modellando reader e writer come `Option`.

`AudioFormatRegistry` centralizza la selezione della *strategia* per importazione ed esportazione del file audio, a partire dall'estensione del percorso: il controller riceve un `AudioReader` o un `AudioWriter` senza conoscere il formato concreto (*Dependency Inversion Principle (DIP)*). La classe è immutabile, ha costruttore privato e viene istanziata tramite la *Factory* `AudioFormatRegistry.make`, che verifica la buona formazione e l'unicità delle estensioni normalizzate (`FileExtensions`). Un registro costruito correttamente è quindi valido rispetto a tali vincoli. In `AppSetup`, `IO.fromEither` converte un eventuale `AudioFormatRegistryError` in `IllegalStateException`, impedendo l'avvio dell'applicazione in presenza di una configurazione errata.

```scala
def readerFor(path: Path): Either[FormatResolutionError, AudioReader] =
  for
    extension <- FileExtensions.of(path).toRight(MissingExtension(path))
    fileType  <- typeOf(extension)
    reader    <- fileType.reader.toRight(ReadingNotSupported(fileType.name))
  yield reader

def writerFor(path: Path): Either[FormatResolutionError, ResolvedWriter] =
  FileExtensions.of(path) match
    case Some(extension) =>
      for
        fileType <- typeOf(extension)
        writer   <- fileType.writer.toRight(WritingNotSupported(fileType.name))
      yield ResolvedWriter(writer, path)
    case None => withDefaultFormat(path)
```

In `readerFor` e `writerFor`, la `for`-comprehension opera sulla monade `Either`: ogni passaggio converte un eventuale `Option` tramite `toRight`, così il primo fallimento interrompe la catena e propaga il caso specifico di `FormatResolutionError`. Nella scrittura, l'assenza di estensione non genera necessariamente un errore: `withDefaultFormat` seleziona il primo formato registrato dotato di writer e aggiunge la relativa `primaryExtension` al percorso tramite `ResolvedWriter`.

In `AudioFormatRegistry.make`, le for-comprehension operano sulla monade `List`. La prima associa ogni formato alle rispettive estensioni normalizzate, mentre la seconda individua quelle malformate. Le collisioni vengono rilevate successivamente raggruppando le associazioni per estensione tramite `groupMap` ed individuando quelle presenti in più formati (`DuplicateExtension`). Il risultato è il primo errore di validazione riscontrato oppure un registro costruito a partire dalle associazioni valide. Il registro si basa unicamente sul nome del file: la verifica che il contenuto rispetti davvero il formato spetta al reader.

```scala
  def make(fileTypes: AudioFileType*): Either[AudioFormatRegistryError, AudioFormatRegistry] =
    val formats = fileTypes.toList
    val claims =
      for
        fileType  <- formats
        extension <- fileType.allExtensions.map(FileExtensions.normalise).distinct
      yield extension -> fileType

    val malformed =
      for
        fileType  <- formats
        extension <- fileType.allExtensions
        if !FileExtensions.isWellFormed(extension)
      yield AudioFormatRegistryError.MalformedExtension(fileType.name, extension)

    val duplicated = claims
      .groupMap((extension, _) => extension)((_, fileType) => fileType.name)
      .toList
      .sortBy((extension, _) => extension)
      .collect:
        case (extension, names) if names.lengthIs > 1 =>
          AudioFormatRegistryError.DuplicateExtension(extension, names)

    malformed.headOption.orElse(duplicated.headOption).toLeft:
      AudioFormatRegistry(claims.toMap, defaultFormatOf(formats))
```

Gli errori sono organizzati in quattro `enum` (`AudioLoadError`, `AudioSaveError`, `FormatResolutionError`, `AudioFormatRegistryError`). Sia `AudioLoadError` sia `AudioSaveError` incapsulano i fallimenti di selezione nel caso `UnresolvedFormat(cause: FormatResolutionError)`, evitando di duplicare i casi di risoluzione e consentendo a `ErrorMessages` di tradurli in un unico punto.

Dal punto di vista dell'*Open/Closed Principle (OCP)*, aggiungere un nuovo formato richiede una coppia reader/writer (o il solo componente necessario), un nuovo `AudioFileType` e la sua registrazione in `AudioFormatRegistry.make` dentro `AppSetup`, lasciando invariati il registro, il controller e `ErrorMessages`. Attualmente l'applicazione registra soltanto il WAV (`WavAudioFormat.fileType`).

## Implementazione del formato WAV

Poiché reader e writer producono e consumano `AudioTrack`, il modello di dominio garantisce gli invarianti strutturali delle tracce prima dell'I/O. In `SampleRate.scala`, la frequenza di campionamento è definita come `opaque type`:

```scala
opaque type SampleRate = Int

object SampleRate:
  def from(value: Int): Either[AudioDomainError, SampleRate] =
    Either.cond(value > 0, value, AudioDomainError.InvalidSampleRate(value))

  extension (sampleRate: SampleRate) def value: Int = sampleRate
```

All'interno del file di definizione `SampleRate` coincide con `Int`, consentendo a `from` ed all'extension method `value` di operare senza wrapper; all'esterno è un tipo distinto, eliminando la *primitive obsession*, impedendo così di scambiare la frequenza con altri interi del dominio. L'unico modo per ottenerlo è `SampleRate.from`, che impone la positività (`> 0`), mentre il limite superiore legato alla codifica (`2^24 Hz`) è verificato nel writer WAV.

`MonoTrack` e `StereoTrack` hanno costruttore privato e due factory: `make` accetta un `Int` e lo valida con `SampleRate.from`, mentre `fromValidated` accetta un `SampleRate` già garantito. Quest'ultimo è usato dal reader dopo l'analisi dell'intestazione. `MonoTrack.fromValidated` è totale, mentre `StereoTrack.fromValidated` restituisce comunque un `Either[AudioDomainError, StereoTrack]`, perché l'uguaglianza delle lunghezze dei canali sinistro e destro in ogni chunk (`DifferentLengthsStereoChannels`) resta un invariante da verificare a runtime.

Il package `scalasound.model.io.wav` è organizzato secondo il principio *Functional Core / Imperative Shell*. Gli effetti di I/O e le chiamate alla *Java Sound API* sono confinati in `WavAudioReader` e `WavAudioWriter` (*imperative shell*). Il resto del package costituisce il *functional core*: `WavFormat` funge da adattatore che converte un `AudioFormat` in un tipo del dominio valido, senza eseguire operazioni di I/O, mentre `ChannelLayout`, `Pcm16` e `PcmChunks` sono componenti puri di trasformazione dei dati.

Il sottoinsieme supportato è il PCM con segno a 16 bit, little-endian, mono o stereo. `WavFormat.from` converte e valida l'`AudioFormat` di `Java Sound` in un valore del dominio (`WavFormat(layout, sampleRate)`) mediante una `for`-comprehension su `Either`, distinguendo i formati validi ma non supportati (`UnsupportedFormat`: codifica diversa, big-endian, più di due canali) dalle intestazioni incoerenti (`CorruptedData`: `frameSize` errato o frequenza non valida).

I campioni del dominio sono `Float` normalizzati in `[-1.0, 1.0)`. `Pcm16` definisce le conversioni come extension method su `Float` e `Short`:

```scala
extension (sample: Float)
  def isRepresentable: Boolean =
    !sample.isNaN && !sample.isInfinite

  def toPcm16: Short =
    math.round(sample.toDouble * Scale).max(Min).min(Max).toShort

extension (pcm: Short)
  def toNormalised: Float =
    pcm / Normalisation

def fromLittleEndianBytes(bytes: Array[Byte], sampleIndex: Int): Short =
  val offset = sampleIndex * BytesPerSample
  val low    = bytes(offset) & 0xff
  val high   = bytes(offset + 1)
  ((high << 8) | low).toShort
```

La quantizzazione calcola il prodotto in `Double` per preservare precisione e satura i valori, finiti fuori scala, all'intervallo `[-32768, 32767]` (così `+1.0` viene limitato a `32767`). Poiché `math.round(Float.NaN)` vale `0` e trasformerebbe un campione non finito in "silenzio", `WavAudioWriter` verifica `isRepresentable` sull'intera traccia prima di scrivere.

Nella decodifica little-endian, il byte basso viene mascherato con `0xff` per evitare l'estensione del segno, mentre quello alto mantiene il segno del `Byte` per ricostruire correttamente il `Short` con segno. `PcmChunks` estende le conversioni ai chunk: in scrittura, `encode` utilizza un `ByteBuffer` locale configurato in little-endian per produrre dati PCM interleaved; per lo stereo, i campioni vengono accoppiati nell'ordine `(left, right)` tramite `left.iterator.zip(right.iterator)`. L'invariante di `StereoTrack` garantisce che i due canali abbiano la stessa lunghezza. In lettura, `decodeMono` e `decodeStereo` ricostruiscono i campioni dall'array di byte e li suddividono in chunk da 1024 frame, senza padding sull'ultimo blocco. Un file valido ma privo di frame produce zero chunk.

Lettura e scrittura incapsulano le operazioni bloccanti con `IO.blocking`:

```scala
private def openStream(file: File): Resource[IO, AudioInputStream] =
  Resource.fromAutoCloseable(IO.blocking(AudioSystem.getAudioInputStream(file)))

private def readRawAudioFile(file: File): IO[Either[AudioLoadError, RawAudio]] =
  for attempted <- openStream(file).use(stream => IO.blocking(readFromStream(stream))).attempt
    yield attempted match
      case Right(result)   => result
      case Left(exception) => Left(fromThrowable(exception))
```

In `WavAudioReader.load`, dopo aver verificato `Files.isRegularFile(path)` (in caso contrario restituisce `FileNotFound`), l'apertura dello stream è gestita da una `Resource` che ne assicura la chiusura anche in caso di errore o cancellazione. `.attempt` cattura le eccezioni sollevate durante l'apertura e la lettura, mentre `fromThrowable` le mappa negli errori di dominio (`EOFException` precede `IOException` nella gerarchia e produce `CorruptedData` anziché `ReadFailure`).

La lettura non avviene in streaming continuo: `readFromStream` carica l'intero file in memoria con `readAllBytes()` e costruisce un `RawAudio` contenente formato, numero di frame dichiarati e byte letti. Successivamente, `decode` invoca `validateAudioData`, che verifica la coerenza tra la quantità di byte, la dimensione dei frame ed il numero di frame dichiarati nell'intestazione. Solo dopo questa validazione, i campioni vengono suddivisi in chunk per l'elaborazione e la riproduzione.

In `WavAudioWriter.save`, `prepare(track)` è racchiusa in `IO.delay` ed esegue tutte le validazioni prima di toccare il file system: tipo di traccia, frequenza non superiore a `2^24 Hz` (limite di rappresentabilità esatta nel `Float` richiesto da `AudioFormat`), dimensione entro il limite PCM a 32 bit (`FileTooLarge`) e assenza di campioni non finiti.

Solo se la validazione ha esito positivo, `writeAudioToFile` esegue la scrittura bloccante tramite `IO.blocking`. `AudioSystem.write` riceve uno `SequenceInputStream` che concatena gli stream dei singoli chunk: la codifica di ciascun chunk avviene progressivamente, senza costruire in anticipo un unico array contenente tutti i dati PCM della traccia. L'eventuale file già presente nella destinazione viene sovrascritto.

## Controller di importazione ed esportazione

`LoadSaveController` funge da *Facade* verso la GUI, esponendo quattro operazioni (`load`, `save`, `acknowledgeImport`, `acknowledgeExport`) tutte di tipo `IO[Unit]` e nascondendo la sua implementazione `LoadSaveControllerImpl`. Le dipendenze (`AppStateStore`, `AudioFormatRegistry`, `AudioProcessor = AudioProcessor.withRules`) sono fornite esplicitamente tramite costruttore (*Dependency Injection (DI)*).

```scala
def make(
    store: AppStateStore,
    formats: AudioFormatRegistry,
    processor: AudioProcessor = AudioProcessor.withRules
): Resource[IO, LoadSaveController] =
  for
    supervisor <- Supervisor[IO](await = false)
    importBusy <- Resource.eval(Ref.of[IO, Boolean](false))
    exportBusy <- Resource.eval(Ref.of[IO, Boolean](false))
  yield LoadSaveControllerImpl(store, formats, processor, supervisor, importBusy, exportBusy)

private def runInBackground(task: IO[Unit]): IO[Unit] =
  supervisor.supervise(task).void

private def startIfIdle(busy: Ref[IO, Boolean])(task: IO[Unit]): IO[Unit] =
  for
    wasBusy <- busy.getAndSet(true)
    _       <- if wasBusy then IO.unit else task.guarantee(busy.set(false))
  yield ()
```

La *factory* `make` restituisce una `Resource` che lega il ciclo di vita delle fibre in background a quello del controller tramite `Supervisor[IO](await = false)`: alla chiusura della risorsa, eventuali operazioni ancora attive vengono cancellate senza attenderne il completamento. Sia per l'importazione sia per l'esportazione (`importBusy` ed `exportBusy`), un `Ref[IO, Boolean]` agisce da guardia atomica tramite `getAndSet(true)`, evitando che due richieste ravvicinate dello stesso tipo avviino esecuzioni concorrenti. `guarantee(busy.set(false))` riporta il flag a `false` al termine dell'operazione, anche in caso di errore o cancellazione, permettendo di accettare nuove richieste.

```scala
private def runImport(path: Path): IO[Unit] =
  val operation =
    for
      _      <- store.dispatch(Command.UpdateImport(ImportState.Importing))
      result <- loadFrom(path).attempt
      _      <- store.dispatch(Command.UpdateImport(toImportState(result)))
    yield ()
  operation.onCancel(store.dispatch(Command.UpdateImport(ImportState.Idle)).void)

override def save(path: Path): IO[Unit] =
  runInBackground:
    for
      state <- store.current
      _     <- state.importState match
        case ImportState.Importing => IO.unit
        case _ => startIfIdle(exportBusy)(runExport(state.track, state.effects, path))
    yield ()
```

In `runImport`, `loadFrom` seleziona il reader adatto dal registro ed incapsula la chiamata `reader.load(path)` in `IO.defer`, così da intercettare con `.attempt` anche eventuali eccezioni sollevate durante la creazione dell'effetto. `toImportState` unifica successi, errori di dominio ed eccezioni impreviste negli stati `Imported` o `Failed`, mentre `onCancel` riporta lo stato a `Idle` in caso di cancellazione.

All'avvio della fibra in background, l'esportazione legge lo stato corrente tramite `store.current`. La traccia e gli effetti estratti da quello stato vengono mantenuti invariati per tutta la durata del salvataggio, indipendentemente dalle modifiche successive apportate all'applicativo. In `processAndSave`, `IO.delay(resolveAndProcess(...))` seleziona il writer adatto dal registro, e solo successivamente applica gli effetti con `processor.apply(track, effects)`, evitando elaborazioni inutili su formati non supportati. Completata l'elaborazione, il controller avvia la scrittura tramite `IO.defer`.

Il controller non esegue direttamente operazioni bloccanti sul file system: delega `IO.blocking` ai singoli reader e writer, occupandosi esclusivamente di comporre e coordinare gli effetti `IO` mediante `IO.delay` e `IO.defer`.

## Interfaccia grafica e logica di importazione ed esportazione

L'interfaccia grafica e la relativa logica sono suddivise in tre componenti:

- `TitlePane` è un `BorderPane` ScalaFX passivo che racchiude l'etichetta di stato, i pulsanti e la configurazione dei `FileChooser`, privo di logica applicativa.
- `LoadSaveLogic` coordina le interazioni, collegando gli eventi della GUI ai metodi del controller e osservando i flussi degli stati di importazione ed esportazione.
- `ErrorMessages` traduce gli errori di dominio e di risoluzione dei formati in messaggi comprensibili per l'utente.

Questa suddivisione riflette il *Single Responsibility Principle (SRP)*: la costruzione dei controlli, il coordinamento delle interazioni e la presentazione degli errori sono responsabilità distinte. Inoltre, l'organizzazione tra `TitlePane` e `LoadSaveLogic` segue il pattern *Passive View*, in cui la `view` si limita a esporre i controlli mentre la logica ne gestisce lo stato.

```scala
override def attach(pane: TitlePane): Unit =
  titlePane = Some(pane)
  refreshView()

  pane.importButton.onAction = _ =>
    chooseOpenFile(pane).foreach(controller.load(_).unsafeRunAndForget())

  pane.exportButton.onAction = _ =>
    chooseSaveFile(pane).foreach(controller.save(_).unsafeRunAndForget())

override def observe: IO[Unit] =
  val imports = store.importStateChanges.evalMap(state => IO(runOnGUIThread(handleImport(state))))
  val exports = store.exportStateChanges.evalMap(state => IO(runOnGUIThread(handleExport(state))))
  imports.merge(exports).compile.drain
```

L'interazione bidirezionale tra `view` e stato applicativo declina il pattern *Observer* in due forme:
- **Event listener sulla view:** in `attach`, `LoadSaveLogic` registra le callback sui pulsanti (`onAction`), incapsula in `Option[Path]` l'esito annullabile dei dialoghi della `view` ed avvia gli effetti `IO` del controller tramite `unsafeRunAndForget()`.
- **Monitoraggio dello stato:** in `observe`, la logica si sottoscrive ai due `Stream` FS2 esposti dallo store (`importStateChanges` ed `exportStateChanges`), già filtrati con `.changes` per scartare stati consecutivi identici. `evalMap` pianifica l'aggiornamento per ciascuna transizione, mentre `merge` consuma entrambi i flussi concorrentemente fino a `compile.drain`.

Poiché le fibre di `Cats Effect` eseguono sul pool di *compute* mentre `JavaFX` consente le modifiche al grafo della scena esclusivamente sul thread UI, `runOnGUIThread` (che per impostazione predefinita delega a `Platform.runLater`) delega `handleImport` e `handleExport` al thread `JavaFX`. Di conseguenza, i campi mutabili interni (`titlePane`, `importInProgress`, `exportInProgress`) risultano confinati a tale thread, evitando aggiornamenti concorrenti della `view`. In `AppSetup`, l'espressione `(ready >> loadSaveLogic.observe).background` avvia l'esecuzione in background di `loadSaveLogic.observe` soltanto dopo il completamento del `Deferred` `ready` in `ScalaFxApp.start`, garantendo che `attach` abbia già collegato il `TitlePane` prima che vengano elaborati i cambiamenti di stato dello store.

```scala
private def handleImport(importState: ImportState): Unit =
  importInProgress = importState == ImportState.Importing
  refreshView()

  importState match
    case ImportState.Imported(_) =>
      showAlert(AlertType.Information, "Import completed", "The track has been imported.")
      controller.acknowledgeImport.unsafeRunAndForget()
    case ImportState.Failed(error) =>
      showAlert(AlertType.Error, "Import failed", ErrorMessages.describe(error))
      controller.acknowledgeImport.unsafeRunAndForget()
    case _ => ()
```

In `handleImport` e `handleExport`, sugli stati terminali (`Imported`, `Failed`, `Exported`), dopo la chiusura del dialogo modale (`showAndWait`), la logica invoca `acknowledgeImport` o `acknowledgeExport` per riportare lo stato a `Idle`.

Questo reset esplicito è necessario perché, filtrando con `.changes` gli stati consecutivi uguali, due fallimenti identici di fila non verrebbero notificati come eventi distinti, se lo stato non tornasse prima a `Idle`.

I parametri `runOnGUIThread` e `showAlert` di `LoadSaveLogic.apply` incapsulano le strategie di scheduling sul thread UI e di visualizzazione degli avvisi, fornendo implementazioni predefinite sostituibili. Questa *Dependency Injection* (in forma di *Strategy* funzionale) consente di verificare la logica nei test senza dipendere dal thread `JavaFX` o dall'apertura effettiva di finestre modali.

\newpage
