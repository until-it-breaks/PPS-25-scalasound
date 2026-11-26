# Retrospettiva

## Sprint 1

Periodo: dal 16/10/2026 al 26/10/2026

### Descrizione
In questo primo sprint ci siamo concentrati nell'avvio e nell'impostazione del progetto, creandone il repository e definendo il workflow
tramite le **GitHub Actions**. A seguire, ci siamo suddivisi i ruoli, abbiamo analizzato il dominio con l'obiettivo di definire le entità
base. Inoltre, ciascuno si è dedicato ad una iniziale struttura dei propri moduli e interfacce principali.
Al termine dello sprint abbiamo ottenuto una macchina a stati per il playback di base, la capacità di importare un file WAV nelle classi di dominio e
un'implementazione di base degli effetti e del processore audio.

### Backlog
Il backlog conteneva le seguenti attività:

- Setup del repository e definizione del workflow (2 ore a testa)
- Aggiunta delle entità di base del dominio, classi e trait (0,5 ore a testa)
- Implementazione di base dell'`AudioOutput` su console (completato in 4 ore da JS)
- Implementazione di base dell'`AppStateStore` (completato in 4,5 ore da JS)
- Implementazione di base dell'`AudioPlayer` (completato in 4 ore da JS)
- Implementazione di base dell'`AudioEffect` (completato in 4 ore da SC)
- Implementazione di base degli effetti `Identity`, `Gain`, `Volume` e `Pan` (completato in 4,5 ore da SC)
- Implementazione di base dell'`AudioProcessor` per soli chunk (completato in 4 ore da SC)
- Definizione degli errori tipizzati di caricamento (`AudioLoadError`) e aggiornamento del contratto `AudioReader` mediante `Either` (completato in 1 ora da MT)
- Implementazione dell'importazione di file WAV mono PCM 16-bit (completato in 3 ore da MT)
- Estensione del `WavAudioReader` per il supporto di file WAV stereo PCM 16-bit (completato in 2,5 ore da MT)
- Implementazione dei test unitari per la validazione WAV e la gestione dei casi di errore (completato in 3 ore da MT)
- Verifica dell'importazione di file WAV reali mediante test sui campioni e hash SHA-256 (completato in 3 ore da MT)

### Commenti sullo sprint
Sprint di base necessario per avviare l'intero progetto, garantendo una base solida. I tempi per le rispettive parti
sono stati rispettati, permettendo il passaggio allo sprint successivo senza intoppi.

## Sprint 2

Periodo: dal 26/10/2026 al 5/11/2026

### Descrizione
Avendo ottenuto una base solida dallo sprint precedente, abbiamo realizzato una GUI minimale per farci una prima idea dell'interfaccia utente. L'`AudioProcessor` è stato esteso per gestire una traccia intera e sono stati aggiunti gli effetti più complessi (`Distortion`, `Echo` e `Autopan`). Per la riproduzione sono stati implementati lo `StreamingAudioPlayer` e l'output audio sulle casse del dispositivo, mentre per l'I/O è stato implementato il `WavAudioWriter` per tracce mono e stereo, insieme al refactoring funzionale del `WavAudioReader`. Al termine dello sprint era possibile riprodurre una traccia con audio funzionante attraverso le casse ed esportarla in un file WAV.

### Backlog
Il backlog conteneva le seguenti attività:

- Implementazione di base dell'output audio sulle casse del dispositivo (completato in 5 ore da JS)
- Miglioramento di `AppState` (completato in 3 ore da JS)
- Miglioramento di `AppStateStore` (completato in 2 ore da JS)
- Implementazione dello `StreamingAudioPlayer` (completato in 3 ore da JS)
- Demo di riproduzione da console (completato in 2 ore da JS)
- Miglioramento dell'`AudioProcessor` per gestire una traccia (completato in 3 ore da SC)
- Implementazione di base dell'effetto `Distortion` (completato in 2 ore da SC)
- Definizione dei test (completato in 2 ore da SC)
- Implementazione di base dell'effetto `Echo` (completato in 2 ore da SC)
- Modifica time-based del trait `AudioEffect` e implementazione di base dell'effetto `Autopan` (completato in 3 ore da SC)
- Implementazione di base dell'interfaccia grafica (completato in 3 ore da SC)
- Definizione del contratto `AudioWriter` e degli errori tipizzati di esportazione `AudioSaveError` (completato in 1 ora da MT)
- Definizione dei test unitari per l'esportazione WAV mono e stereo (completato in 1,5 ore da MT)
- Implementazione dell'esportazione di tracce mono in formato PCM WAV 16-bit (completato in 2 ore da MT)
- Implementazione dell'esportazione di tracce stereo in formato PCM WAV 16-bit (completato in 2 ore da MT)
- Gestione degli errori I/O e dei casi limite del `WavAudioWriter` (completato in 1 ora da MT)
- Test di esportazione e verifica del round-trip Import/Export (completato in 0,5 ore da MT)
- Refactoring funzionale della validazione del formato e dei dati nel `WavAudioReader` (completato in 1,5 ore da MT)
- Refactoring funzionale della decodifica e della costruzione delle tracce mono e stereo (completato in 2 ore da MT)
- Aggiornamento e verifica dei test unitari di regressione del `WavAudioReader` (completato in 1,5 ore da MT)
- Integrazione dei moduli, aggiornamento Scaladoc e verifica finale (completato in 2 ore da MT)

### Commenti sullo sprint
Lo sprint è stato più impegnativo del previsto. La gestione di una traccia intera, la riproduzione in streaming, gli effetti dipendenti dal tempo e l'esportazione hanno richiesto più tempo del previsto, e sono serviti alcuni meeting per chiarire le interfacce tra i componenti del gruppo.

## Sprint 3

Periodo: dal 5/11/2026 al 15/11/2026

### Descrizione
In questo sprint ci siamo dedicati ai controller di riproduzione e di importazione ed esportazione (`PlaybackController` e `LoadSaveController`) e alla loro interazione con la GUI, in modo che l'interfaccia reagisca in tempo reale alle azioni dell'utente e ai cambiamenti di stato. Sono stati integrati i pulsanti di importazione ed esportazione, con la scelta dei file e la visualizzazione degli errori, ed è iniziata la stesura del report. Al termine dello sprint abbiamo ottenuto una GUI funzionante e quasi completa, con tutti i suoi pannelli, che interagisce con i controller. Il collegamento degli effetti allo stato dell'applicazione è stato rimandato allo sprint successivo.

### Backlog
Il backlog conteneva le seguenti attività:

- Spostamento del seek in secondi nel `PlaybackController` e adeguamento dei test (completato in 4 ore da JS)
- Demo della GUI con i soli comandi di riproduzione (completato in 4 ore da JS)
- Stesura iniziale del report: introduzione e processo (completato in 5 ore da JS)
- Correzioni alla configurazione del progetto (completato in 2 ore da JS)
- Completamento della GUI e di tutti i suoi pannelli (completato in 8 ore da SC)
- Aggiunta di `ParameterConfiguration` negli effetti e successivo refactoring di `AudioEffect` (completato in 3 ore da SC)
- Miglioramento delle formule presenti negli effetti e correzione di errori (completato in 3 ore da SC)
- Pulizia del codice e miglioramento della documentazione (completato in 1 ora da SC)
- Analisi dell'architettura esistente e definizione delle responsabilità del `LoadSaveController` (completato in 0,5 ore da MT)
- Implementazione del `LoadSaveController` per la gestione di Load e Save mediante `Cats Effect` `IO` e `Fiber` (completato in 2 ore da MT)
- Implementazione dei test unitari del `LoadSaveController`, inclusi errori e gestione delle operazioni concorrenti (completato in 1,5 ore da MT)
- Implementazione dell'osservazione reattiva mediante `FS2` degli aggiornamenti di `AppStateStore` (completato in 1,5 ore da MT)
- Implementazione dei test unitari dell'osservazione dello stato e verifica delle transizioni (completato in 1 ora da MT)
- Integrazione dei pulsanti Load e Save nella GUI, gestione dei `FileChooser` e aggiornamento della View (completato in 2,5 ore da MT)
- Refactoring funzionale di `WavAudioWriter`, con aggiornamento dei test unitari (completato in 2 ore da MT)
- Refactoring funzionale di `WavAudioReader`, con aggiornamento dei test unitari (completato in 2 ore da MT)
- Test di integrazione Import/Export, verifica del round-trip e test di regressione (completato in 1,5 ore da MT)

### Commenti sullo sprint
Le funzionalità previste sono state completate nei tempi, ad eccezione del controller degli effetti e della loro modellazione nello stato dell'applicazione, rimandati allo sprint successivo. In questa tappa ci si è concentrati sul perfezionamento della codebase esistente e sull'introduzione dei controller per completare il modello MVC.

## Sprint 4

Periodo: dal 15/11/2026 al 25/11/2026

### Descrizione
Nell'ultimo sprint abbiamo completato le funzionalità rimaste e rifinito il progetto. Sono stati implementati l'`EffectsController` e la gestione degli effetti nello stato dell'applicazione. Il codice è stato rifinito con diversi refactoring, in particolare sui componenti di riproduzione, sul package `processing` e sull'I/O dei file, dove `AudioFormatRegistry` sceglie ora il reader o il writer in base all'estensione. Sono stati inoltre migliorati i test, configurati `scalafmt` e la coverage, finalizzato il report e generati gli eseguibili.

### Backlog
Il backlog conteneva le seguenti attività:

- Refactoring di `AudioOutput`, `AudioPlayer` e `SpeakerAudioOutput`, con i relativi test (completato in 2 ore da JS)
- Refactoring di `PlaybackController`, con i relativi test (completato in 2 ore da JS)
- Refactoring di `AudioTrack` (durata e conversione tra posizione e indice del chunk) e del seek del `PlaybackController` con `FiniteDuration` (completato in 2 ore da JS)
- Configurazione della build (`scalafmt`, `scalacOptions`, `sbt-coverage`) e correzione dei warning (completato in 2 ore da JS)
- Rifinitura della GUI (completato in 1 ora da JS)
- Stesura del report (completato in 6 ore da JS)
- Refactoring di `AudioProcessor` (completato in 2 ore da SC)
- Implementazione di `EffectsController` e modifiche in `AppState` (completato in 1 ora da SC)
- Gestione della logica dei widget degli effetti nella GUI (completato in 1 ora da SC)
- Refactoring della GUI e rimozione di warning (completato in 1 ora da SC)
- Miglioramento dei test per `AudioEffects` e `AudioProcessors`, e aggiunta suite di test per `EffectsController` (completato in 2 ore da SC)
- Refactoring dei singoli effetti e del package `processing` (completato in 3 ore da SC)
- Stesura del report (completato in 5 ore da SC)

### Commenti sullo sprint
Le funzionalità previste sono state completate con alcuni giorni di anticipo rispetto alla data di consegna. Il tempo rimasto è stato usato per rifinire il codice, ampliare i test, curare la Scaladoc e finalizzare il report.

## Commenti finali
Il gruppo si ritiene soddisfatto del risultato raggiunto. ScalaSound permette di importare un file audio, modificarlo con una serie di effetti, riprodurlo ed esportarlo. Il dominio dell'elaborazione digitale del segnale (*Digital Signal Processing*, DSP) era poco familiare a tutti i membri del team, ma le difficoltà tecniche sono state superate e il lavoro è stato portato a termine con successo. Il rispetto del monte ore dedicato a
ciascuno Sprint, assieme ad una corretta pianificazione delle attività e una forte collaborazione tra i componenti del team,
ha permesso la riuscita del progetto nei tempi stabiliti.
