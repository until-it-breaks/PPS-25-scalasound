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
  - `view.panels`: i pannelli visuali che compongono l'interfaccia grafica, ciascuno con la relativa logica di presentazione.
  - `ScalaFxApp` e `AppDependencies`: costruzione della finestra principale e dipendenze di cui la View ha bisogno.
- `AppSetup` (composizione dei componenti) e `Main` (avvio dell'applicazione).

## Model

### Dominio

Il dominio è stato progettato dall'intero gruppo ed è evoluto nel tempo. L'audio è rappresentato da due gerarchie, che rendono impossibili gli stati non validi. I tipi sono definiti nel file `AudioADT` (package `model.domain`).

- `AudioChunk` è un'enumerazione con due casi: `MonoChunk`, con un solo vettore di campioni, e `StereoChunk`, con i canali sinistro e destro separati. È l'unità alla base dell'elaborazione, della riproduzione e delle operazioni di importazione ed esportazione.
- `AudioTrack` è il trait che rappresenta una traccia: espone la frequenza di campionamento e la sequenza ordinata di chunk che lo compone. Ha due implementazioni: `MonoTrack`, composta solo da `MonoChunk`, e `StereoTrack`, composta solo da `StereoChunk` in cui i canali sinistro e destro hanno la stessa lunghezza. In entrambe la frequenza di campionamento deve essere positiva.

Sia `AudioChunk` sia `AudioTrack` espongono il numero di frame contenuti. Un frame comprende i campioni di tutti i canali relativi allo stesso istante. Ad esempio, un chunk stereo con 1024 campioni per canale contiene 1024 frame.

\newpage

![Gerarchia di tracce e chunk](doc/img/05-detailed-design/dominio.png){ width=80% }

Scelte di design:

- la traccia è composta da una sequenza di chunk di dimensione fissa (1024 frame quando importata). Questo permette di elaborare e riprodurre il flusso audio un blocco alla volta, solo quando necessario;
- i campioni sono `Float` normalizzati in [-1, 1): tutti gli effetti lavorano su un'unica scala, indipendentemente dalla risoluzione in bit del file di provenienza, e il calcolo in virgola mobile non ha problemi di overflow con gli interi a 16 bit (un guadagno potrebbe superare il valore massimo). Il `Float` inoltre occupa metà della memoria rispetto al `Double`;
- `chunks` e i campioni (`samples`, `left`, `right`) sono contenuti in `Vector`, la sequenza immutabile di Scala con accesso per indice efficiente: il player raggiunge così il chunk da riprodurre senza percorrere quelli precedenti, come invece accadrebbe con una `List`.

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

Lo stato iniziale ha i valori indicati nell'ultima colonna: nessuna traccia né effetto, riproduzione ferma e importazione ed esportazione a riposo. Lo stato non viene mai modificato poiché il metodo `AppState.handle` riceve un comando (`Command`) e restituisce una nuova versione di `AppState`, lasciando invariata quella precedente.

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

Le operazioni di importazione ed esportazione procedono per fasi, registrate nello stato (`importState` ed `exportState`). Seguono lo stesso ciclo, rappresentato da: `Idle`, operazione in corso, esito (riuscita o fallita) e ritorno a `Idle` quando l'esito viene confermato.

![Stati di importazione ed esportazione](doc/img/05-detailed-design/stati-import-export.png){ width=75% }

Scelte di design:

- la posizione nella traccia è rappresentata dall'indice del chunk da riprodurre (`chunkIndex`) e non da un valore temporale (per esempio i secondi dall'inizio), che si ricava dall'indice solo quando serve;
- l'esito di un'importazione o di un'esportazione, riuscito o fallito, resta nello stato finché non viene riconosciuto e solo allora si torna a `Idle`. In questo modo si forza il riconoscimento dell'esito;
- gli effetti sono configurati dall'utente e non sono legati alla traccia. Per questo motivo l'importazione di una nuova traccia li lascia invariati;
- l'importazione di una nuova traccia ferma la riproduzione e riporta `chunkIndex` a zero. In questo modo la nuova traccia non parte da un indice che avrebbe senso solo per quella precedente;
- un comando che non si può applicare allo stato corrente non produce un errore ma restituisce lo stato invariato.

Casi limite:

- `Play` durante la riproduzione non modifica lo stato, e `Stop` da fermo azzera soltanto `chunkIndex` (utile dopo un seek). Entrambi si possono inviare più volte senza conseguenze;
- un `SeekToIndex` fuori dall'intervallo permesso effettua il riposizionamento al primo o all'ultimo chunk. Se la traccia è assente non ha effetto.

### Store dello stato

`AppStateStore` è l'unico punto in cui `AppState` viene letto e modificato. Viene creato una sola volta all'avvio dell'applicazione e la stessa istanza è condivisa da `AudioPlayer`, Controller e View, che comunicano solo attraverso esso. Internamente conserva lo stato in un `SignallingRef` di FS2. Un `Ref` di Cats Effect è un riferimento modificabile in modo atomico, il `SignallingRef` lo estende con la capacità di propagare ogni aggiornamento attraverso uno `Stream` di FS2. Le sue operazioni sono:

- `current`, che restituisce l'`AppState` corrente;
- `dispatch(command)`, che applica il comando con `AppState.handle` e restituisce l'`AppState`;
- `changes`, uno `Stream` che emette lo stato corrente alla sottoscrizione e poi a ogni modifica.

Scelte di design:

- lo store rappresenta l'unica fonte di verità dello stato dell'applicazione;
- `AppState` è immutabile per sua natura, ma il riferimento che lo condivide non lo è: lo stato corrente è un valore che viene sostituito a ogni modifica. Se più componenti leggessero lo stato, calcolassero il successivo e lo riscrivessero in quel riferimento, una modifica potrebbe sovrascrivere l'altra (*lost update*). Per questo la concorrenza è gestita solo dallo store, che applica ogni comando in modo atomico all'ultimo stato disponibile attraverso il `SignallingRef`;
- gli osservatori reagiscono ai cambiamenti senza meccanismi di polling.

Casi limite:

- un osservatore lento può non ricevere gli stati intermedi, ma solo l'ultimo;
- l'`AppState` non è salvato su disco. A ogni avvio dell'applicazione si riparte da `AppState.initial`.

### Elaborazione audio

![Effetti e processori](doc/img/05-detailed-design/effetti-processore.png){ width=100% }

`AudioEffect` rappresenta una trasformazione che, dato un `AudioChunk`, ne restituisce un altro. È un trait sealed con due sottotipi, che distinguono gli effetti in base a ciò che serve per calcolarli:

- `StatelessAudioEffect`, per gli effetti che dipendono solo dal chunk stesso: Identity, Gain, Volume, Distortion e Pan;
- `TimeAudioEffect`, per quelli che dipendono anche dalla posizione nella traccia: Echo e Autopan.

Ogni effetto restituisce `Either[AudioError, AudioChunk]`: un parametro fuori dai limiti produce un errore come valore, e non un'eccezione.

L'enumerazione `AudioEffectType` elenca gli effetti disponibili e associa a ciascuno la propria configurazione (`AudioConfiguration`, con `ParameterConfiguration` che indica minimo, massimo e valore predefinito di ogni parametro). In questo modo limiti e valori predefiniti sono definiti in un solo punto e usati sia dagli effetti stessi che dalla View.

Parametri, intervalli e comportamento di ciascun effetto sono descritti nei requisiti funzionali.

`AudioProcessor` applica una lista di `AudioEffect` ad un `AudioChunk` o ad una `AudioTrack`. Ne esistono due varianti:

- `SimpleAudioProcessor` applica gli effetti nell'ordine in cui sono dati;
- `AudioProcessorWithRules` li ordina prima per priorità (Identity, Gain, Distortion, Echo, Pan, Autopan, Volume). In tal modo il risultato dell'elaborazione non dipende dall'ordine in cui l'utente li ha configurati. L'applicazione usa questa variante.

Chi lo usa sceglie una delle due varianti e chiama `apply`, che esiste con due signature: `apply(chunk, effects, chunkIndex)`, usata per la riproduzione in tempo reale a blocchi, e `apply(track, effects)`, usata per l'esportazione. Entrambe restituiscono un `Either` con un `AudioError` oppure con l'`AudioChunk` o l'`AudioTrack` elaborati. L'indice serve agli effetti temporali; nella signature per traccia è il processore a ricavarlo, scorrendo i chunk in sequenza. Entrambi i metodi verificano gli input e poi delegano a `processChunk` o a `processTrack`, che le implementazioni possono ridefinire. Ad esempio, `AudioProcessorWithRules` li ridefinisce per ordinare gli effetti in input.

Scelte di design:

- per implementare un nuovo effetto si estende uno dei due sottotipi (`StatelessAudioEffect` o `TimeAudioEffect`), lo si registra in `AudioEffectType` e gli si assegna una priorità;
- l'ordine fisso ha una motivazione sonora, spiegata nei requisiti: il guadagno precede la distorsione perché più il segnale in ingresso è forte, più la distorsione lo satura; l'eco ripete il suono già distorto; pan e autopan spostano nello spazio stereofonico anche le ripetizioni mentre il volume regola per ultimo il livello finale;
- quando l'applicazione di un effetto fallisce, l'elaborazione si interrompe e l'errore, che riporta il nome dell'effetto, arriva al chiamante.

Casi limite:

- con lista di effetti vuota il chunk è restituito invariato;
- un chunk o una traccia vuoti sono rifiutati con un errore;
- un effetto sconosciuto (non presente in `AudioEffectType`) è accettato e applicato per ultimo;
- Pan e Autopan sono gli unici effetti che trasformano un chunk mono in stereo. Quando una traccia è elaborata per intero, basta che un chunk diventi stereo perché lo diventi l'intera traccia.

### Motore di riproduzione

`AudioPlayer` è il trait del motore di riproduzione: ha il solo metodo `run(store)`, che restituisce uno `Stream` che descrive il ciclo di vita del player, cioè l'ascolto dell'`AppState` e la riproduzione dei chunk. La sua implementazione, `StreamingAudioPlayer`, non ha uno stato proprio poiché osserva l'`AppStateStore` e agisce di conseguenza, elaborando ogni chunk subito prima di suonarlo. Riceve alla creazione un `AudioProcessor` che applica gli effetti a ogni chunk e un `AudioOutput` che lo porta in uscita. Lo store gli viene fornito solo quando viene avviato, con `run(store)`.

\newpage

![Dipendenze del motore di riproduzione](doc/img/05-detailed-design/dipendenze-player.png){ width=100% }

Anche il trait `AudioOutput` ha un solo metodo, `write(chunk, sampleRate)`. Nell'applicazione è usata l'implementazione `SpeakerAudioOutput`, che riproduce il suono attraverso la libreria Java Sound, effettuando la conversione in byte PCM e scrivendo i campioni su una linea audio con un buffer di circa 200 ms. La scrittura si blocca solo quando il buffer è pieno, finché la riproduzione non libera spazio. È disponibile anche `AudioOutput.console`, che stampa i metadati dei chunk in console. Di per sé non è ritmata, perché `write` termina subito, ma la si può rendere tale facendola attendere la durata di ogni chunk.

`StreamingAudioPlayer` elabora i chunk seguendo l'approccio *just-in-time*: non processa la traccia in anticipo, ma prepara ogni chunk subito prima di mandarlo in uscita. Una modifica degli effetti o un riposizionamento si applica quindi già dal chunk successivo. Supponendo che come `AudioOutput` sia usato `SpeakerAudioOutput`, il ritmo non deriva da un timer ma dall'output stesso, che accetta un nuovo chunk solo quando ha spazio nel buffer.

Nello specifico, `StreamingAudioPlayer` ascolta l'`AppState` e reagisce solo ai cambiamenti di `playbackState` e di `chunkIndex` in `AppState`. Quando `playbackState` diventa `Playing`, esso prende il chunk in posizione `chunkIndex`, lo elabora con `AudioProcessor` usando gli `effects` correnti, lo manda in output attraverso `AudioOutput` e invia `NextChunk` allo store. Il ciclo riparte ogni volta che `chunkIndex` è diverso dal precedente, per effetto di `NextChunk` o di `SeekToIndex`, e finisce quando `playbackState` non è più `Playing` o quando la traccia è arrivata all'ultimo chunk, nel qual caso il player stesso invia `Stop` allo store.

![Stati di `StreamingAudioPlayer`](doc/img/05-detailed-design/stati-player.png){ width=100% }

\newpage

Il diagramma di sequenza seguente mostra la riproduzione di una traccia dall'invio di `Play` allo store fino all'ultimo chunk, quando il player invia `Stop`.

![Flusso di una riproduzione](doc/img/05-detailed-design/flusso-riproduzione.png){ width=100% }

Scelte di design:

- l'`AudioPlayer` è modellato come uno `Stream` di FS2 perché deve reagire ai cambiamenti dello stato condiviso nel tempo, senza polling.
- l'elaborazione è *just-in-time*: una modifica degli effetti o un riposizionamento si sente già dal chunk successivo. In cambio l'elaborazione di un chunk deve restare più breve della sua durata. `AudioPlayer` è un trait e `StreamingAudioPlayer` ne è una sola implementazione, quindi in futuro si può aggiungere una soluzione con buffer, che elabora in anticipo alcuni chunk per assorbire i momenti in cui l'elaborazione è più lenta della riproduzione ed evitare le interruzioni.

Casi limite:

- se l'elaborazione di un chunk è più lenta della sua durata (circa 23 ms per 1024 frame a 44100 Hz), il buffer dell'output si svuota e la riproduzione procede a scatti;
- se invece la CPU è abbastanza potente, il buffer è quasi sempre pieno e una modifica non si percepisce subito, poiché il buffer non viene svuotato abbastanza rapidamente. Dopo una modifica degli effetti ma anche impartendo i comandi `Pause` o `SeekToIndex`, in uscita si sentono comunque al massimo circa 200 ms di audio, cioè circa nove chunk da 1024 frame a 44100 Hz, già elaborati con le impostazioni precedenti;
- se `AudioProcessor` restituisce un `AudioError`, l'errore viene stampato in console e si riproduce il chunk originale, poiché un effetto malfunzionante non deve interrompere la riproduzione;
- se la riproduzione di un chunk fallisce con un'eccezione (per esempio nell'`AudioOutput`), l'errore viene stampato in console, la riproduzione passa a `Stopped` con `chunkIndex` impostato a zero mentre il player resta attivo, pronto a ripartire.

### Lettura e scrittura dei file

I trait `AudioReader` e `AudioWriter` definiscono come si legge e si scrive una traccia, senza dipendere dal formato del file: `load(path)` restituisce una traccia e `save(track, path)` ne scrive una su file. `WavAudioReader` e `WavAudioWriter` ne sono le implementazioni per il WAV PCM a 16 bit little-endian, mono e stereo. Entrambe restituiscono un `Either`, quindi l'esito è un valore e gli errori appartengono a un insieme chiuso, distinto per lettura e per scrittura.

![Reader e writer](doc/img/05-detailed-design/classi-io.png){ width=100% }

Il WAV è un formato non compresso: il file contiene un'intestazione con la descrizione del formato e poi i campioni, che si leggono e si scrivono direttamente. Il sottoinsieme supportato è il PCM con segno a 16 bit, con i campioni in ordine little-endian, a uno (mono) o due (stereo) canali, con i canali intercalati frame per frame. Sono rifiutati come formato non supportato le altre codifiche (compresse, a virgola mobile, a 8 o 24 bit), i campioni big-endian e i file con più di due canali.

Il diagramma seguente mostra le operazioni eseguite da `WavAudioReader` e `WavAudioWriter`. Entrambi i flussi procedono per verifiche successive, ciascuna delle quali può terminare con un errore: solo se tutte sono completate con successo si ottiene la traccia o il file.

![Passi di lettura e scrittura](doc/img/05-detailed-design/lettura-scrittura.png){ width=85% }

Scelte di design:

- gli errori sono valori e non eccezioni: l'esito è un valore che chi chiama può esaminare e gestire senza terminare l'applicazione;
- l'applicazione supporta un solo sottoinsieme del WAV e il formato resta isolato nei due trait. Se si intende supportarne altri basta creare nuove implementazioni.
- le implementazioni WAV usano la Java Sound API; per formati più comuni come MP3 servirebbe un'altra libreria;
- lettura e scrittura sono una l'inversa dell'altra: la lettura normalizza i campioni in [-1, 1) dividendo per 32768, la scrittura li quantizza saturando i valori fuori scala, ad esempio quelli prodotti dagli effetti. L'intervallo è asimmetrico, quindi +1,0 viene limitato a 32767;
- la lettura divide i campioni in chunk di 1024 frame, l'ultimo contiene i frame rimanenti e senza riempimento;
- la scrittura verifica la traccia prima di scrivere, quindi una traccia non valida non effettua alcuna modifica al file system.

Casi limite:

- un file WAV valido ma senza frame è accettato e produce una traccia senza chunk, di durata zero;
- un file di un formato diverso da WAV PCM a 16 bit è rifiutato con un errore, così come i dati incoerenti, cioè una lunghezza non multipla della dimensione del frame o frame dichiarati diversi da quelli presenti;
- la scrittura rifiuta campioni con valori `NaN` o infiniti, una frequenza di campionamento oltre il limite del formato, una traccia che supera la dimensione massima del file e una traccia che non è né mono né stereo;
- se il file di destinazione esiste, viene sovrascritto.

## Controller

I controller sono il livello che collega la View al Model. Essi rispecchiano le azioni che l'utente può compiere sull'applicazione e le traducono in comandi per l'`AppStateStore`. Ne sono presenti uno per ciascun gruppo di azioni: riproduzione, effetti, importazione ed esportazione. I loro metodi restituiscono un valore `IO` di Cats Effect, che descrive l'azione (per esempio l'invio di un comando allo store) ma non la esegue. L'effetto avviene solo quando chi chiama il metodo esegue l'`IO`. La View lo fa con `unsafeRunAndForget`, che lo avvia su una fibra del runtime di Cats Effect e restituisce subito il controllo, senza bloccare il chiamante.

Il compito di ciascun controller è il seguente:

- `PlaybackController` permette di controllare la riproduzione con play, pause, stop e seek. Il seek riceve una posizione come durata, la converte nell'indice del chunk corrispondente e invia `SeekToIndex` con tale indice allo store.
- `EffectsController` permette di aggiungere un effetto e di rimuoverli tutti, inviando `AddEffect` e `RemoveAllEffects`.
- `LoadSaveController` permette di importare un file audio come traccia corrente e di esportare la traccia con gli effetti attivi. È l'unico controller con una logica complessa, poiché gestisce operazioni bloccanti. Esso interagisce con `AudioReader`, `AudioWriter` e `AudioProcessor`, e modifica lo stato impartendo i comandi `UpdateImport` e `UpdateExport` allo store. Per questo motivo è descritto nella sezione seguente.

\newpage

![Controller e metodi esposti](doc/img/05-detailed-design/controller-metodi.png){ width=60% }

### Importazione ed esportazione

`LoadSaveController` riceve dall'esterno lo store, un `AudioFormatRegistry` e un `AudioProcessor`, e offre quattro operazioni. Il registro permette di scegliere l'`AudioReader` o l'`AudioWriter` in base all'estensione del file, quindi il controller non dipende da un formato specifico:

- `load(path)` importa il file audio indicato e lo rende la traccia corrente;
- `save(path)` esporta la traccia corrente, con gli effetti attivi, nel file indicato;
- `acknowledgeImport` e `acknowledgeExport` confermano l'esito di un'importazione o di un'esportazione, riportando lo stato a `Idle`.

`load` e `save` avviano le rispettive operazioni in background e restituiscono subito il controllo, mentre il progresso e l'esito sono osservabili nell'`AppState`.

\newpage

Durante l'importazione `importState` passa da `Idle` a `Importing` e poi a `Imported(track)` o `Failed(error)`. A lettura finita il controller aggiorna l'`AppState` con la nuova traccia, che ferma la riproduzione e mantiene gli effetti, oppure con l'errore.

![Sequenza dell'importazione](doc/img/05-detailed-design/sequenza-importazione.png){ width=80% }

Durante l'esportazione `exportState` passa da `Idle` a `Exporting` e poi a `Exported` o `Failed(error)`. L'esportazione segue lo stesso schema con tre differenze: legge dallo store la traccia e gli effetti correnti, fa elaborare la traccia dall'`AudioProcessor` prima di passarla all'`AudioWriter`, e non parte se un'importazione è in corso.

![Sequenza dell'esportazione](doc/img/05-detailed-design/sequenza-esportazione.png){ width=75% }

\newpage

Scelte di design:

- l'esportazione lavora su una copia dell'`AppState` presa alla richiesta. Il file di output contiene quindi la traccia elaborata con gli effetti presenti in quel momento, anche se durante l'operazione l'utente carica un'altra traccia o cambia gli effetti;
- l'esportazione scrive la traccia originale sul file system se non ci sono effetti attivi;
- gli errori restituiti da reader, processore e writer finiscono tutti nello stato `Failed`, quindi l'osservatore degli stati di importazione ed esportazione riceve sempre un esito e può di conseguenza reagire;
- una richiesta di importazione o di esportazione è ignorata se un'operazione dello stesso tipo è già in corso. Inoltre una richiesta di esportazione è ignorata se è in corso un'importazione.

Casi limite:

- l'esportazione senza traccia caricata porta l'`AppState` nello stato `Failed`;
- l'esportazione di una traccia in un file esistente con lo stesso nome sovrascrive quest'ultimo;
- alla terminazione dell'applicazione un'importazione o un'esportazione ancora in corso viene interrotta e non attesa.

## View

L'interfaccia è realizzata con ScalaFX, un wrapper Scala di JavaFX, ed è una finestra unica, divisa in tre zone, una per ciascuna funzionalità: importazione ed esportazione, riproduzione e controllo degli effetti. Ogni zona ospita un pannello, composto da elementi grafici, che descrivono solo l'aspetto, e da una classe di logica, che li collega ai controller e li aggiorna in base ai flussi di stato provenienti dall'`AppStateStore`.

| Zona | Elementi grafici | Logica | Contenuto                                                                |
|----------|------------------------------|----------------------|--------------------------------------|
| In alto | `TitlePane` | `LoadSaveLogic` | titolo, stato di importazione ed esportazione, pulsanti Import ed Export |
| Al centro | `TrackBox` | `TrackBoxLogic` | stato di avanzamento e comandi di riproduzione                           |
| In basso | `EffectsPanelContainer` | `EffectsLogic` | controlli degli effetti                                                  |

Le classi di logica seguono uno schema comune. Il metodo `attach` collega gli elementi grafici ai controller, così ogni azione dell'utente diventa una chiamata a un controller. Il metodo `observe` invece effettua l'iscrizione ai flussi dello store e aggiorna l'interfaccia grafica a ogni cambiamento di stato. È un `IO` che non termina finché i flussi restano aperti, quindi si tratta di un effetto da avviare in background. In `LoadSaveLogic` si occupa di esporre gli esiti di importazione ed esportazione e di abilitare o disabilitare i pulsanti in coerenza con lo stato, mentre in `TrackBoxLogic` aggiorna la barra che rappresenta l'avanzamento della traccia. `EffectsLogic` non ha tale metodo, perché si limita a tradurre le azioni su pulsanti e cursori in effetti da aggiungere. Poiché `observe` è eseguito in background su una fibra e contiene la logica di aggiornamento dell'interfaccia grafica, tali modifiche vanno eseguite sul thread JavaFX, e quindi inoltrate con `Platform.runLater` o altri meccanismi equivalenti.

![Classi di logica della view e loro dipendenze](doc/img/05-detailed-design/classi-logica-view.png){ width=70% }

## Avvio dell'applicazione

L'applicazione è assemblata nella radice dei package da due classi con compiti distinti.

`AppSetup.make` costruisce il grafo degli oggetti in una `Resource` e svolge i seguenti compiti:

- crea un unico `AppStateStore`;
- sceglie le implementazioni concrete (il formato WAV, con `WavAudioReader` e `WavAudioWriter`, incluso nel registro dei formati, `AudioProcessorWithRules` per l'elaborazione e `SpeakerAudioOutput` per l'uscita audio) e le collega con i costruttori;
- costruisce i controller e le logiche della View;
- avvia in background lo `StreamingAudioPlayer` e gli osservatori della view, che partono solo quando la finestra è pronta;
- restituisce alla View le sole logiche, raccolte in `AppDependencies`.

`Main` è il punto di ingresso dell'applicazione. Alloca le dipendenze con `AppSetup.make`, poi lancia l'applicazione ScalaFX, che ha un proprio ciclo di vita: la finestra è costruita nel metodo `start` e il programma resta in esecuzione finché non viene chiusa. Alla chiusura rilascia la `Resource`, che interrompe le attività in background e libera le risorse.

\newpage
