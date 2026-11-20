# Design Architetturale

## Pattern MVC

L'architettura adotta una variante del pattern **Model-View-Controller (MVC)**, in chiave reattiva. La scelta di tale pattern risponde a precise esigenze strutturali tipiche di un'applicazione multimediale interattiva:

1. In un player audio la rappresentazione visiva (controlli, timeline, pulsanti) e lo stato logico dell'applicazione si evolvono di continuo. Isolare la View dal Model rende la logica indipendente dal framework grafico, più facile da testare e modulare.
2. L'importazione di file da disco, l'esportazione e il controllo della riproduzione sono operazioni intrinsecamente asincrone. Il livello di Controller evita che la View si faccia carico di logiche operative complesse e la mantiene reattiva anche durante operazioni lunghe.

La comunicazione tra le macro-componenti del sistema segue un ciclo chiuso in cui i ruoli sono rigorosamente distinti:

- **Model:**
  Rappresenta il nucleo logico e la fonte di verità dell'applicazione. Esso incapsula lo stato globale dell'applicazione ed è costituito dalle entità di dominio e dai moduli che implementano la logica di business.
- **View:**
  Ha la responsabilità di presentare i dati all'utente e di catturarne le interazioni (click su pulsanti, posizionamento dei cursori, selezione dei file), che invia ai Controller come intenti. La View è disaccoppiata dalla logica di business e non esegue alcuna mutazione diretta del Model. Si limita a recepire e fornire un interpretazione grafica dei cambiamenti di stato emessi dal Model.
- **Controller:**
  Rappresenta il mediatore tra gli input della View e il Model, colmando il divario tra l'interfaccia utente e la logica di dominio. I componenti di controllo, dedicati alla gestione della riproduzione audio, alle operazioni di importazione/esportazione e al controllo degli effetti audio, intercettano le azioni generate dall'interfaccia, gestiscono i task asincroni complessi e traducono tali input in comandi concreti, inoltrati al Model.

Il ciclo si svolge quindi nei seguenti passi:

1. la View cattura l'azione dell'utente e la invia, come intento, al Controller di competenza;
2. il Controller traduce l'intento in un comando e lo inoltra al Model;
3. il Model applica il comando e produce un nuovo stato, che pubblica come flusso;
4. la View ascolta il flusso e si aggiorna.

![Schema del pattern MVC](doc/img/mvc.png){ width=60% }

\newpage

## Componenti e interazioni

ScalaSound è un'applicazione desktop eseguita in un unico processo e non è distribuita, quindi i componenti comunicano all'interno dello stesso programma. Il diagramma seguente mostra i componenti, chi interagisce con chi e quali dati si scambiano: le frecce continue sono richieste o invii di dati, quelle tratteggiate sono flussi di stato pubblicati da `AppStateStore`. I componenti sono raggruppati secondo i ruoli di MVC: il Model comprende `AppStateStore`, `AudioReader` e `AudioWriter`, e `AudioPlayer` con `AudioProcessor` e `AudioOutput`, perché è lì che risiede la logica dell'applicazione.

![Componenti del sistema e dati scambiati](doc/img/architettura_componenti.png){ width=80% }

- **View:**
  Mostra la finestra all'utente e raccoglie le sue azioni, che inoltra ai controller. Si aggiorna ascoltando `AppStateStore`.
- **`PlaybackController`:**
  Gestisce play, pausa, stop e posizionamento nella traccia.
- **`EffectsController`:**
  Gestisce gli effetti attivi: li aggiunge, li sostituisce e li rimuove.
- **`LoadSaveController`:**
  Gestisce importazione ed esportazione dei file, in background. Per esportare prende dallo stato la traccia e gli effetti attivi, li fa elaborare da `AudioProcessor` e fa scrivere il risultato ad `AudioWriter`.
- **`AppStateStore`:**
  Custodisce lo stato dell'applicazione, applica i comandi e avvisa chi lo ascolta quando cambia.
- **`AudioPlayer`:**
  Riproduce la traccia un chunk alla volta. Per ognuno chiede ad `AudioProcessor` di applicare gli effetti e lo manda ad `AudioOutput`.
- **`AudioProcessor`:**
  Applica una lista di effetti a un chunk o a una traccia, nell'ordine di priorità richiesto.
- **`AudioOutput`:**
  Riceve i chunk da riprodurre e li porta in uscita.
- **`AudioReader`:**
  Legge un file audio e lo trasforma in una traccia.
- **`AudioWriter`:**
  Scrive una traccia in un file audio.

I dati scambiati tra i componenti sono i seguenti:

- View → controller: azioni dell'utente (play, posizione, parametri degli effetti, percorso del file);
- controller → `AppStateStore`: comandi; `AppStateStore` → `PlaybackController` e `LoadSaveController`: stato corrente (traccia ed effetti attivi);
- `AppStateStore` → View e `AudioPlayer`: flussi di stato;
- `AudioPlayer` → `AppStateStore`: comandi di avanzamento e di arresto;
- `AudioPlayer` → `AudioProcessor`: chunk, effetti attivi e indice del chunk; `AudioProcessor` risponde con il chunk elaborato oppure con un errore;
- `AudioPlayer` → `AudioOutput`: chunk e frequenza di campionamento;
- `LoadSaveController` → `AudioReader`: percorso del file; `AudioReader` risponde con la traccia oppure con un errore;
- `LoadSaveController` → `AudioProcessor`: traccia ed effetti attivi; `AudioProcessor` risponde con la traccia elaborata oppure con un errore;
- `LoadSaveController` → `AudioWriter`: percorso e traccia elaborata; `AudioWriter` risponde con l'esito (riuscito o errore).

## Scelte tecnologiche

Scala 3, ScalaFX e Java Sound sono vincoli del progetto (si veda il capitolo dei requisiti); Cats Effect e FS2 sono stati scelti dal team per gestire concorrenza e flussi di dati senza polling. Le tecnologie sono confinate in pochi componenti: ScalaFX nella sola view, Java Sound nella lettura/scrittura WAV e nell'uscita audio.

\newpage
