\newpage

# Testing

## Tecnologie utilizzate

Per il testing sono stati usati due framework e uno strumento per misurare la copertura.

- **ScalaTest** è il framework di base, usato per il codice puro e sincrono.
- **MUnit** (con l'integrazione **munit-cats-effect**) si affianca a ScalaTest qualora il codice da testare ha a che fare con costrutti come `IO`, `Resource` e `fs2.Stream`. Questi componenti descrivono computazioni asincrone e concorrenti che non producono un risultato finché non vengono eseguite. La suite `CatsEffectSuite` permette di restituire un `IO` direttamente da `test(...)`, lo esegue nel runtime di cats-effect e ne verifica l'esito. Offre inoltre la possibilità di configurare un timeout oltre il quale il test fallisce invece di restare bloccato in attesa.
- **sbt-scoverage** misura la copertura (statement e branch) e genera il report attraverso il comando `sbt coverage testFull coverageReport`.

## Metodologia

Il team **non ha adottato il TDD** poiché si trattava del primo progetto in Scala per i membri del gruppo, e la scarsa dimestichezza sia con il linguaggio sia con lo stack di librerie (`cats-effect`, `fs2`) rendeva poco praticabile scrivere i test prima del codice. Si è quindi scelto di scrivere i test dopo l'implementazione, una volta stabilizzate le interfacce dei componenti.

L'assenza del TDD non ha però ridotto il rigore della verifica: i componenti principali sono stati testati in modo sistematico, con 297 test e una copertura del 93.55 % a livello di statement e del 91.80 % a livello di branch.

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

Tutti i test automatici si eseguono con `sbt testFull` e non richiedono display, scheda audio o risorse esterne: i file WAV di prova, quando servono, sono in `src/test/resources`. La demo non fa parte dei test automatici e si avvia a parte, con `sbt "Test/runMain scalasound.AudioPlaybackDemo"`.

## Copertura

La coverage è misurata con sbt-scoverage, escludendo il package `view` e i punti d'ingresso dell'applicazione (`Main`, `ScalaFxApp`, ecc.). La misura riguarda quindi i package `scalasound.model` e `scalasound.controller`, dove risiede la logica di business.

| Metrica                  | Valore                                 |
|--------------------------|----------------------------------------|
| Numero di test           | 297 (224 ScalaTest + 73 MUnit)         |
| Statement coverage       | 93.55 % (1189 / 1271)                  |
| Branch coverage          | 91.80 % (168 / 183)                    |
| Righe di codice misurate | 2499 in 32 file, 45 classi, 232 metodi |

| Package                                   | Statement coverage |
|-------------------------------------------|--------------------|
| `scalasound.controller`                   | 92.49 %            |
| `scalasound.model.domain`                 | 98.86 %            |
| `scalasound.model.io`                     | 100.00 %           |
| `scalasound.model.io.wav`                 | 93.56 %            |
| `scalasound.model.player`                 | 66.91 %            |
| `scalasound.model.processing`             | 97.20 %            |
| `scalasound.model.processing.effects`     | 100.00 %           |
| `scalasound.model.processing.processors`  | 100.00 %           |
| `scalasound.model.state`                  | 100.00 %           |

Il package `scalasound.model.player` è coperto al 66.91 % poiché la parte non testata si concentra in `SpeakerAudioOutput`, in particolare per quanto riguarda la gestione della `SourceDataLine` di Java Sound API (apertura, uso e riapertura al cambio di formato), che si interfaccia con la scheda audio del sistema fisico. La fedeltà acustica dell'audio in output è stata verificata manualmente con la demo di riproduzione `AudioPlaybackDemo`.

## Integrazione continua

Il workflow `ci.yml` esegue le suite di test in automatico a ogni push e pull request verso il branch `main`, in modo tale che il codice sia validato sia prima sia dopo l'integrazione. Se la compilazione o anche un solo test falliscono, il workflow si interrompe e lo step successivo, `sbt assembly`, non viene eseguito, per cui l'eseguibile è prodotto solo con codice che ha passato tutti i test.

## Esempi rilevanti

### StreamingAudioPlayer: gestione degli errori

Se l'`AudioProcessor` restituisce `Left(AudioError)`, il player stampa l'errore e riproduce l'`AudioChunk` originale senza interrompersi. Nel test il processore fallisce sempre, quindi in uscita arriva l'intera traccia originale.

```scala
test("a processor error falls back to the original chunk " +
  "and keeps playing") {
  val out = RecordingOutput()
  playToEnd(monoTrack, out, FailingProcessor).map: state =>
    assertEquals(out.chunks, monoTrack.chunks)
    assertEquals(state.playbackState, PlaybackState.Stopped)
}
```

Se l'`AudioOutput` lancia un'eccezione, la riproduzione passa a `Stopped` e il `chunkIndex` non avanza.

```scala
test("a failing output stops playback " +
  "without advancing the playhead") {
  playToEnd(monoTrack, FailingOutput).map: state =>
    assertEquals(state.playbackState, PlaybackState.Stopped)
    assertEquals(state.chunkIndex, 0)
}
```

L'helper `playToEnd` esegue l'intero scenario: avvia il player in background su un nuovo `AppStateStore`, importa la traccia, invia `Play`, attende che la riproduzione torni a `Stopped` e restituisce l'`AppState` finale su cui vengono fatte le asserzioni.

\newpage

### Importazione WAV

`RealWavImportSpec` verifica il comportamento di `WavAudioReader` su due file WAV reali (stereo, 44100 Hz, 16 bit, da 526 073 e 1 322 253 frame). I valori di riferimento non sono ricavati dal codice sotto test, ma calcolati a parte con il modulo `wave` di Python. Per ciascun file si controlla che la traccia abbia frequenza di campionamento, numero di frame e durata del file, che i chunk siano ben formati, che sei campioni di riferimento coincidano e che i campioni siano normalizzati in [-1.0, 1.0).

Elencare tutti i campioni nel test sarebbe impraticabile, essendo milioni. Per ciascun canale si confronta quindi l'hash SHA-256 della sequenza di campioni letta dal reader con quello calcolato in precedenza con Python sul file originale. Basta un solo campione diverso perché il test fallisca.

```scala
name should "match the reference SHA-256 of the whole " +
  "left and right sample sequences" in:
  val track = loadStereo(fixture.resource)

  sha256OfInt16LittleEndian(
    fromNormalisedFloatToInt16(track.chunks.flatMap(_.left))
  ) shouldBe fixture.leftSha256
  sha256OfInt16LittleEndian(
    fromNormalisedFloatToInt16(track.chunks.flatMap(_.right))
  ) shouldBe fixture.rightSha256
```

### Esportazione WAV

`WavAudioWriterSpec` verifica i file prodotti da tracce mono e stereo. Controlla che l'header sia corretto e che i dati coincidano byte per byte con i chunk codificati da `PcmChunks`. Tra i casi limite si controlla che:

- `NaN` e infiniti siano rifiutati con `InvalidSample`, senza creare alcun file;
- una cartella inesistente o una directory al posto del file producano l'errore `WriteFailure`;
- un file esistente sia sovrascritto completamente, senza byte residui di quello precedente;
- la dimensione massima (2^32^ − 44 byte) sia accettata e un byte in più produca l'errore `FileTooLarge`.

```scala
"Exporting invalid samples" should
  "reject NaN with InvalidSample and create no file" in withTempDir:
  tempDir =>
    val path = tempDir.resolve("nan.wav")

    runSave(invalidSampleTrack(Float.NaN), path) should matchPattern {
      case Left(AudioSaveError.InvalidSample(_)) =>
    }
    Files.exists(path) shouldBe false
```

### Flusso completo di importazione ed esportazione

`LoadSaveIntegrationSpec` verifica il percorso intero con componenti reali: store, `LoadSaveController`, `AudioFormatRegistry` con il formato WAV e `LoadSaveLogic`. Gli avvisi che quest'ultima mostrerebbe all'utente sono raccolti da un registratore, così il test può verificarli. Nello specifico, un file WAV reale viene importato ed esportato senza effetti in una cartella temporanea. La traccia esportata deve essere uguale all'originale e devono essere stati notificati gli avvisi di importazione e di esportazione completate. Il test è riportato in forma semplificata.

```scala
application(recorder).use: (store, loadSaveController, _) =>
  for
    _ <- loadSaveController.load(input)
    ...
    _ <- loadSaveController.save(dir.resolve("output"))
    ...
    exported <- WavAudioReader.load(dir.resolve("output.wav"))
  yield
    assertEquals(recorder.alerts.get, expectedAlerts)
    assertEquals(exported, track)
```

### Effetti audio: parametri e comportamento tra chunk

`AudioEffectsTest` verifica il comportamento di ogni effetto sui valori ammessi e il rifiuto con un `AudioEffectsError` quando un parametro esce dall'intervallo.

L'eco è l'esempio più interessante, perché il suo risultato dipende dai chunk precedenti. Con un chunk di 10 campioni, una frequenza di 1000 Hz e un ritardo di 10 ms, l'eco si ripete esattamente dopo un chunk. L'effetto mantiene un buffer dei campioni già elaborati, quindi il secondo chunk deve contenere il segnale originale più il primo chunk elaborato moltiplicato per `alpha`.

```scala
val echoEffect = EchoEffect(alpha, delay = 10, sampleRate = 1000.0f)
val firstChunk = echoEffect(chunk, chunkIndex = 0)
// il primo chunk resta invariato
firstChunk.value.channels shouldBe chunk.channels

val secondChunk = echoEffect(chunk, chunkIndex = 1)
val expectedSampleValue =
  defaultValueChunk + (alpha * defaultValueChunk)
secondChunk.value.channels.head shouldBe
  Vector.fill(sizeChunk)(expectedSampleValue)
```

\newpage

### Processore: ordine degli effetti

`AudioProcessorTest` verifica come i due processori implementati applicano gli effetti a un chunk. Il risultato atteso è calcolato da una pipeline di riferimento scritta nel test, indipendente dal processore. `SimpleAudioProcessor` deve rispettare l'ordine di inserimento, mentre `AudioProcessorWithRules` deve imporre l'ordine di priorità a prescindere da come gli effetti sono stati inseriti.

```scala
val simpleEffectsList = List(gain, volume, pan)
val orderedEffects    = List(gain, pan, volume)

"SimpleAudioProcessor" should "apply the effects in the same order " +
  "in which they were added to the list" in:
  val resultChunk =
    SimpleAudioProcessor(chunk, simpleEffectsList, StartingIndex)
  resultChunk.value shouldBe
    pipeline(simpleEffectsList, chunk, StartingIndex).value

"AudioProcessorWithRules" should
  "apply the effects in a specific order: [Gain, Pan, Volume]" in:
  val resultChunk =
    AudioProcessorWithRules(chunk, simpleEffectsList, StartingIndex)
  resultChunk.value shouldBe
    pipeline(orderedEffects, chunk, StartingIndex).value
```

\newpage