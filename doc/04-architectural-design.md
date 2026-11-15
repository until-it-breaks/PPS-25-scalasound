# Design Architetturale

## Pattern MVC

L'architettura adotta una variante del pattern **Model-View-Controller (MVC)**, in chiave reattiva.

La scelta del pattern MVC risponde a precise esigenze strutturali tipiche di un'applicazione multimediale interattiva:

1. In un player audio, la rappresentazione visiva (i controlli grafici, la timeline, i pulsanti) e lo stato logico dell'applicazione si evolvono con finalità differenti. Isolare la View dal Model garantisce che la logica di business e la gestione dello stato rimangano del tutto indipendenti dal framework grafico, rendendo il sistema più facile da testare, modulare e potenzialmente indipendente da vincoli tecnologici specifici della UI.
2. Operazioni come l'importazione di file da disco, il controllo del flusso di riproduzione introducono una forte componente asincrona. L'introduzione di un livello di Controller evita che la View si faccia carico di logiche operative complesse.
3. A differenza delle implementazioni imperative tradizionali, l'approccio reattivo sfrutta uno stato centralizzato immutabile e flussi di dati continui. In questo modo la View si limita a rispecchiare in tempo reale le variazioni di stato propagate dal sistema.

La comunicazione tra le macro-componenti del sistema segue così un ciclo chiuso in cui i ruoli e le responsabilità sono rigorosamente distinti:

- **Model:**
  Rappresenta il nucleo logico e la "fonte di verità" dell'applicazione. È costituito dalla struttura dati globale (`AppState`), dalle entità di dominio (`AudioTrack`, `AudioChunk`) e dai moduli che implementano le regole di business e la logica di elaborazione.

- **View:**
  Ha la responsabilità esclusiva di presentare i dati all'utente e di catturarne le interazioni fisiche (click su pulsanti, posizionamento dei cursori, selezione dei file). La View è disaccoppiata dalla logica di business e non esegue alcuna mutazione diretta: si limita a recepire in modo passivo i cambiamenti attraverso l'ascolto continuo dei flussi di stato emessi dal Model.

- **Controller:**
  Rappresenta il mediatore tra gli input della View e il Model, colmando il divario tra l'interfaccia utente e la logica di dominio. I componenti di controllo, dedicati alla gestione del playback, alle operazioni asincrone di importazione/esportazione su file system e al controllo degli effetti audio da applicare, intercettano le azioni generate dall'interfaccia, gestiscono i task asincroni complessi e traducono tali input in comandi concreti, inoltrandoli al Model.

![Schema del pattern MVC](doc/img/mvc.png){ width=80% }

\newpage

## Gestione dello stato

Il nucleo dello stato dell'applicazione e la gestione della concorrenza sono progettati secondo i principi della programmazione funzionale, sfruttando i costrutti di Cats Effect e FS2:

1. L'intero stato globale, che include lo stato di riproduzione, la traccia audio attiva, la catena degli effetti selezionati (`effects`), la testina di riproduzione (`chunkIndex`) e gli stati di importazione/esportazione, è incapsulato all'interno della `case class` immutabile `AppState`. Qualsiasi modifica avviene in modo puramente funzionale generando nuove copie tramite il costrutto `.copy()`. Le transizioni di stato sono effettuate deterministicamente da una macchina a stati finiti guidata da comandi predefiniti (`Command`).

2. La creazione di un'astrazione centralizzata come `AppStateStore` si è resa necessaria per coordinare l'accesso concorrente e la mutazione dello stato da parte di flussi operativi differenti e paralleli, quali l'interfaccia grafica (GUI) e il motore di riproduzione. Basato internamente su un `SignallingRef` di FS2, lo store garantisce che le modifiche, eseguite tramite l'operazione di `dispatch`, avvengano in modo atomico e thread-safe. Contestualmente, lo store sfrutta flussi continui di dati (*Stream*) per propagare in tempo reale le variazioni di stato (globali o granulari) a tutti i componenti in ascolto, eliminando la necessità di meccanismi di polling.

![Flusso di controllo e gestione reattiva dello stato](doc/img/appstate_architecture.png){ width=80% }

\newpage

## Organizzazione dei Package

All'interno del package radice `scalasound`, l'architettura si articola nei seguenti macro-componenti:

- **`model`**:
  - `model.domain`: Contiene le definizioni delle classi di dominio principali.
  - `model.state`: Gestione dello stato globale immutabile (`AppState`) e dello store reattivo (`AppStateStore`).
  - `model.io`: Componenti per la lettura e scrittura dei file audio.
  - `model.player`: Gestione dell'output audio e del motore di riproduzione.
  - `model.processing`: Gestione dell'elaborazione del segnale digitale e degli effetti applicabili.

- **`controller`**:
  - `PlaybackController`: Gestione dei comandi di riproduzione.
  - `LoadSaveController`: Gestione dei flussi di importazione ed esportazione delle tracce.
  - `EffectsController`: Gestione dell'applicazione degli effetti audio.

- **`view`**: ???

\newpage