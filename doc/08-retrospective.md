\newpage

# Retrospettiva

Descrizione finale dettagliata dell'andamento dello sviluppo, del backlog, delle iterazioni e commenti finali

## Sprint 1

### Descrizione dell'andamento dello sviluppo
In questo primo sprint ci siamo concentrati nell'avvio e nell'impostazione del progetto, creandone il repository e definendo il workflow
tramite le **Github Actions**. A seguire, ci siamo suddivisi i ruoli, abbiamo analizzato il dominio con l'obiettivo di definire le entità
base. Inoltre, ciascuno di noi si è dedicato ad una iniziale struttura dei propri moduli e interfacce principali.
Al termine dello sprint si ha ottenuto una macchina a stati playback di base, la capacità per di importare un *wav* nell'applicazione e
una implementazione iniziale di effetti di base e dell'audio processor funzionante.

### Backlog
Il backlog conteneva le seguenti attività:
- Setup del repository e definizione del workflow (5 ore a testa)
- Aggiunta delle entità di base del dominio, classi e traits (3 ore a testa)
- Implementazione iniziale di un `AudioOutput` console-based (completato in 3 ore da JS)
- Definizione di base dell'`AppStateStore` (completato in 2 ore da JS)
- Definizione di base dell'`AudioPlayer` (completato in 2 ore da JS)
- Definizione di base dell'`AudioEffects` (completato in 2 ore da SC)
- Prima implementazione di effetti di base: `Identity`, `Gain`, `Volume`, `Pan` (completato in 3 ore da SC)
- Definizione di base dell'`AudioProcessor` per soli chunks (completato in 2 ore da SC)

### Iterazioni

### Commenti sullo sprint
Sprint di base necessario per avviare l'intero progetto, garantendo una base solida. I tempi per le rispettive parti
sono stati rispettati, permettendo il passaggio allo sprint successivo senza intoppi.

## Sprint 2

### Descrizione dell'andamento dello sviluppo
Avendo ottenuto una base solida da cui partire, grazie allo sprint precedente, abbiamo definito una GUI minimale per
definire una prima idea di interfaccia con l'utente. In seguito, è stato aggiornato l'`AudioProcessor` per gestire una 
traccia intera, sono state aggiunte le prime implementazioni di base degli effetti più complessi, in modo che al termine
dello sprint corrente si potesse riprodurre una track con audio funzionante correttamente attraverso le casse del
dispositivo.

### Backlog
Il backlog conteneva le seguenti attività:
- Output sonoro di base via impianto audio del dispositivo (completato in 5 ore da JS)
- Miglioramento delle funzionalità dell'`AppState` (completato in 3 ore da JS)
- Miglioramento delle funzionalità dell'`AppStateStore` (completato in 2 ore da JS)
- Definizione dello `StreamingAudioPlayer` (completato in 3 ore da JS)
- Demo playback via console (completato in 2 ore da JS)
- Miglioramento dell'`AudioProcessor` per gestire una traccia (completato in 3 ore da SC)
- Aggiunta di base dell'effetto `Distortion` (completato in 2 ore da SC)
- Implementazione di base dell'effetto `Echo` (completato in 2 ore da SC)
- Modifica time-based del `trait AudioEffect` e implementazione base dell'effetto `Autopan` (completato in 3 ore da SC)
- Definizione e implementazione di base dell'interfaccia grafica (completato in 3 ore da SC)

### Iterazioni

### Commenti sullo sprint
Lo sprint è risultato essere più impegnativo del previsto, richiedendo un tempo superiore alle aspettative per
la gestione della traccia,la sua importazione e la gestione degli effetti in tempo reale. Sono stati necessari alcuni
meeting nel corso dello sprint per chiarire le idee tra i vari componenti del gruppo.

## Sprint 3

### Descrizione dell'andamento dello sviluppo
In questo sprint ci siamo dedicati alla gestione dei controller (`EffectsController`, `LoadSaveController`, `PlaybackController`)
e la loro interazione con la GUI, al fine di reagire ai cambiamenti in modo reattivo alle azione che l'utente esegue
nell'applicazione. Al termine dello sprint abbiamo ottenuto una GUI funzionante e quasi completa, che interagisce con i 
controller, anch'essi funzionanti. Inoltre, alla traccia importata, a cui si può interagire tramite i pulsanti messi a 
disposizione, possono essere applicati correttamente tutti gli effetti descritti. 

### Backlog
- Aggiunta spostamento del seek in secondi al `PlaybackController` e adeguamento dei test(completato in 4 ore da JS)
- Definizione di una GUI demo con i soli comandi di riproduzione (completato in 4 ore da JS)
- Stesura iniziale del report: introduzione processo (completato in 5 ore da JS)
- Correzioni minori alla configurazione di progetto (completato in 2 ore da JS)
- Completamento della GUI e di tutti i suoi pannelli (completato in 8 ore da SC)
- Aggiunta di `ParameterConfiguration` negli effetti e successivo refactoring di `AudioEffect` (completato in 3 ore da SC)
- Miglioramento delle formule presenti negli effetti e correzione di errori (completato in 3 ore da SC)
- Puliza del codice e miglioramento della documentazione (completato in 1 ora da SC)

### Iterazioni

### Commenti sullo sprint
Tutte le funzionalità richieste sono state completate con successo nei tempi previsti. In particolare in questa tappa
ci si è concentrati sul perfezionamento della codebase esistente e all'introduzione della componente Controller per 
completare il modello MVC.

## Sprint 4

### Descrizione dell'andamento dello sviluppo
L'ultimo sprint è stato necessario per rifinire il codice (pulizia e refactoring), migliorare i test, finalizzare il report
e generare gli eseguibili. 

### Backlog
- Collegamento della logica di riproduzione alla GUI e refactoring dell'entry point (completato in 2 ore da JS)
- Refactoring di `AudioOutput`, `AudioPlayer` e `SpeakerAudioOutput` con aggiunta dei relativi test (completato in 2 ore da JS)
- Refactoring di `PlaybackController` e relativa suite di test (completato in 2 ore da JS)
- Configurazione della build: *scalafmt*, *scalacOptions*, *sbt-coverage* e correzione dei warning (completato in 2 ore da JS)
- Rifinitura della GUI (completato in 1 ora da JS)
- Stesura del report - Proseguimento - (completato in 6 ore da JS)
- Refactoring di `AudioProcessor` (completato in 2 ore da SC)
- Implementazione di `EffectsController` e modifiche in `AppState` (completato in 1 ora da SC)
- Gestione della logica dei widget degli effetti nella GUI (completato in 1 ora da SC)
- Refactoring della GUI e rimozione di warning (completato in 1 ora da SC)
- Miglioramento dei test per `AudioEffects` e `AudioProcessors`, e aggiunta suite di test per `EffectsController` (completato in 2 ora da SC)
- Refactoring dei singoli effetti e del package `processing` (completato in 3 ore da SC)
- Stesura del report (completato in 5 ore da SC)

### Iterazioni

### Commenti sullo sprint
Le funzionalità di dominio sono state completate con qualche giorno di anticipo rispetto alla data di consegna, lasciandoci
il tempo necessario a migliorare il codice e la documentazione presente, sia per quanto riguarda il report sia per la *scaladoc*.

# Conclusioni
Il gruppo si ritiene soddisfatto del risultato raggiunto nella realizzazione di un sistema di *importazione*, *modifica* ed
*esportazione* di un file audio. Nonostante le complessità tecniche derivanti da un dominio del *Digital Signal Processing* (DSP), 
poco familiare a tutti i membri del team, il lavoro è stato portato a termine con successo. Il rispetto del monte ore dedicato a 
ciascuno Sprint, assieme ad una corretta pianificazione delle attività e una forte collaborazione tra i componenti del team,
ha permesso la riuscita del progetto nei tempi stabiliti.