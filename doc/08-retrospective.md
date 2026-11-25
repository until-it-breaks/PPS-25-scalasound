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

## Sprint 4

# Conclusioni