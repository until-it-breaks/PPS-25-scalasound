\newpage

# Testing

## Tecnologie utilizzate

Per il testing sono stati usati due framework e uno strumento per misurare la copertura.

- **ScalaTest** è il framework di base, usato per il codice puro e sincrono.
- **MUnit** (con l'integrazione **munit-cats-effect**) si affianca a ScalaTest qualora il codice da testare ha a che fare con costrutti come `IO`, `Resource` e `fs2.Stream`. Questi componenti descrivono computazioni asincrone e concorrenti che non producono un risultato finché non vengono eseguite. La suite `CatsEffectSuite` permette di restituire un `IO` direttamente da `test(...)`, lo esegue nel runtime di cats-effect e ne verifica l'esito. Offre inoltre la possibilità di configurare un timeout oltre il quale il test fallisce invece di restare bloccato in attesa.
- **sbt-scoverage** misura la copertura (statement e branch) e genera il report attraverso il comando `sbt coverage testFull coverageReport`.

## Metodologia

Il team **non ha adottato il TDD** poichè si trattava del primo progetto in Scala per i membri del gruppo, e la scarsa dimestichezza sia con il linguaggio sia con lo stack di librerie (`cats-effect`, `fs2`) rendeva poco praticabile scrivere i test prima del codice. Si è quindi scelto di scrivere i test dopo l'implementazione, una volta stabilizzate le interfacce dei componenti.

L'assenza del TDD non ha però ridotto il rigore della verifica: i componenti principali sono stati testati in modo sistematico, con 294 test e una copertura del *93.02%* a livello di statement e dell'*90.40%* a livello di branch.

Le suite in `src/test` sono di tipologie diverse, riassunte nella tabella seguente.

+----------------------+----------------------------------------------+----------------------------------------+
| Tipologia            | Suite                                        | Cosa verificano                        |
+======================+==============================================+========================================+
| Unit sulla logica    | `AudioTrackSpec`\                            | dominio, stato, effetti,               |
| pura                 | `AppStateSpec`\                              | processore, formati WAV,               |
|                      | `AudioEffectsTest`\                          | registro dei formati e                 |
|                      | `AudioProcessorTest`\                        | codifica PCM                           |
|                      | `AudioFormatRegistrySpec`\                   |                                        |
|                      | `FileExtensionsSpec`\                        |                                        |
|                      | `WavFormatSpec`\                             |                                        |
|                      | `Pcm16Spec`\                                 |                                        |
|                      | `PcmChunksSpec`\                             |                                        |
|                      | `SpeakerAudioOutputSpec`                     |                                        |
+----------------------+----------------------------------------------+----------------------------------------+
| Unit su componenti   | `PlaybackControllerSpec`\                    | comportamento osservabile di           |
| con `IO`             | `EffectsControllerSpec`\                     | controller, store e output             |
|                      | `LoadSaveControllerSpec`\                    |                                        |
|                      | `AppStateStoreSpec`\                         |                                        |
|                      | `AudioOutputSpec`                            |                                        |
+----------------------+----------------------------------------------+----------------------------------------+
| I/O su file          | `WavAudioReaderSpec`\                        | lettura e scrittura WAV su file        |
|                      | `WavAudioWriterSpec`\                        | temporanei                             |
|                      | `WavAudioFormatSpec`                         |                                        |
+----------------------+----------------------------------------------+----------------------------------------+
| Integrazione         | `StreamingAudioPlayerSpec`\                  | più componenti usati insieme e         |
|                      | `LoadSaveIntegrationSpec`\                   | round-trip di esportazione e           |
|                      | `WavAudioExportImportSpec`                   | reimportazione                         |
+----------------------+----------------------------------------------+----------------------------------------+
| Validazione su dati  | `RealWavImportSpec`                          | import di WAV reali, confrontato con   |
| reali                |                                              | valori di riferimento calcolati in     |
|                      |                                              | Python                                 |
+----------------------+----------------------------------------------+----------------------------------------+
| Manuale              | `AudioPlaybackDemo`                          | riproduzione audio attraverso la       |
|                      |                                              | scheda audio                           |
+----------------------+----------------------------------------------+----------------------------------------+

`WavFixtures` non è una suite, ma l'insieme di utilità che costruisce i file WAV di prova.

Tutti i test automatici si eseguono direttamente con `sbt testFull`: non richiedono display, scheda audio o file esterni al repository, dato che i file WAV di prova, se necessari, si trovano in `src/test/resources`. La demo non fa parte dei test automatici e si avvia a parte, con `sbt "Test/runMain scalasound.AudioPlaybackDemo"`.

## Copertura

La coverage è misurata con sbt-scoverage, escludendo il package `view` e i punti d'ingresso dell'applicazione (`Main`, `ScalaFxApp`, ecc.). La misura riguarda quindi i package `scalasound.model` e `scalasound.controller`, dove risiede la logica di business dell'applicazione.

| Metrica                  | Valore                                 |
|--------------------------|----------------------------------------|
| Numero di test           | 294 (224 ScalaTest + 70 MUnit)         |
| Statement coverage       | 93.02 % (1146 / 1232)                  |
| Branch coverage          | 90.40 % (160 / 177)                    |
| Righe di codice misurate | 2468 in 32 file, 45 classi, 229 metodi |

| Package                                   | Statement coverage |
|-------------------------------------------|--------------------|
| `scalasound.controller`                   | 90.32 %            |
| `scalasound.model.domain`                 | 98.86 %            |
| `scalasound.model.io`                     | 94.21 %            |
| `scalasound.model.player`                 | 66.91 %            |
| `scalasound.model.processing`             | 97.20 %            |
| `scalasound.model.processing.effects`     | 100.00 %           |
| `scalasound.model.processing.processors`  | 100.00 %           |
| `scalasound.model.state`                  | 100.00 %           |

Il package `scalasound.model.player` è coperto al 66.91% poichè la parte non testata si concentra in `SpeakerAudioOutput`, in particolare per quanto riguarda la gestione della `SourceDataLine` di Java Sound API (apertura, uso e riapertura al cambio di formato), che si interfaccia con la scheda audio del sistema fisico. È stata invece testata la codifica degli `AudioChunk` in byte da inviare alla libreria suddetta. La fedeltà acustica dell'audio in output è stata verificata manualmente con la demo di riproduzione (`AudioPlaybackDemo`).

## Esempi rilevanti

### StreamingAudioPlayer: gestione degli errori

Il comportamento di fronte agli errori è una scelta di progetto, quindi è verificato esplicitamente:

- se l'`AudioProcessor` restituisce `Left(AudioError)`, il player stampa l'errore e riproduce l'`AudioChunk` originale senza interrompersi;
- se l'`AudioOutput` lancia un'eccezione, la riproduzione passa a `Stopped` e il play head non avanza (`chunkIndex == 0`), ma il player resta attivo e pronto a un nuovo `Play`.

```scala
test("a processor error falls back to the original chunk and keeps playing") {
  val out = RecordingOutput()
  playToEnd(monoTrack, out, FailingProcessor).map: state =>
    assertEquals(out.chunks, monoTrack.chunks)
    assertEquals(state.playbackState, PlaybackState.Stopped)
}

test("a failing output stops playback without advancing the play head") {
  playToEnd(monoTrack, FailingOutput).map: state =>
    assertEquals(state.playbackState, PlaybackState.Stopped)
    assertEquals(state.chunkIndex, 0)
}
```

L'helper `playToEnd` incapsula lo scenario: crea l'`AppStateStore`, avvia il player in background, importa la traccia, avvia il waiter su `Stopped`, invia `Play` e restituisce l'`AppState` finale.

### AppState e AppStateStore: flussi di stati senza duplicati

`AppState` è immutabile e `AppStateStore` lo espone come uno stream di cambiamenti. I flussi derivati (`playbackStateChanges`, `playheadChanges`, `playbackProgressChanges`, `importStateChanges`, `exportStateChanges`) devono emettere un valore solo quando cambia la parte di stato a cui sono interessati. Per verificarli in isolamento, i test usano un'implementazione dello store che emette una lista fissa di stati, ottenuti con `copy` a partire dallo stato iniziale: il risultato atteso si legge direttamente nel test. Le transizioni dello stato (`Play` senza traccia ignorato, `Pause` solo da `Playing`, `Stop` che azzera il play head, clamp del `seek`, un solo effetto per tipo) sono funzioni pure e sono testate in `AppStateSpec` senza alcuna infrastruttura.

```scala
test("playbackStateChanges should drop repeated playback states") {
  val store = storeEmitting(
    base,
    base.copy(playbackState = PlaybackState.Playing),
    // cambia solo l'indice del chunk, non lo stato di riproduzione
    base.copy(playbackState = PlaybackState.Playing, chunkIndex = 1),
    base.copy(playbackState = PlaybackState.Paused)
  )
  store.playbackStateChanges.compile.toList.map { result =>
    assertEquals(
      result,
      List(PlaybackState.Stopped, PlaybackState.Playing, PlaybackState.Paused)
    )
  }
}
```

### Flusso completo di importazione ed esportazione

`LoadSaveIntegrationSpec` verifica il percorso intero con componenti reali: store, `LoadSaveController`, `AudioFormatRegistry` con il formato WAV (`WavAudioReader` e `WavAudioWriter`) e l'osservatore che aggiorna la view. La view è sostituita da una versione che registra gli eventi ricevuti. Un file WAV reale viene scritto in una cartella temporanea, importato tramite il controller ed esportato di nuovo, quindi riletto: la traccia esportata deve essere uguale a quella originale e la view deve aver ricevuto la traccia importata e l'esito dell'esportazione. Un secondo test verifica che gli effetti attivi siano applicati alla traccia esportata senza modificare quella caricata. Un terzo verifica che gli errori (file inesistente, salvataggio senza traccia) arrivino alla view e vengano azzerati dopo la conferma dell'utente, e un quarto che un file con estensione non supportata sia segnalato come errore di importazione senza essere aperto. La cartella temporanea è gestita da una `Resource` e viene eliminata anche se il test fallisce.

### Importazione di file WAV reali

`RealWavImportSpec` verifica il lettore su due file WAV reali (stereo, 44100 Hz, 16 bit, da 526 073 e 1 322 253 frame). I valori di riferimento non sono stati ricavati dal codice sotto test, ma calcolati in modo indipendente con il modulo `wave` di Python. Per ciascun file si controlla che:

- la traccia sia stereo, con la frequenza di campionamento e il numero di frame del file, e la durata coerente;
- i chunk siano ben formati: canali allineati e tutti della stessa dimensione tranne l'ultimo, più corto;
- sei campioni di riferimento (all'inizio, a metà e alla fine) coincidano con quelli del file;
- l'hash SHA-256 dell'intera sequenza dei campioni, canale per canale, coincida con quello di riferimento;
- tutti i campioni siano normalizzati nell'intervallo [-1.0, 1.0).

L'hash verifica ogni campione del file senza doverli elencare uno per uno nel test. I test sono generati con un ciclo sui file di riferimento, quindi aggiungerne un altro richiede solo di descriverne i valori.

### Esportazione WAV: byte esatti e casi limite

`WavAudioWriterSpec` confronta il file prodotto, byte per byte, con un WAV PCM a 16 bit costruito a parte da `WavFixtures`, e legge l'header campo per campo (dimensioni RIFF, byte rate, block align) per mono e stereo. Verifica poi che i dati siano la concatenazione dei chunk codificati da `PcmChunks` e i casi limite. Le regole di conversione dei campioni (arrotondamento, saturazione di +1.0 a 32767, ordine little-endian) sono invece verificate a parte, in `Pcm16Spec` e `PcmChunksSpec`:

- `NaN` e infiniti sono rifiutati con `InvalidSample` e non viene creato alcun file;
- una cartella inesistente o un percorso che è una directory producono `WriteFailure`;
- sovrascrivere un file non lascia byte residui del contenuto precedente;
- la dimensione massima del file (2^32^ − 44 byte) è accettata, un byte in più produce `FileTooLarge` e il calcolo non va in overflow nemmeno con `Long.MaxValue`.

### Effetti audio: parametri e comportamento tra chunk

I test degli effetti (`AudioEffectsTest`) ricavano i limiti dei parametri dalla configurazione stessa degli effetti (`AudioConfiguration`), così non restano allineati a valori scritti a mano: per ogni effetto si verifica il comportamento sui valori ammessi, sia su chunk mono sia su chunk stereo, e il rifiuto con un `AudioEffectsError` quando un parametro esce dall'intervallo. L'eco è l'esempio più interessante, perché il suo risultato dipende dai chunk precedenti: con un chunk di 10 campioni, una frequenza di 1000 Hz e un ritardo di 10 ms, il ritardo coincide esattamente con la durata di un chunk, e il secondo chunk deve contenere il segnale originale più quello del primo moltiplicato per `alpha`.

```scala
val echoEffect = EchoEffect(alpha, delay = 10, sampleRate = 1000.0f)
val firstChunk = echoEffect(chunk, chunkIndex = 0)
// niente da ripetere: il primo chunk è uguale all'ingresso
firstChunk.value.channels shouldBe chunk.channels

val secondChunk = echoEffect(chunk, chunkIndex = 1)
val expectedSampleValue = defaultValueChunk + (alpha * defaultValueChunk)
secondChunk.value.channels.head shouldBe
  Vector.fill(sizeChunk)(expectedSampleValue)
```

### Processore: ordine degli effetti

`AudioProcessorTest` verifica come `SimpleAudioProcessor` e `AudioProcessorWithRules` applicano la lista di effetti a un chunk e a un'intera traccia. Il risultato atteso non è calcolato con il codice sotto test, ma con una pipeline di riferimento scritta nel test (una `foldLeft` sugli effetti che passa l'indice del chunk agli effetti dipendenti dal tempo). Si controlla che il processore semplice rispetti l'ordine di inserimento, che quello con regole imponga un ordine di priorità (identità, gain, distorsione, eco, pan, autopan, volume) indipendentemente dall'ordine di inserimento, che un effetto sconosciuto venga comunque accettato e applicato per ultimo, che l'indice del chunk cresca lungo la traccia e che una traccia mono diventi stereo solo se un effetto produce chunk stereo. Un errore di un effetto viene riportato con il nome dell'effetto che l'ha causato.

```scala
it should "enforce priority on the effects even if a new effect previously " +
  "unknown is passed to the processor" in:
  val unorderedList = List(gain, NewEffect, volume, pan)
  val orderedList = List(gain, pan, volume, NewEffect)
  val result = AudioProcessorWithRules(chunk, unorderedList, StartingIndex)
  result.value shouldBe pipeline(orderedList, chunk, StartingIndex).value
```

## Integrazione continua

Il workflow "Scala CI" (`ci.yml`) esegue le suite di test in automatico a ogni push e pull request verso il branch `main`, in modo tale che il codice sia validato sia prima sia dopo l'integrazione. Se la compilazione o anche un solo test falliscono, il workflow si interrompe e lo step successivo, `sbt assembly`, non viene eseguito, per cui un jar è prodotto solo da codice con tutti i test passati.

\newpage