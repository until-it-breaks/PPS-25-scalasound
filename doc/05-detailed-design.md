# Design di Dettaglio

## Organizzazione dei package

All'interno del package radice `scalasound`, il codice si articola nella seguente struttura:

- **`model`**:
  - `model.domain`: le classi di dominio `AudioChunk` e `AudioTrack`.
  - `model.state`: lo stato globale immutabile, i comandi che lo modificano, e lo store reattivo.
  - `model.io`: lettura e scrittura dei file audio, con le implementazioni per il formato WAV e i relativi errori.
  - `model.player`: il motore di riproduzione e l'output audio.
  - `model.processing`: gli effetti, i processori audio e i relativi errori.
- **`controller`**: i tre controller di riproduzione, importazione/esportazione ed effetti.
- **`view`**:
  - `view.panels`: i pannelli grafici (riquadro della traccia, pannello degli effetti e pannello di importazione ed esportazione), ciascuno con la relativa logica di presentazione e il collegamento ai controller.
  - `ScalaFxApp` e `AppDependencies`: costruzione della finestra principale e dipendenze di cui la view ha bisogno.
- `AppSetup` (composizione dei componenti) e `Main` (avvio dell'applicazione).

## Model

### Dominio

Il dominio è stato progettato dall'intero gruppo ed è evoluto nel tempo. L'audio è rappresentato da due gerarchie, che rendono impossibili gli stati non validi. I tipi sono definiti nel file `AudioADT` (package `model.domain`).

- `AudioChunk` è un'enumerazione con due casi: `MonoChunk`, con un solo vettore di campioni, e `StereoChunk`, con i canali sinistro e destro separati. È l'unità alla base dell'elaborazione, della riproduzione e delle operazioni di importazione ed esportazione.
- `AudioTrack` è il trait che rappresenta una traccia: espone la frequenza di campionamento e la sequenza ordinata di chunk che lo compone. Ha due implementazioni: `MonoTrack`, composta solo da `MonoChunk`, e `StereoTrack`, composta solo da `StereoChunk` in cui i canali sinistro e destro hanno la stessa lunghezza. In entrambe la frequenza di campionamento deve essere positiva.

Sia `AudioChunk` sia `AudioTrack` espongono `frameCount`, il numero di frame contenuti. Un frame comprende i campioni di tutti i canali relativi allo stesso istante: un campione in una traccia mono, due (sinistro e destro) in una stereo. Ad esempio, un chunk stereo con 1024 campioni per canale contiene 1024 frame, cioè 2048 campioni in totale.

\newpage

![Gerarchia di tracce e chunk](doc/img/05-detailed-design/dominio.png){ width=80% }

Scelte di design:

- la traccia è composta da una sequenza di chunk di dimensione fissa (1024 frame quando importata). Questo permette di elaborare e riprodurre il flusso audio un blocco alla volta, solo quando necessario;
- i campioni sono `Float` normalizzati in [-1, 1): tutti gli effetti lavorano su un'unica scala, indipendentemente dalla risoluzione in bit del file di provenienza, e il calcolo in virgola mobile non ha problemi di overflow con gli interi a 16 bit (un guadagno potrebbe superare il valore massimo). Il `Float` inoltre occupa metà della memoria rispetto al `Double`;
- `chunks` e i campioni (`samples`, `left`, `right`) sono contenuti in `Vector`, la sequenza immutabile di Scala con accesso per indice efficiente, che facilita l'operazione di seek. In questo modo spostare la riproduzione in un punto qualsiasi della traccia non richiede di percorrere i chunk precedenti, come invece accadrebbe con una `List`.

Casi limite:

- una traccia con frequenza di campionamento non positiva, o una traccia stereo con canali di lunghezza diversa, viene rifiutata alla costruzione;
- l'ultimo chunk di una traccia può contenere meno frame degli altri.

\newpage

### Stato e comandi

Lo stato dell'applicazione è contenuto nella `case class` immutabile `AppState`, composta da:

| Campo | Tipo | Significato | Valore iniziale |
|----------------|------------------------|--------------------------------------------|----------------|
| `playbackState` | `PlaybackState` | fase della riproduzione: `Stopped`, `Playing` o `Paused` | `Stopped` |
| `track` | `Option[AudioTrack]` | traccia caricata, se presente: è quella di riferimento per elaborazione, riproduzione ed eventuale esportazione | `None` |
| `chunkIndex` | `Int` | indice del chunk da riprodurre | `0` |
| `effects` | `List[AudioEffect]` | effetti attivi, al più uno per tipo | `List.empty` |
| `importState` | `ImportState` | ciclo dell'importazione: `Idle`, `Importing`, `Imported(track)` o `Failed(error)` | `Idle` |
| `exportState` | `ExportState` | ciclo dell'esportazione: `Idle`, `Exporting`, `Exported` o `Failed(error)` | `Idle` |

Lo stato iniziale ha i valori indicati nell'ultima colonna: nessuna traccia né effetto, riproduzione ferma e importazione ed esportazione a riposo. Lo stato non viene mai modificato: la funzione `AppState.handle` riceve un comando (`Command`) e restituisce una nuova versione di `AppState`, lasciando invariata quella precedente.

I comandi disponibili e il loro effetto sui campi di `AppState` sono:

- `Play`: imposta `playbackState` a `Playing`, ma solo se `track` contiene una traccia;
- `Pause`: imposta `playbackState` a `Paused`, ma solo se la riproduzione è in corso;
- `Stop`: imposta `playbackState` a `Stopped` e riporta `chunkIndex` a zero, in qualunque stato;
- `NextChunk`: incrementa `chunkIndex` di uno, ma solo durante la riproduzione;
- `SeekToIndex(index)`: imposta `chunkIndex` a `index`, riportato al primo o all'ultimo chunk se esce dall'intervallo valido; non cambia `playbackState` e, senza traccia, non ha effetto;
- `AddEffect(effect)`: inserisce `effect` in `effects` al posto di quello dello stesso tipo, quindi nella lista c'è al massimo un effetto per tipo;
- `RemoveAllEffects`: svuota `effects`;
- `UpdateImport(state)`: imposta `importState` a `state`; se `state` è `Imported(track)`, sostituisce anche `track` con la nuova traccia, imposta `playbackState` a `Stopped` e `chunkIndex` a zero, lasciando invariati gli `effects`;
- `UpdateExport(state)`: imposta `exportState` a `state`, senza toccare gli altri campi.

\newpage

La riproduzione è possibile solo se è stata caricata una traccia e passa attraverso tre fasi, `Stopped`, `Playing` e `Paused`, secondo le transizioni mostrate nel diagramma.

![Stati della riproduzione audio](doc/img/05-detailed-design/stati-riproduzione.png){ width=70% }

Le operazioni di importazione ed esportazione procedono per fasi, registrate nello stato (`importState` ed `exportState`) e lette dalla View per mostrare l'avanzamento all'utente. Seguono lo stesso ciclo, rappresentato da semplici valori dello stato: `Idle`, operazione in corso, esito (riuscita o fallita) e ritorno a `Idle` quando l'esito viene confermato.

![Stati di importazione ed esportazione](doc/img/05-detailed-design/stati-import-export.png){ width=75% }

Scelte di design:

- la posizione nella traccia è rappresentata principalmente dall'indice del chunk da riprodurre (`chunkIndex`) e non da un tempo in secondi, che si ricava dall'indice solo quando serve; le richieste espresse in secondi sono convertite in indice dal livello dei controller. Inoltre, se la dimensione dei chunk è ben configurata, l'indice offre una risoluzione molto più fine del secondo: con chunk di 1024 frame a 44100 Hz, ad esempio, ogni chunk dura circa 23 ms;
- l'esito di un'importazione o di un'esportazione, riuscito o fallito, resta nello stato finché non viene riconosciuto e solo allora si torna a `Idle`. In questo modo si forza il riconoscimento dell'esito;
- gli effetti sono configurati dall'utente e non sono legati alla traccia. Per questo motivo l'importazione di un nuovo file li lascia invariati;
- l'importazione di una nuova traccia ferma la riproduzione e riporta `chunkIndex` a zero, così il nuovo file non parte da un indice che aveva senso solo per la traccia precedente;
- un comando che non si può applicare allo stato corrente non produce un errore: `handle` restituisce lo stato invariato.

Casi limite:

- `Play` durante la riproduzione non modifica lo stato, e `Stop` da fermo azzera soltanto `chunkIndex` (utile dopo un seek). Entrambi si possono inviare più volte senza conseguenze;
- un `SeekToIndex` fuori dall'intervallo viene portato al primo o all'ultimo chunk. Se la traccia è assente non ha effetto.

### Store dello stato

`AppStateStore` è l'unico punto in cui `AppState` viene letto e modificato. Viene creato una sola volta alla composizione dell'applicazione e la stessa istanza è condivisa da `AudioPlayer`, Controller e View, che comunicano solo attraverso di esso. Internamente conserva lo stato in un `SignallingRef` di FS2. Un `Ref` di Cats Effect è un riferimento modificabile in modo atomico, sicuro da usare da più fibre senza lock espliciti né thread bloccati. Il `SignallingRef` lo estende con la capacità di segnalare ogni aggiornamento attraverso uno `Stream` di FS2, che lo store espone come `changes`. Le sue operazioni sono:

- `current`, che restituisce l'`AppState` corrente;
- `dispatch(command)`, che applica il comando con `AppState.handle` e restituisce il `AppState`;
- `changes`, uno `Stream` che emette lo stato corrente alla sottoscrizione e poi a ogni modifica;
- flussi derivati da `changes`, basati su componenti parziali dell'`AppState`, che emettono solo quando tale porzione cambia.

Dal punto di vista dei pattern, ogni azione è un **Command** che lo store applica, e i flussi pubblicati realizzano un **Observer**: gli interessati si iscrivono agli stessi flussi e vengono notificati dei cambiamenti senza conoscersi. I flussi derivati permettono a ciascuno di ascoltare solo ciò che gli serve.

Scelte di design:

- lo store rappresenta l'unica fonte di verità dello stato dell'applicazione;
- `AppState` è immutabile per sua natura, ma il riferimento che lo condivide non lo è: lo stato corrente è un valore che viene sostituito a ogni modifica. Se più componenti leggessero lo stato, calcolassero il successivo e lo riscrivessero in quel riferimento, una modifica potrebbe sovrascrivere l'altra (*lost update*). Per questo la concorrenza è gestita solo dallo store, che applica ogni comando in modo atomico all'ultimo stato disponibile.
- chi osserva reagisce ai cambiamenti senza meccanismi di polling.

Casi limite:

- un osservatore lento può non ricevere gli stati intermedi, ma solo l'ultimo;
- l'`AppState` non è salvato su disco. A ogni avvio dell'applicazione si riparte da `AppState.initial`.

### Elaborazione audio

![Effetti e processori](doc/img/05-detailed-design/effetti-processore.png){ width=95% }

`AudioEffect` rappresenta una trasformazione che, dato un `AudioChunk`, ne restituisce un altro. È un trait sealed con due sottotipi, che distinguono gli effetti in base a ciò che serve per calcolarli:

- `StatelessAudioEffect`, per gli effetti che dipendono solo dal chunk stesso (`apply(chunk)`): Identity, Gain, Distortion, Pan e Volume;
- `TimeAudioEffect`, per quelli che dipendono anche dalla posizione nella traccia (`apply(chunk, chunkIndex)`): Echo e Autopan.

Ogni effetto restituisce `Either[AudioError, AudioChunk]`: un parametro fuori dai limiti produce un errore come valore, e non un'eccezione.

L'enumerazione `AudioEffectType` elenca gli effetti disponibili e associa a ciascuno la propria configurazione (`AudioConfiguration`, con `ParameterConfiguration` che indica minimo, massimo e valore predefinito di ogni parametro). In questo modo limiti e valori predefiniti sono definiti in un solo punto e usati sia dagli effetti stessi che dalla View.

Parametri, intervalli e comportamento di ciascun effetto sono descritti nei requisiti funzionali.

`AudioProcessor` applica una lista di `AudioEffect` ad un `AudioChunk` o ad una `AudioTrack`. Ne esistono due varianti:

- `SimpleAudioProcessor` applica gli effetti nell'ordine in cui sono dati;
- `AudioProcessorWithRules` li ordina prima per priorità (Identity, Gain, Distortion, Echo, Pan, Autopan, Volume), in tal modo il risultato non dipende dall'ordine in cui l'utente li ha attivati. L'applicazione usa questa variante.

Chi lo usa sceglie una delle due varianti e chiama `apply`, che esiste con due signature: `apply(chunk, effects, chunkIndex)`, usata per la riproduzione in tempo reale a blocchi, e `apply(track, effects)`, usata per l'esportazione. Entrambe restituiscono un `Either` con un `AudioError` oppure con l'`AudioChunk` o l'`AudioTrack` elaborati. L'indice serve agli effetti temporali; nella signature per traccia è il processore a ricavarlo, scorrendo i chunk in sequenza. Ciascuna verifica gli input e poi delega a `processChunk` o a `processTrack`, che le varianti possono ridefinire. Ad esempio, `AudioProcessorWithRules` li ridefinisce per ordinare gli effetti in input.

Scelte di design:

- per implementare un nuovo effetto si estende uno dei due sottotipi (`StatelessAudioEffect` o `TimeAudioEffect`), lo si registra in `AudioEffectType` e gli si assegna una priorità.
- l'ordine fisso ha una motivazione sonora, spiegata nei requisiti: il guadagno precede la distorsione perché più il segnale in ingresso è forte, più la distorsione lo satura; l'eco ripete il suono già distorto; pan e autopan spostano nello spazio stereofonico anche le ripetizioni mentre il volume regola per ultimo il livello finale;
- quando l'applicazione di un effetto fallisce, l'elaborazione si interrompe e l'errore, che riporta il nome dell'effetto, arriva al chiamante.

Casi limite:

- con lista di effetti vuota il chunk è restituito invariato;
- un chunk o una traccia vuoti sono rifiutati con un errore;
- un effetto sconosciuto (non presente in `AudioEffectType`) è accettato e applicato per ultimo;
- Pan e Autopan sono gli unici effetti che trasformano un chunk mono in stereo. Quando una traccia è elaborata per intero, basta che un chunk diventi stereo perché lo diventi l'intera traccia;
- Echo è l'unico effetto con stato mutabile. Tale effetto utilizza un buffer circolare per canale, di dimensione pari al ritardo in campioni. Sostituire l'effetto (per esempio modificandone un parametro) crea un nuovo buffer.

### Motore di riproduzione

`AudioPlayer` è il contratto del motore di riproduzione: ha il solo metodo `run(store)`, che restituisce uno `Stream` che descrive il ciclo di vita del player, cioè l'ascolto dell'`AppState` e la riproduzione dei chunk. La sua implementazione, `StreamingAudioPlayer`, non ha uno stato proprio poichè osserva l'`AppStateStore` e agisce di conseguenza, elaborando ogni chunk subito prima di suonarlo. Riceve alla creazione un `AudioProcessor` che applica gli effetti a ogni chunk e un `AudioOutput` che lo porta in uscita. Lo store gli viene fornito solo quando viene avviato, con `run(store)`.

![Dipendenze del motore di riproduzione](doc/img/05-detailed-design/dipendenze-player.png){ width=95% }

Anche il trait `AudioOutput` ha un solo metodo, `write(chunk, sampleRate)`. Nell'applicazione è usata l'implementazione `SpeakerAudioOutput`, che riproduce il suono attraverso la libreria Java Sound, effettuando la conversione in byte PCM e scrivendo i campioni su una linea audio con un buffer di circa 200 ms. La scrittura si blocca solo quando il buffer è pieno, finché la riproduzione non libera spazio. È disponibile anche `AudioOutput.console`, che stampa i metadati dei chunk in console. Di per sé non è ritmata, perché `write` termina subito, ma la si può rendere tale facendola attendere la durata di ogni chunk.

`StreamingAudioPlayer` elabora i chunk seguendo l'approccio just-in-time: non processa la traccia in anticipo, ma prepara ogni chunk subito prima di mandarlo in uscita. Una modifica degli effetti o un riposizionamento si applica quindi già dal chunk successivo. Supponendo che come `AudioOutput` sia usato `SpeakerAudioOutput`, il ritmo non deriva da un timer ma dall'output stesso, che accetta un nuovo chunk solo quando ha spazio nel buffer.

Nello specifico, `StreamingAudioPlayer` ascolta il flusso `changes` e reagisce solo ai cambiamenti di `playbackState` e di `chunkIndex` in `AppState`. Quando `playbackState` diventa `Playing`, esso prende il chunk in posizione `chunkIndex`, lo elabora con `AudioProcessor` usando gli `effects` correnti, lo manda in output attraverso `AudioOutput` e invia `NextChunk` allo store. Il ciclo riparte ogni volta che `chunkIndex` è diverso dal precedente, per effetto di `NextChunk` o di `SeekToIndex`, e finisce quando `playbackState` non è più `Playing` o quando la traccia è arrivata all'ultimo chunk, nel qual caso il player stesso invia `Stop` allo store.

![Stati di `StreamingAudioPlayer`](doc/img/05-detailed-design/stati-player.png){ width=90% }

Il diagramma di sequenza seguente mostra la riproduzione di una traccia dall'invio di `Play` allo store fino all'ultimo chunk, quando il player invia `Stop`.

![Flusso di una riproduzione](doc/img/05-detailed-design/flusso-riproduzione.png){ width=100% }

Scelte di design:

- l'`AudioPlayer` è modellato come un flusso (`Stream` di FS2) perché la riproduzione è una sequenza di chunk nel tempo descritta dal ciclo "elabora, riproduci, avanza" invece di gestire a mano indici e attese.
- l'elaborazione è just-in-time: una modifica degli effetti o un riposizionamento si sente già dal chunk successivo. In cambio l'elaborazione di un chunk deve restare più breve della sua durata. `AudioPlayer` è un trait e `StreamingAudioPlayer` ne è una sola implementazione, quindi in futuro si può aggiungere una soluzione con buffer, che elabora in anticipo alcuni chunk per assorbire i momenti in cui l'elaborazione è più lenta della riproduzione ed evitare le interruzioni.

Casi limite:

- se l'elaborazione di un chunk è più lenta della sua durata (circa 23 ms per 1024 frame a 44100 Hz), il buffer dell'output si svuota e la riproduzione procede a scatti;
- se invece la CPU è abbastanza potente, il buffer è quasi sempre pieno e una modifica non si sente subito, perché il buffer non viene svuotato. Dopo una modifica degli effetti ma anche impartendo i comandi `Pause` o `SeekToIndex`, in uscita si sentono comunque al massimo circa 200 ms di audio, cioè circa nove chunk da 1024 frame a 44100 Hz, già elaborati con le impostazioni precedenti;
- se `AudioProcessor` restituisce un `AudioError`, l'errore viene stampato in console e si riproduce il chunk originale, poiché un effetto malfunzionante non deve interrompere la riproduzione;
- se la riproduzione di un chunk fallisce con un'eccezione (per esempio nell'`AudioOutput`), l'errore viene stampato in console, la riproduzione passa a `Stopped` con `chunkIndex` a zero e il player resta attivo, pronto a ripartire.

### Lettura e scrittura dei file

I trait `AudioReader` e `AudioWriter` definiscono come si legge e si scrive una traccia, senza dipendere dal formato del file: `load(filePath)` restituisce una traccia e `save(track, path)` ne scrive una su file. `WavAudioReader` e `WavAudioWriter` ne sono le implementazioni per il WAV PCM a 16 bit little-endian, mono e stereo. Le operazioni sono bloccanti ed entrambe restituiscono un `Either` con un errore da un insieme chiuso: `AudioLoadError` (file non trovato, formato non supportato, dati corrotti, errore di lettura) e `AudioSaveError` (errore di scrittura, traccia non valida, campione non valido, file troppo grande, nessuna traccia).

![Reader e writer](doc/img/05-detailed-design/classi-io.png){ width=80% }

Il WAV è un formato non compresso: il file contiene un'intestazione con la descrizione del formato e poi i campioni, quindi si leggono e si scrivono senza codifica o decodifica. Il sottoinsieme supportato è il PCM con segno a 16 bit, con i campioni in ordine little-endian, a uno (mono) o due (stereo) canali, con i canali intercalati frame per frame. Sono rifiutati con `UnsupportedFormat` le altre codifiche (compresse, a virgola mobile, a 8 o 24 bit), i campioni big-endian e i file con più di due canali.

Il diagramma seguente mostra le operazioni eseguite da `WavAudioReader` e `WavAudioWriter`. Entrambe i flussi procedono per verifiche successive, ciascuna delle quali può terminare con un errore: solo se tutte sono completate con successo si ottiene la traccia o il file.

![Passi di lettura e scrittura](doc/img/05-detailed-design/lettura-scrittura.png){ width=65% }

Scelte di design:

- gli errori sono valori e non eccezioni: l'esito è un dato che chi chiama può esaminare e gestire senza terminare l'applicazione;
- si supporta un solo sottoinsieme del WAV e il formato resta isolato nei due trait. Se si intende supportarne altri basta creare implementazioni, senza modificare il resto del sistema.
- le implementazioni WAV usano la Java Sound API; per formati popolari come MP3 servirebbe un'altra libreria;
- lettura e scrittura sono una l'inversa dell'altra: la lettura normalizza i campioni in [-1, 1) dividendo per 32768, la scrittura li quantizza saturando i valori fuori scala, ad esempio quelli prodotti dagli effetti. L'intervallo è asimmetrico, quindi +1,0 viene limitato a 32767;
- la lettura divide i campioni in chunk di 1024 frame, l'ultimo contiene i frame rimanenti e senza riempimento;
- la scrittura esegue tutte le verifiche prima di scrivere un byte, quindi una traccia non valida non crea né modifica il file.

Casi limite:

- un file WAV valido ma senza frame è accettato e produce una traccia senza chunk, di durata zero;
- un file di un formato diverso da WAV PCM a 16 bit è rifiutato con un errore; dati incoerenti, cioè una lunghezza non multipla della dimensione del frame o frame dichiarati diversi da quelli presenti, risultano nell'errore `CorruptedData`;
- la scrittura rifiuta campioni con valori `NaN` o infiniti, una frequenza di campionamento oltre il limite del formato, una traccia che supera la dimensione massima del file e una traccia che non è né mono né stereo;
- se il file di destinazione esiste, viene sovrascritto.

## Controller

I controller espongono alla view le operazioni dell'utente e le traducono in comandi per lo store.

- `PlaybackController` (play, pause, stop, seek) non contiene logica di stato: invia il comando corrispondente. Il seek converte i secondi nell'indice del chunk che contiene quel punto, riportando i valori fuori traccia al primo o all'ultimo chunk.
- `EffectsController` invia `AddEffect` e `RemoveAllEffects`.
- `LoadSaveController` è l'unico che gestisce operazioni lente. `load` e `save` restituiscono un `IO` che avvia l'operazione in background sotto un `Supervisor` e termina subito; la lettura, oppure l'elaborazione seguita dalla scrittura, è eseguita in `IO.blocking`. In esportazione il controller legge dallo stato la traccia e gli effetti attivi, ne ottiene la versione elaborata con l'`AudioProcessor` e la passa all'`AudioWriter`.

![Sequenza dell'importazione](doc/img/05-detailed-design/sequenza-importazione.png){ width=100% }

L'esportazione segue lo stesso schema con tre differenze: legge dallo store la traccia e gli effetti attivi al momento della richiesta, fa elaborare la traccia dall'`AudioProcessor` prima di passarla all'`AudioWriter`, e non parte se un'importazione è in corso.

Scelte di design per `LoadSaveController`:

- un salvataggio lavora su una copia dell'`AppState` presa alla richiesta: il file contiene la traccia e gli effetti presenti in quel momento, anche se nel frattempo ne viene caricata un'altra o gli effetti cambiano;
- se non ci sono effetti attivi l'elaborazione restituisce la traccia invariata, che viene scritta così com'è;
- gli errori restituiti da reader, processore e writer e le eccezioni impreviste finiscono tutti in `Failed`, quindi la view riceve sempre un esito;
- una richiesta di importazione o di esportazione è ignorata se un'operazione dello stesso tipo è già in corso, e un salvataggio è ignorato anche durante un'importazione; il controllo si basa su un `Ref` per ciascun tipo di operazione;
- l'utente riporta lo stato a `Idle` con `acknowledgeImport` e `acknowledgeExport`, che hanno effetto solo se l'operazione è conclusa.

Casi limite:

- un salvataggio senza traccia caricata produce `Failed(NoTrackLoaded)`;
- se manca l'estensione `.wav`, viene aggiunta; un file esistente è sovrascritto.

## View

La view non modifica lo stato: ascolta gli stream dello store e invia richieste ai controller. Ogni pannello è diviso in due parti: i widget, che descrivono solo l'aspetto, e una classe di logica, che li collega ai controller e li aggiorna in base allo stato.

| Pannello | Widget | Logica |
|--------------------|--------------------------------|--------------------------|
| Riproduzione | `TrackBox` | `TrackBoxLogic` |
| Effetti | `EffectsPanelContainer` | `EffectsLogic` |
| Importazione ed esportazione | `TitlePane` | `LoadSaveLogic` |

La finestra è un `BorderPane` a tre zone:

- in alto `TitlePane`, con il titolo, un'etichetta di stato per importazione ed esportazione e i pulsanti Import ed Export;
- al centro `TrackBox`, con la timeline, le etichette del tempo iniziale e finale e i pulsanti Play, Pause e Stop;
- in basso `EffectsPanelContainer`, con tre riquadri e il pulsante che ripristina tutti gli effetti: `VolumeGainBox` (volume e gain), `DistortionPanBox` (distortion, con la scelta tra `tanh` e `sin`, e pan) ed `EchoAutopanBox` (attenuazione e ritardo dell'eco e frequenza dell'autopan).

Il comportamento di ogni pannello è nella sua logica:

- `TrackBoxLogic` collega i pulsanti e la timeline ai controller e aggiorna posizione ed etichette in base allo stato;
- `EffectsLogic` collega i cursori ai controller, attraverso `EffectsControllerWrapper`, e invia l'effetto corrispondente;
- `LoadSaveLogic` collega i pulsanti Import ed Export di `TitlePane` ai controller, con i selettori di file, e si iscrive agli stati di importazione ed esportazione: aggiorna l'etichetta e i pulsanti di `TitlePane` e mostra un avviso di riuscita o di errore, con i messaggi definiti in un unico punto (`ErrorMessages`); dopo l'avviso conferma l'esito al controller con `acknowledgeImport` o `acknowledgeExport`.

Scelte di design:

- `EffectsControllerWrapper` nasconde l'esecuzione dell'`IO` dei controller degli effetti (`unsafeRunAndForget`), così `EffectsLogic` chiama semplici metodi;
- `LoadSaveLogic` riceve per iniezione le funzioni che eseguono gli aggiornamenti sul thread JavaFX (`runOnGUIThread`) e mostrano gli avvisi (`showAlert`): così la sua logica si può verificare senza avviare l'interfaccia;
- i widget non conoscono i controller né lo store: tutto il collegamento è nelle classi di logica.

Casi limite:

- `Echo` e `Autopan` sono creati dalla view senza la frequenza di campionamento della traccia, quindi usano il valore predefinito delle factory (44100 Hz);
- se l'utente annulla un selettore di file non viene inviata nessuna richiesta.

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
| `LoadSaveController` | `load(path: Path)`, `save(path: Path)`, `acknowledgeImport`, `acknowledgeExport`: `IO[Unit]` | `load` e `save` avviano l'operazione in background e terminano subito |

### Concorrenza e thread

L'applicazione usa tre contesti di esecuzione, che comunicano soltanto attraverso `AppStateStore`:

- il **thread JavaFX** esegue la view: i gestori dei controlli (`TrackBoxLogic`, `EffectsLogic`, `LoadSaveLogic`) partono da qui, e gli aggiornamenti dei widget vi devono tornare con `Platform.runLater`;
- le **fibre di Cats Effect**, sul pool di calcolo, eseguono i metodi in `PlaybackController`, `EffectsController` e `LoadSaveController`, il ciclo di `StreamingAudioPlayer` e gli osservatori della view (`TrackBoxLogic.observe`, `LoadSaveLogic.observe`), che si iscrivono ai flussi dello store;
- il **pool dei compiti bloccanti** esegue le operazioni che attendono il sistema, con `IO.blocking`: `WavAudioReader` e `WavAudioWriter` per i file e `SpeakerAudioOutput` per la scrittura sulla linea audio.

![Contesti di esecuzione e passaggi tra di essi](doc/img/05-detailed-design/contesti-esecuzione.png){ width=80% }

Ne derivano tre regole:

- la view avvia i controller con `unsafeRunAndForget` (direttamente nelle classi di logica, oppure attraverso `EffectsControllerWrapper`), senza attenderne il risultato, così l'interfaccia non si blocca durante le operazioni lunghe;
- le operazioni bloccanti non occupano mai il pool di calcolo, quindi il player e gli altri flussi continuano a girare;
- lo stato condiviso è uno solo, `AppStateStore`, che serializza le modifiche in modo atomico.

### Composizione dell'applicazione

`AppSetup.make` costruisce il grafo degli oggetti in una `Resource`: store, controller, logiche della view, avvio del player in background e osservatori. Al termine, la `Resource` rilascia tutto in ordine. `Main` è un `def main` ordinario: alloca le dipendenze, lancia l'applicazione ScalaFX e rilascia le risorse alla chiusura.

\newpage
