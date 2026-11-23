# Design di Dettaglio

Due scelte di fondo valgono per tutti i componenti:

- **Immutabilità.** Tracce, chunk, stato ed effetti sono valori immutabili; ogni trasformazione produce un nuovo valore.
- **Errori come valori.** Le operazioni che possono fallire restituiscono un `Either` il cui errore è un'enumerazione (`AudioError`, `AudioLoadError`, `AudioSaveError`). Le eccezioni di Java Sound e del file system sono intercettate ai bordi e trasformate in errori. L'unica eccezione lanciata dal modello è quella delle precondizioni (`require`) nella costruzione delle tracce.

## Organizzazione dei package

All'interno del package radice `scalasound`, il codice si articola nella seguente struttura:

- **`model`**:
  - `model.domain`: le classi di dominio `AudioChunk` (mono e stereo) e `AudioTrack` (mono e stereo).
  - `model.state`: lo stato globale immutabile e i comandi che lo modificano, e lo store reattivo.
  - `model.io`: lettura e scrittura dei file audio, con le implementazioni per il formato WAV e i relativi errori.
  - `model.player`: il motore di riproduzione e l'output audio.
  - `model.processing`: gli effetti, i processori audio e i relativi errori.
- **`controller`**: i tre controller di riproduzione, importazione/esportazione ed effetti.
- **`view`**:
  - `view.panels`: i pannelli grafici (riquadro della traccia e pannello degli effetti) con la relativa logica di presentazione e il collegamento ai controller.
  - `ScalaFxApp` e `AppDependencies`: costruzione della finestra principale e dipendenze di cui la view ha bisogno.
- `AppSetup` (composizione dei componenti) e `Main` (avvio dell'applicazione).

## Model

### Dominio

Il dominio è stato progettato dall'intero gruppo. L'audio è rappresentato da due gerarchie di tipi algebrici, che rendono impossibili gli stati non validi.

- `AudioChunk` è un'enumerazione con due casi: `MonoChunk`, con un solo vettore di campioni, e `StereoChunk`, con i canali sinistro e destro separati. È l'unità alla base dell'elaborazione, della riproduzione e delle operazioni di importazione ed esportazione.
- `AudioTrack` è il trait che rappresenta un brano: espone la frequenza di campionamento e il vettore di chunk che lo compone. Ha due implementazioni, `MonoTrack` (composta da chunk mono) e `StereoTrack` (composta da chunk stereo), che verificano le precondizioni alla costruzione con `require`.

![Gerarchia di tracce e chunk](doc/img/05-detailed-design/dominio.png){ width=80% }

Scelte di design:

- la traccia è divisa in chunk di dimensione fissa (1024 frame quando importata), così l'audio viene elaborato e riprodotto un blocco alla volta, solo quando necessario;
- i campioni sono `Float` normalizzati in [-1, 1): tutti gli effetti lavorano su un'unica scala, indipendente dalla profondità di bit del file di provenienza, e il calcolo in virgola mobile non ha i problemi di overflow degli interi a 16 bit (un guadagno potrebbe superare il valore massimo). Il `Float` inoltre occupa la metà del `Double`;
- `chunks` e i campioni (`samples`, `left`, `right`) sono contenuti in `Vector`, la sequenza immutabile di Scala con accesso per indice efficiente, che facilita l'operazione di seek. In questo modo portare la testina in un punto qualsiasi della traccia non richiede di percorrere i chunk precedenti, come invece accadrebbe con una `List`.

Casi limite:

- una traccia stereo i cui canali hanno lunghezze diverse, o una frequenza non positiva, vengono rifiutate alla costruzione;
- l'ultimo chunk di una traccia può contenere meno frame degli altri.

### Stato e comandi

Lo stato globale è contenuto nella `case class` immutabile `AppState`, composta dai seguenti campi:

| Campo | Tipo | Significato |
|----------------|------------------------|--------------------------------------------|
| `playbackState` | `PlaybackState` | fase della riproduzione: `Stopped`, `Playing` o `Paused` |
| `track` | `Option[AudioTrack]` | traccia caricata, se presente |
| `chunkIndex` | `Int` | posizione della testina, come indice del chunk corrente |
| `effects` | `List[AudioEffect]` | effetti attivi, al più uno per tipo |
| `importState` | `ImportState` | ciclo dell'importazione: `Idle`, `Importing`, `Imported(track)` o `Failed(error)` |
| `exportState` | `ExportState` | ciclo dell'esportazione: `Idle`, `Exporting`, `Exported` o `Failed(error)` |

Lo stato iniziale non ha traccia né effetti, ha la riproduzione `Stopped`, la testina a zero e importazione ed esportazione in stato `Idle`. Ogni modifica è espressa da un `Command` (`Play`, `Pause`, `Stop`, `NextChunk`, `SeekToIndex`, `AddEffect`, `RemoveAll`, `UpdateImport`, `UpdateExport`) e applicata dalla funzione `AppState.handle`, che restituisce un nuovo `AppState` senza alterare il precedente.

![Stati della riproduzione audio](doc/img/05-detailed-design/stati-riproduzione.png){ width=70% }

Ogni comando ha un effetto preciso sullo stato, definito in `handle`:

- `Play` porta la riproduzione in `Playing`, ma solo se è caricata una traccia;
- `Pause` porta la riproduzione in `Paused`, ma solo se è in corso;
- `Stop` porta la riproduzione in `Stopped` e riporta l'indice a zero;
- `NextChunk` fa avanzare l'indice di un chunk, ma solo durante la riproduzione;
- `SeekToIndex` imposta l'indice, limitandolo all'intervallo valido della traccia;
- `AddEffect` aggiunge un effetto sostituendo quello dello stesso tipo, quindi nella lista c'è al massimo un effetto per tipo, mentre `RemoveAll` la svuota;
- `UpdateImport` con `Imported` sostituisce la traccia, ferma la riproduzione, riporta l'indice a zero e mantiene gli effetti già scelti; con gli altri stati aggiorna solo lo stato di importazione, e `UpdateExport` fa lo stesso con quello di esportazione.

Importazione ed esportazione seguono lo stesso ciclo, rappresentato da semplici valori dello stato: `Idle`, operazione in corso, esito (riuscita o fallita) e ritorno a `Idle` quando l'esito viene confermato.

![Stati di importazione ed esportazione](doc/img/05-detailed-design/stati-import-export.png){ width=95% }

Scelte di design: 

- poiché `handle` non ha effetti collaterali, un comando non applicabile restituisce lo stato invariato invece di un errore.

Casi limite:

- `Play` senza traccia, `Pause` da fermo o `NextChunk` in pausa non modificano lo stato;
- un `SeekToIndex` fuori dall'intervallo viene portato al primo o all'ultimo chunk, e su una traccia assente non ha effetto.


### Store dello stato

`AppStateStore` è l'unico punto in cui lo stato viene letto e modificato. Viene creato una sola volta e la stessa istanza è condivisa da controller, player e view, che comunicano solo attraverso di esso. Le sue operazioni sono:

- `current`, che restituisce lo stato corrente;
- `dispatch(command)`, che applica il comando con `AppState.handle` in modo atomico e restituisce il nuovo stato;
- `changes`, un flusso che emette lo stato corrente alla sottoscrizione e poi a ogni modifica.

Dal punto di vista dei pattern, ogni azione è un valore (**Command**) che lo store applica, e i flussi pubblicati realizzano un **Observer**: gli interessati si iscrivono agli stessi flussi e vengono notificati dei cambiamenti senza conoscersi.

Scelte di design:

- lo store rappresenta l'unica fonte di verità. In questo modo tutti gli interessati vedono sempre lo stesso stato;
- ogni modifica è atomica, perciò i comandi inviati da view, controller e player in concorrenza non si perdono né si sovrascrivono, senza bisogno di altri meccanismi di sincronizzazione;
- chi osserva reagisce ai cambiamenti senza meccanismi di polling.

Casi limite:

- un osservatore lento può non ricevere gli stati intermedi, ma solo l'ultimo;
- lo stato non è salvato: a ogni avvio dell'applicazione si riparte da `AppState.initial`.

### Elaborazione audio

![Effetti e processori](doc/img/05-detailed-design/effetti-processore.png){ width=95% }

`AudioEffect` è un trait sealed con due sottotipi, che distinguono gli effetti in base a ciò che serve per calcolarli:

- `StatelessAudioEffect`, per gli effetti che dipendono solo dal chunk (`apply(chunk)`): identità, gain, distorsione, pan e volume;
- `TimeAudioEffect`, per quelli che dipendono anche dalla posizione nel brano (`apply(chunk, chunkIndex)`): eco e autopan.

Ogni effetto restituisce `Either[AudioError, AudioChunk]`: un parametro fuori dai limiti produce un errore come valore, e non un'eccezione. Le classi concrete sono interne al package e si ottengono dalle factory del companion (`AudioEffect.gain(...)`).

L'enumerazione `AudioEffectType` elenca gli effetti disponibili e associa a ciascuno la propria configurazione (`AudioConfiguration`, con `ParameterConfiguration` che indica minimo, massimo e valore predefinito di ogni parametro). In questo modo limiti e valori predefiniti sono definiti in un solo punto e usati sia dagli effetti sia dalla view.

Gli effetti calcolano, per ogni chunk, quanto segue (i campioni sono in [-1, 1)):

| Effetto | Parametri | Operazione |
|---------|-----------|------------|
| Identity | nessuno | restituisce il chunk invariato |
| Gain | fattore, [0, 2] | moltiplica ogni campione per il fattore e limita il risultato a [-1, 1] |
| Volume | livello, [0, 1] | moltiplica ogni campione per il livello |
| Distortion | intensità, [0, 10]; funzione `tanh` o `sin` | applica la funzione a `campione × intensità`. La view invia `tanh` o `sin` secondo la scelta dell'utente; il valore predefinito delle factory è `tanh` |
| Pan | posizione, [-1, 1] | porta il segnale in stereo (un chunk mono è duplicato) e moltiplica i due canali per coefficienti ricavati dall'angolo `posizione × 45°`: sinistro `√2/2 · (cos − sin)` e destro `√2/2 · (cos + sin)`, così la potenza resta costante |
| Autopan | frequenza, [0, 20] Hz; frequenza di campionamento | come il pan, ma la posizione oscilla in modo sinusoidale alla frequenza data; il tempo di ogni campione si ricava dall'indice del chunk |
| Echo | attenuazione, [0, 0.9]; ritardo, [10, 1000] ms; frequenza di campionamento | tiene un buffer circolare per canale lungo `ritardo × frequenza` campioni. Ogni campione in uscita è `x + attenuazione × campione ritardato`, limitato a [-1, 1] e riscritto nel buffer |

`AudioProcessor` applica una lista di effetti a un chunk o a una traccia (template method): verifica gli ingressi e delega a `processChunk` e `processTrack`. Ogni effetto temporale riceve l'indice del chunk. Esistono due varianti:

- `SimpleAudioProcessor` applica gli effetti nell'ordine in cui sono dati;
- `AudioProcessorWithRules` li ordina prima per priorità (identità, gain, distorsione, eco, pan, autopan, volume), così il risultato non dipende dall'ordine in cui l'utente li ha attivati. L'applicazione usa questa variante.

Scelte di design:

- per aggiungere un effetto si estende uno dei due sottotipi, lo si aggiunge ad `AudioEffectType` e gli si assegna una priorità, senza modifiche a player e controller;
- la gerarchia sealed fa verificare al compilatore che ogni effetto nuovo sia trattato come stateless o come temporale, ed evita di dover passare un indice a effetti che non ne hanno bisogno;
- l'ordine fisso ha una motivazione sonora, spiegata nei requisiti: il guadagno precede la distorsione, l'eco ne ripete il suono già distorto, pan e autopan spostano nello spazio anche le ripetizioni e il volume regola per ultimo il livello finale;
- quando un effetto fallisce, l'elaborazione si interrompe e l'errore, che riporta il nome dell'effetto, arriva al chiamante.

Casi limite:

- con lista di effetti vuota il chunk è restituito invariato;
- un chunk o una traccia vuoti sono rifiutati con un errore;
- un effetto sconosciuto (non presente in `AudioEffectType`) è accettato e applicato per ultimo;
- se un chunk elaborato diventa stereo, l'intera traccia lo diventa;
- l'eco è l'unico componente con stato mutabile: tiene un buffer circolare per canale, di dimensione pari al ritardo in campioni. Sostituire l'effetto (per esempio muovendo il cursore) crea un nuovo buffer, quindi la coda dell'eco precedente si interrompe.

### Motore di riproduzione

`AudioPlayer` non ha uno stato proprio: osserva lo store e agisce di conseguenza. Riceve alla creazione, un `AudioProcessor` che applica gli effetti a ogni chunk e un `AudioOutput` che lo porta in uscita, e conosce solo i loro contratti. Perciò `SimpleAudioProcessor` e `AudioProcessorWithRules`, cosi come `AudioOutput.console` e `AudioOutput.speakers`, sono intercambiabili senza modificare il player. Lo store gli viene fornito quando viene avviato, con `run(store)`.

![Dipendenze di AudioPlayer](doc/img/05-detailed-design/dipendenze-player.png){ width=95% }

`StreamingAudioPlayer` ascolta il flusso `changes` e reagisce solo ai cambi della coppia (`playbackState`, `chunkIndex`). Per ogni stato `Playing` prende il chunk in posizione `chunkIndex`, lo elabora con `AudioProcessor.apply` usando gli `effects` correnti, lo manda in output con `AudioOutput.write` e invia `NextChunk` allo store; il nuovo `chunkIndex` che ne risulta fa partire la riproduzione del chunk successivo.

![Flusso di una riproduzione](doc/img/05-detailed-design/flusso-riproduzione.png){ width=100% }

La riproduzione procede un chunk alla volta: il player non elabora la traccia in anticipo, ma prepara ogni chunk subito prima di mandarlo in uscita. Per questo gli effetti e la posizione possono cambiare mentre la traccia suona, e il risultato si sente dal chunk successivo. Il vincolo è che l'elaborazione di un chunk resti più breve della sua durata (circa 23 ms per 1024 frame a 44100 Hz), altrimenti l'audio si interrompe; la risposta a una modifica non è comunque istantanea, perché una piccola quantità di audio è già stata consegnata all'uscita.

`AudioOutput` ha un solo metodo, `write(chunk, sampleRate)`. `AudioOutput.console` stampa i metadati del chunk in console, mentre `AudioOutput.speakers` produce il suono attraverso la libreria Java Sound.

Scelte di design:

- `Pause`, `Stop` e `SeekToIndex` interrompono l'elaborazione del chunk in corso e fanno ripartire il ciclo dal nuovo stato;
- il ritmo della riproduzione deriva da `AudioOutput.write`, che termina solo quando l'output può accettare il chunk, e non da un timer. Il player resta quindi poco avanti rispetto a quanto si sente.

Casi limite:

- se `AudioProcessor.apply` restituisce un `Left` con un `AudioError`, l'errore viene stampato in console e si riproduce il chunk originale, poichè un effetto malfunzionante non deve rendere silente riproduzione;
- se l'output fallisce con un'eccezione, la riproduzione passa a `Stopped` e il player resta attivo, pronto a ripartire;
- alla fine della traccia il player stesso invia `Stop`, che riporta `chunkIndex` a zero.

### Lettura e scrittura dei file

I trait `AudioReader` e `AudioWriter` separano il formato dal resto dell'applicazione; `WavAudioReader` e `WavAudioWriter` ne sono le implementazioni per il WAV PCM a 16 bit little-endian, mono e stereo. Entrambi restituiscono un `Either` con un errore da un insieme chiuso: `AudioLoadError` (file non trovato, formato non supportato, dati corrotti, errore di lettura) e `AudioSaveError` (errore di scrittura, traccia non valida, campione non valido, file troppo grande, nessuna traccia).

Scelte di design:

- la lettura divide i campioni in chunk e li normalizza in [-1, 1) dividendo per 32768;
- la scrittura li quantizza saturando i valori fuori scala.

Casi limite:

- la scrittura rifiuta `NaN` e infiniti, una frequenza di campionamento oltre il limite del formato e una traccia che supera la dimensione massima del file;
- se manca l'estensione `.wav`, viene aggiunta; un file esistente è sovrascritto;
- un file di un formato diverso da WAV PCM a 16 bit è rifiutato con un errore, senza modificare lo stato.

## Controller

I controller espongono alla view le operazioni dell'utente e le traducono in comandi per lo store.

- `PlaybackController` (play, pause, stop, seek) non contiene logica di stato: invia il comando corrispondente. Il seek converte i secondi nell'indice del chunk che contiene quel punto, riportando i valori fuori traccia al primo o all'ultimo chunk.
- `EffectsController` invia `AddEffect` e `RemoveAll`.
- `LoadSaveController` è l'unico che gestisce operazioni lente. `load` e `save` ritornano subito e lavorano in background sotto un `Supervisor`, con la lettura, oppure l'elaborazione seguita dalla scrittura, eseguite in `IO.blocking`. In esportazione il controller legge dallo stato la traccia e gli effetti attivi, ne ottiene la versione elaborata con l'`AudioProcessor` e la passa all'`AudioWriter`.

![Sequenza dell'importazione](doc/img/05-detailed-design/sequenza-importazione.png){ width=100% }

Scelte di design per `LoadSaveController`:

- un salvataggio lavora su una copia dell'`AppState` presa alla richiesta: il file contiene la traccia e gli effetti presenti in quel momento, anche se nel frattempo ne viene caricata un'altra o gli effetti cambiano;
- se non ci sono effetti attivi l'elaborazione restituisce la traccia invariata, che viene scritta così com'è;
- gli errori restituiti da reader, processore e writer e le eccezioni impreviste finiscono tutti in `Failed`, quindi la view riceve sempre un esito;
- l'utente riporta lo stato a `Idle` con `acknowledgeImport` e `acknowledgeExport`, che hanno effetto solo se l'operazione è conclusa.

Casi limite: un salvataggio senza traccia caricata produce `Failed(NoTrackLoaded)`.

## View

La view non modifica lo stato: ascolta gli stream dello store e invia richieste ai controller. Ogni pannello è diviso in due parti: i widget, che descrivono solo l'aspetto, e una classe di logica, che li collega ai controller e li aggiorna in base allo stato.

| Pannello | Widget | Logica |
|--------------------|--------------------------------|--------------------------|
| Riproduzione | `TrackBox` | `TrackBoxLogic` |
| Effetti | `EffectsPanelContainer` | `EffectsLogic` |
| Importazione ed esportazione | `TitlePane`, `LoadSaveControls` | `LoadSaveObserver` |

La finestra è un `BorderPane` a tre zone:

- in alto `TitlePane`, con il titolo, un'etichetta di stato per importazione ed esportazione e i pulsanti Import ed Export;
- al centro `TrackBox`, con la timeline, le etichette del tempo iniziale e finale e i pulsanti Play, Pause e Stop;
- in basso `EffectsPanelContainer`, con tre riquadri e il pulsante che ripristina tutti gli effetti: `VolumeGainBox` (volume e gain), `DistortionPanBox` (distortion, con la scelta tra `tanh` e `sin`, e pan) ed `EchoAutopanBox` (attenuazione e ritardo dell'eco e frequenza dell'autopan).

Il comportamento di ogni pannello è nella sua logica:

- `TrackBoxLogic` collega i pulsanti e la timeline ai controller e aggiorna posizione ed etichette in base allo stato;
- `EffectsLogic` collega i cursori ai controller e invia l'effetto corrispondente;
- `LoadSaveObserver` si iscrive agli stati di importazione ed esportazione e aggiorna i controlli (`LoadSaveControls`), compresi i messaggi d'errore per l'utente, definiti in un unico punto (`ErrorMessages`).

## Aspetti trasversali

### Interfacce principali

I componenti si conoscono solo attraverso i contratti seguenti: chi li usa non dipende dalle realizzazioni.

| Interfaccia | Operazioni | Note |
|--------------------|----------------------------------|------------------|
| `AudioReader` | `load(filePath: String): Either[AudioLoadError, AudioTrack]` | operazione bloccante |
| `AudioWriter` | `save(track: AudioTrack, path: Path): Either[AudioSaveError, Unit]` | operazione bloccante |
| `AudioProcessor` | `apply(chunk: AudioChunk, effects: List[AudioEffect], chunkIndex: Int): Either[AudioError, AudioChunk]`; `apply(track: AudioTrack, effects: List[AudioEffect]): Either[AudioError, AudioTrack]` | con lista di effetti vuota restituisce l'ingresso invariato |
| `AudioOutput` | `write(chunk: AudioChunk, sampleRate: Int): IO[Unit]` | la scrittura termina quando l'uscita ha accettato il chunk |
| `AudioPlayer` | `run(store: AppStateStore): Stream[IO, Unit]` | il flusso restituito, una volta avviato, osserva lo store e riproduce |
| `AppStateStore` | `current: IO[AppState]`; `dispatch(command: Command): IO[AppState]`; `changes: Stream[IO, AppState]`; `playbackChanges`, `trackChanges`, `importStateChanges`, `exportStateChanges` | i flussi derivati emettono solo quando la parte di stato cambia davvero |
| `PlaybackController` | `play`, `pause`, `stop`: `IO[Unit]`; `seek(positionSeconds: Double): IO[Unit]` | il posizionamento è in secondi |
| `EffectsController` | `addEffect(effect: AudioEffect): IO[Unit]`; `removeAll(): IO[Unit]` | |
| `LoadSaveController` | `load(path: Path): Unit`; `save(path: Path): Unit`; `acknowledgeImport: Unit`; `acknowledgeExport: Unit` | lavorano in background e ritornano subito |

### Concorrenza e thread

L'applicazione usa tre contesti di esecuzione, che comunicano soltanto attraverso `AppStateStore`:

- il **thread JavaFX** esegue la view: i gestori dei controlli (`TrackBoxLogic`, `EffectsLogic`) partono da qui, e gli aggiornamenti dei widget vi devono tornare con `Platform.runLater`;
- le **fibre di Cats Effect**, sul pool di calcolo, eseguono i metodi in `PlaybackController`, `EffectsController` e `LoadSaveController`, il ciclo di `StreamingAudioPlayer` e gli osservatori della view (`TrackBoxLogic.observe`, `LoadSaveObserver.observe`), che si iscrivono ai flussi dello store;
- il **pool dei compiti bloccanti** esegue le operazioni che attendono il sistema, con `IO.blocking`: `WavAudioReader` e `WavAudioWriter` per i file e `SpeakerAudioOutput` per la scrittura sulla linea audio.

![Contesti di esecuzione e passaggi tra di essi](doc/img/05-detailed-design/contesti-esecuzione.png){ width=80% }

Ne derivano tre regole:

- la view avvia i controller con `unsafeRunAndForget`, senza attenderne il risultato, così l'interfaccia non si blocca durante le operazioni lunghe;
- le operazioni bloccanti non occupano mai il pool di calcolo, quindi il player e gli altri flussi continuano a girare;
- lo stato condiviso è uno solo, `AppStateStore`, che serializza le modifiche in modo atomico.

### Composizione dell'applicazione

`AppSetup.make` costruisce il grafo degli oggetti in una `Resource`: store, controller, logiche della view, avvio del player in background e osservatori. Al termine, la `Resource` rilascia tutto in ordine. `Main` è un `def main` ordinario: alloca le dipendenze, lancia l'applicazione ScalaFX e rilascia le risorse alla chiusura.

\newpage
