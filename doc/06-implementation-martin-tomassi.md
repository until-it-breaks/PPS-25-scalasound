## Martin Tomassi

### Panoramica dei contributi

Il mio contributo al progetto si è focalizzato nelle seguenti aree:

- **Astrazioni I/O, registro dei formati e gestione degli errori**: i trait `AudioReader` e `AudioWriter`, il descrittore `AudioFileType`, l'`AudioFormatRegistry` e la gerarchia degli errori (`AudioLoadError`, `AudioSaveError`, `FormatResolutionError`, `AudioFormatRegistryError`).
- **Implementazione del formato WAV**: `WavAudioReader` e `WavAudioWriter` basati sulla Java Sound API, insieme ai componenti ausiliari `WavFormat`, `ChannelLayout`, `Pcm16`, `PcmChunks` e al descrittore `WavAudioFormat`.
- **Controller di importazione ed esportazione**: `LoadSaveController`, che coordina in background le operazioni di caricamento e salvataggio aggiornando lo stato applicativo.
- **Interfaccia grafica e logica Load/Save**: `TitlePane`, `LoadSaveLogic` ed `ErrorMessages`.
- **Modello di dominio**: l'`opaque type` `SampleRate`, gli errori di dominio (`AudioDomainError`) ed i costruttori di `MonoTrack` e `StereoTrack`.
- **Testing**: definizione ed implementazione di tutti i test riguardante la parte I/O ed il relativo controller.

### Astrazioni I/O, gestione degli errori e registro dei formati

L'importazione ed esportazione sono modellate da due trait distinti con un solo metodo ciascuno, entrambi con tipo di ritorno `IO[Either[E, A]]`: `IO` descrive l'effetto differendone l'esecuzione al chiamante, mentre `Either` rappresenta i fallimenti previsti come valori tipizzati, lasciando il canale d'errore di `IO` alle sole eccezioni impreviste. Separando i due contratti, si applica l'*Interface Segregation Principle (ISP)*: l'importazione ed esportazione hanno errori diversi ed un formato può supportare una sola delle due. Il descrittore `AudioFileType` riflette questa scelta modellando reader e writer come `Option`:

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

`AudioFormatRegistry` centralizza la selezione della strategia a partire dall'estensione del percorso (**Registry** e **Strategy**): il controller riceve un `AudioReader` o un `AudioWriter` senza conoscere il formato concreto (**Dependency Inversion Principle**). La classe è immutabile, ha costruttore privato e si istanzia soltanto tramite la **Factory** `AudioFormatRegistry.make`, che verifica la buona formazione ed unicità delle estensioni normalizzate (`FileExtensions`). Un'istanza esistente è quindi valida per costruzione; in `AppSetup`, `IO.fromEither` converte un eventuale `AudioFormatRegistryError` in `IllegalStateException`, bloccando l'avvio in caso di configurazione errata.

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

In `readerFor` e `writerFor`, la `for`-comprehension opera sulla monade `Either`: ogni passaggio intermedio converte un `Option` tramite `toRight`, così il primo fallimento interrompe la catena propagando il caso specifico di `FormatResolutionError`. Nella scrittura, l'assenza di estensione non scaturisce un errore: `withDefaultFormat` seleziona il primo formato registrato dotato di writer e ne appende la `primaryExtension` al percorso tramite `ResolvedWriter`.

In `AudioFormatRegistry.make`, le for-comprehension operano sulla monade `List`: la prima associa ogni formato alle rispettive estensioni normalizzate, mentre la seconda individua quelle malformate. Le collisioni vengono successivamente rilevate raggruppando le associazioni per estensione tramite `groupMap`, verificando quali siano rivendicate da più formati. Il registro si basa unicamente sul nome del file; la verifica che il contenuto rispetti davvero il formato spetta al reader.

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

Gli errori sono organizzati in quattro `enum` (`AudioLoadError`, `AudioSaveError`, `FormatResolutionError`, `AudioFormatRegistryError`), che abilitano il controllo di esaustività del compilatore nei `match`. Sia `AudioLoadError` sia `AudioSaveError` incapsulano i fallimenti di selezione nel caso `UnresolvedFormat(cause: FormatResolutionError)`, evitando di duplicare i quattro casi di risoluzione e consentendo a `ErrorMessages` di tradurli in un solo punto.

Dal punto di vista dell'*Open/Closed Principle (OCP)*, aggiungere un nuovo formato richiede una coppia reader/writer (opzionali), un nuovo `AudioFileType` e la sua registrazione in `AudioFormatRegistry.make` dentro `AppSetup`, lasciando invariati registro, controller ed `ErrorMessages`. Attualmente l'applicativo registra soltanto il WAV.

### Implementazione del formato WAV

Poiché reader e writer producono e consumano `AudioTrack`, il modello di dominio garantisce gli invarianti strutturali delle tracce prima dell'I/O. In `SampleRate.scala`, la frequenza di campionamento è definita come `opaque type`:

```scala
opaque type SampleRate = Int

object SampleRate:
  def from(value: Int): Either[AudioDomainError, SampleRate] =
    Either.cond(value > 0, value, AudioDomainError.InvalidSampleRate(value))

  extension (sampleRate: SampleRate) def value: Int = sampleRate
```

All'interno del file di definizione `SampleRate` coincide con `Int`, consentendo a `from` ed all'extension method `value` di operare senza wrapper; all'esterno è un tipo distinto, eliminando la *primitive obsession*, impedendo così di scambiare la frequenza con altri interi del dominio. L'unico modo per ottenerlo è `SampleRate.from`, che impone la positività (`> 0`), mentre il limite superiore legato alla codifica (`2^24 Hz`) resta confinato nel writer WAV.

`MonoTrack` e `StereoTrack` hanno costruttore privato e due factory: `make` accetta un `Int` e lo valida con `SampleRate.from`, mentre `fromValidated` accetta un `SampleRate` già validato (usato dal reader dopo l'analisi dell'intestazione). `MonoTrack.fromValidated` è totale, laddove `StereoTrack.fromValidated` restituisce comunque un `Either[AudioDomainError, StereoTrack]` perché l'uguaglianza delle lunghezze dei canali sinistro e destro in ogni chunk (`DifferentLengthsStereoChannels`) resta un invariante da verificare a runtime.

All'interno del package `scalasound.model.io.wav` la struttura segue il principio **Functional Core / Imperative Shell**: gli effetti di I/O e l'interazione con `javax.sound.sampled` sono confinati in `WavAudioReader` e `WavAudioWriter`, mentre `WavFormat`, `ChannelLayout`, `Pcm16` e `PcmChunks` sono i componenti puri.

Il sottoinsieme supportato è il PCM con segno a 16 bit, little-endian, mono o stereo. `WavFormat.from` adatta l'`AudioFormat` di Java Sound a un valore validato del dominio (`WavFormat(layout, sampleRate)`) mediante una `for`-comprehension su `Either`, distinguendo i formati ben formati ma non supportati (`UnsupportedFormat`: codifica diversa, big-endian, più di due canali) dalle intestazioni incoerenti (`CorruptedData`: `frameSize` errato o frequenza non valida).

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

La quantizzazione calcola il prodotto in `Double` per preservare precisione e satura i valori finiti fuori scala all'intervallo `[-32768, 32767]` (così `+1.0` viene limitato a `32767`). Poiché `math.round(Float.NaN)` vale `0` e trasformerebbe un campione non finito in silenzio, `WavAudioWriter` verifica `isRepresentable` sull'intera traccia prima di scrivere. Nella decodifica little-endian, il byte basso viene mascherato con `0xff` per evitare l'estensione del segno, mentre quello alto mantiene il segno del `Byte` per ricostruire correttamente il `Short` con segno. `PcmChunks` estende le conversioni ai chunk: in scrittura, `encode` utilizza un `ByteBuffer` locale per produrre i dati PCM interleaved; in lettura, `decodeMono` e `decodeStereo` ricostruiscono i campioni a partire dall'array di byte, suddividendoli in chunk da 1024 frame.

Lettura e scrittura incapsulano la frontiera bloccante con `IO.blocking`:

```scala
private def openStream(file: File): Resource[IO, AudioInputStream] =
  Resource.fromAutoCloseable(IO.blocking(AudioSystem.getAudioInputStream(file)))

private def readRawAudioFile(file: File): IO[Either[AudioLoadError, RawAudio]] =
  for attempted <- openStream(file).use(stream => IO.blocking(readFromStream(stream))).attempt
    yield attempted match
      case Right(result)   => result
      case Left(exception) => Left(fromThrowable(exception))
```

In `WavAudioReader.load`, dopo aver verificato `Files.isRegularFile(path)` (`FileNotFound`), l'apertura dello stream è gestita da una `Resource` che ne assicura la chiusura anche in caso di errore o cancellazione; `.attempt` cattura le eccezioni e `fromThrowable` le mappa negli errori di dominio (`EOFException` precede `IOException` nella gerarchia e produce `CorruptedData` anziché `ReadFailure`). La lettura non avviene in streaming continuo: `readFromStream` carica l'intero file in memoria con `readAllBytes()`, verifica coerenza tra byte letti e frame dichiarati, e solo in seguito `decode` suddivide i campioni in chunk per elaborazione e riproduzione.

In `WavAudioWriter.save`, `prepare(track)` è racchiusa in `IO.delay` ed esegue tutte le validazioni prima di toccare il file system: tipo di traccia, frequenza non superiore a `2^24 Hz` (limite di rappresentabilità esatta nel `Float` richiesto da `AudioFormat`), dimensione entro il limite PCM a 32 bit verificata con `Try(Math.multiplyExact(...))` (`FileTooLarge`) e assenza di campioni non finiti tramite una `for`-comprehension pigra su `Iterator` arrestata al primo errore da `.nextOption()`. Solo a validazione superata `writeAudioToFile` esegue la scrittura bloccante alimentando `AudioSystem.write` con uno `SequenceInputStream` che codifica un chunk per volta in modo pigro, sovrascrivendo l'eventuale file di destinazione.

### Controller di importazione ed esportazione

`LoadSaveController` funge da **Facade** verso la GUI esponendo quattro operazioni (`load`, `save`, `acknowledgeImport`, `acknowledgeExport`), tutte di tipo `IO[Unit]`, e nascondendo l'implementazione `LoadSaveControllerImpl`. Le dipendenze (`AppStateStore`, `AudioFormatRegistry`, `AudioProcessor = AudioProcessor.withRules`) sono fornite esplicitamente tramite costruttore (**Dependency Injection**).

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

La factory `make` restituisce una `Resource` che lega il ciclo di vita delle fibre in background a quello del controller tramite `Supervisor[IO](await = false)`: alla chiusura dell'applicazione eventuali operazioni ancora attive vengono cancellate e non attese. Per ogni direzione un `Ref[IO, Boolean]` agisce da guardia atomica tramite `getAndSet(true)`, evitando che due richieste ravvicinate dello stesso tipo avviino due esecuzioni concorrenti, mentre `guarantee(busy.set(false))` ripristina lo stato libero al termine, su errore o su cancellazione.

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

In `runImport`, `loadFrom` risolve il reader dal registro e avvolge `reader.load(path)` in `IO.defer` affinché anche un'eccezione sollevata durante la costruzione dell'effetto venga intercettata da `.attempt`; `toImportState` unifica successi, errori di dominio ed eccezioni impreviste in `Imported` o `Failed`, mentre `onCancel` riporta lo stato a `Idle`.

L'esportazione legge una singola istantanea con `store.current` così da salvare la traccia e gli effetti attivi al momento della richiesta anche se l'utente modifica lo stato durante la scrittura. In `processAndSave`, `IO.delay(resolveAndProcess(...))` risolve il writer prima di invocare `processor.apply(track, effects)`, evitando di elaborare l'intera traccia se la destinazione non è supportata. Il controller delega `IO.blocking` a reader e writer, limitandosi a `IO.delay` e `IO.defer`. Due limiti emergono dal codice: il controllo `state.importState` in `save` non è atomico rispetto all'avvio di un'importazione (la vista aggiunge una seconda protezione disabilitando il pulsante Export) e la cancellazione di una fibra viene osservata solo al rientro dalla chiamata bloccante su Java Sound.

### Interfaccia grafica e logica di importazione ed esportazione

La presentazione è suddivisa in tre componenti: `TitlePane` è un `BorderPane` ScalaFX passivo con etichetta di stato, pulsanti e configurazioni dei `FileChooser`; `LoadSaveLogic` collega eventi e flussi di stato (**Observer**); `ErrorMessages` traduce gli errori in testo per l'utente.

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

`attach` incapsula in `Option[Path]` l'esito annullabile dei dialoghi nativi e avvia gli effetti del controller con `unsafeRunAndForget()`, richiedendo un parametro contestuale `(using IORuntime)` in `LoadSaveLogic.apply` (unico impiego di `using`, riservato al runtime tecnico). L'osservazione utilizza due flussi FS2 (`importStateChanges` ed `exportStateChanges`), già filtrati con `.changes` dallo store per scartare stati consecutivi identici: `evalMap` pianifica l'aggiornamento per ciascun elemento e `merge` consuma entrambi i flussi concorrentemente fino a `compile.drain`. In `AppSetup`, `(ready >> loadSaveLogic.observe).background` avvia la fibra di osservazione solo dopo il completamento del `Deferred` `ready` in `ScalaFxApp.start`, garantendo che `attach` abbia già collegato il `TitlePane`.

Poiché le fibre Cats Effect eseguono su thread del pool di compute mentre JavaFX consente modifiche al grafo di scena solo sul thread UI, `runOnGUIThread` (di default `Platform.runLater`) delega `handleImport` e `handleExport` al thread JavaFX, mantenendo i campi mutabili interni (`titlePane`, `importInProgress`, `exportInProgress`) confinati a tale thread per convenzione e restituendo subito il controllo al flusso FS2:

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

`refreshView` disabilita `exportButton` durante importazione ed esportazione, mentre mantiene attivo `importButton` durante l'esportazione in coerenza con lo snapshot catturato dal controller. Sugli stati terminali (`Imported`, `Failed`, `Exported`), dopo il dialogo modale (`showAndWait`), la logica invoca `acknowledgeImport` o `acknowledgeExport` per riportare lo stato a `Idle`: questo passaggio è necessario anche perché, eliminando `.changes` i duplicati consecutivi, due fallimenti identici di fila non verrebbero notificati senza il ritorno a `Idle`. Gli stati intermedi come `Importing` possono essere saltati su file molto piccoli senza impatti, poiché le notifiche dipendono dagli stati terminali.

\newpage
