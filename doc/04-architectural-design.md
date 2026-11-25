# Design Architetturale

## Pattern MVC

L'architettura adotta una variante del pattern **Model-View-Controller (MVC)**, in chiave reattiva. La scelta di tale pattern risponde a esigenze strutturali tipiche di un'applicazione multimediale interattiva:

1. In un riproduttore audio sia l'interfaccia sia lo stato logico dell'applicazione sono soggetti a frequenti modifiche. Separare la View dal Model permette di far evolvere l'una senza ripercussioni sull'altro, e rende la logica indipendente dai componenti grafici.
2. L'importazione di file da disco, l'esportazione e il controllo della riproduzione sono operazioni intrinsecamente asincrone. Il livello di Controller evita che la View si faccia carico di logiche operative complesse e la mantiene reattiva anche durante operazioni lunghe.

La comunicazione tra le macro-componenti del sistema segue un ciclo chiuso in cui i ruoli sono rigorosamente distinti:

- **Model:**
  Rappresenta l'unica fonte di verità dell'applicazione. Incapsula lo stato globale dell'applicazione ed è costituito dalle entità di dominio e dai moduli che implementano la logica di business.
- **View:**
  Ha la responsabilità di presentare i dati all'utente e di catturarne le interazioni (click su pulsanti, posizionamento dei cursori, selezione dei file), che invia al Controller sotto forma di intenti. La View è disaccoppiata dalla logica di business e non esegue alcuna mutazione diretta del Model. Si limita a recepire e reagire ai cambiamenti di stato emessi dal Model.
- **Controller:**
  Rappresenta l'intermediario tra la View e il Model. I componenti di controllo intercettano le azioni generate dalla View, gestiscono i task asincroni complessi e traducono gli input in comandi inoltrati al Model.

Il ciclo si svolge quindi nei seguenti passi:

1. la View cattura l'azione dell'utente e la invia al Controller di competenza come intento;
2. il Controller traduce l'intento in un comando e lo inoltra al Model;
3. il Model applica il comando e produce un nuovo stato, che pubblica come flusso;
4. la View ascolta il flusso e si aggiorna.

![Schema del pattern MVC](doc/img/04-architectural-design/mvc.png){ width=60% }

\newpage

## Componenti e interazioni

ScalaSound è un'applicazione eseguita in un unico processo, quindi i componenti comunicano all'interno dello stesso programma. Il diagramma seguente mostra i componenti, le interazioni e i dati scambiati: le frecce continue sono richieste o invii di dati, quelle tratteggiate sono flussi di stato pubblicati da `AppStateStore`.

![Componenti del sistema e dati scambiati](doc/img/04-architectural-design/componenti.png){ width=80% }

- **`View`:**
  L'interfaccia grafica mostrata all'utente. Intercetta gli input di quest'ultimo e li inoltra ai controller di competenza. Si aggiorna reagendo ai cambiamenti di stato emessi da `AppStateStore`.
- **`PlaybackController`:**
  Gestisce l'avvio, la pausa e l'arresto della riproduzione, e la ripresa da un punto scelto della traccia.
- **`EffectsController`:**
  Gestisce gli effetti da applicare. Aggiunge quelli scelti dall'utente e li rimuove quando i parametri vengono ripristinati.
- **`LoadSaveController`:**
  Gestisce importazione ed esportazione dei file senza bloccare l'interfaccia. Per esportare legge dallo stato la traccia e gli effetti attivi, li fa elaborare da `AudioProcessor` e fa scrivere il risultato ad `AudioWriter`.
- **`AppStateStore`:**
  Custodisce lo stato dell'applicazione, applica i comandi e avvisa chi lo osserva quando cambia.
- **`AudioPlayer`:**
  Riproduce la traccia un blocco di campioni (chunk) alla volta. Per ciascun blocco chiede ad `AudioProcessor` di applicare gli effetti e lo invia ad `AudioOutput`.
- **`AudioProcessor`:**
  Applica una sequenza di effetti a un blocco o a un'intera traccia, nell'ordine di priorità stabilito.
- **`AudioOutput`:**
  Riceve i blocchi da riprodurre e li porta in uscita.
- **`AudioReader`:**
  Legge un file audio e lo trasforma in una traccia.
- **`AudioWriter`:**
  Scrive una traccia in un file audio.

I dati scambiati tra i componenti sono i seguenti:

- View → Controller: azioni dell'utente (avvio, pausa e arresto, posizione scelta nella traccia, parametri degli effetti, percorso del file);
- Controller → `AppStateStore`: comandi;
- `AppStateStore` → `PlaybackController` e `LoadSaveController`: stato corrente (ad esempio la traccia e gli effetti attivi);
- `AppStateStore` → View e `AudioPlayer`: flussi di stato;
- `AudioPlayer` → `AppStateStore`: comandi;
- `AudioPlayer` → `AudioProcessor`: chunk, effetti attivi e indice del chunk; `AudioProcessor` risponde con il chunk elaborato oppure con un errore;
- `AudioPlayer` → `AudioOutput`: chunk e frequenza di campionamento;
- `LoadSaveController` → `AudioReader`: percorso del file; `AudioReader` risponde con la traccia oppure con un errore;
- `LoadSaveController` → `AudioProcessor`: traccia ed effetti attivi; `AudioProcessor` risponde con la traccia elaborata oppure con un errore;
- `LoadSaveController` → `AudioWriter`: percorso e traccia elaborata; `AudioWriter` risponde con l'esito (riuscito o errore).

## Scelte tecnologiche

- **Scala 3:** linguaggio di programmazione con cui è stato implementato il sistema.
- **ScalaFX:** libreria per l'interfaccia grafica, basata su JavaFX. È usata solo nella View.
- **Java Sound API (`javax.sound.sampled`):** libreria usata per la lettura e la scrittura dei file WAV e la riproduzione attraverso la scheda audio.
- **Cats Effect:** libreria per la programmazione funzionale con effetti. Un'operazione con effetti collaterali (leggere un file, scrivere sulla scheda audio, modificare lo stato) è descritta come un valore di tipo `IO`, che non fa nulla finché non viene eseguito. Ciò permette di comporre le operazioni e di eseguirle in modo concorrente come fibre (`fiber`), thread leggeri gestiti dalla libreria e distribuiti su pool di thread, senza bloccare l'interfaccia.
- **FS2:** libreria usata per rappresentare la riproduzione e le variazioni dello stato come flussi di dati. In questo modo la View e l'`AudioPlayer` reagiscono ai cambiamenti di stato senza ricorrere a meccanismi di polling.

Cats Effect e FS2 sono usati solo dai componenti che presentano effetti collaterali durante l'esecuzione: `AppStateStore`, `AudioPlayer`, `AudioOutput`, i controller (`PlaybackController`, `EffectsController` e `LoadSaveController`) e le classi della View che gestiscono la logica dei componenti grafici a cui sono collegati.

\newpage
