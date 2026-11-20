# Requisiti

## Business
L'applicazione nasce per offrire un ambiente desktop di riproduzione e manipolazione audio in tempo reale, rivolto agli appassionati che desiderano ascoltare file audio applicando effetti regolabili.

L'applicazione è concepita come un riproduttore a traccia singola, privo di funzionalità di gestione multi-traccia o di editing sofisticato tipiche delle workstation professionali. Per questa ragione, ciascun effetto della libreria viene applicato in modo globale all'intera traccia corrente, agendo sull'intero flusso audio anziché su porzioni temporali o regioni specifiche.

## Modello di dominio

Dal punto di vista concettuale, il suono nel mondo reale è un'onda continua; per poter essere elaborato da un computer, viene convertito in un segnale audio digitale, ovvero una sequenza discreta di **campioni** numerici registrati a una determinata **frequenza di campionamento** (es. 44.1 kHz). Ciascun campione è un numero in virgola mobile che esprime l'ampiezza del segnale, normalizzata nell'intervallo $[-1, 1)$, dove 0 rappresenta il silenzio e gli estremi la massima ampiezza rappresentabile.

La spazialità del suono, che è anche quella supportata dall'applicazione, si distingue principalmente in:

- **Mono:** Il flusso audio è composto da un unico canale sonoro; il segnale viene riprodotto in modo identico su qualsiasi cassa o cuffia, senza percezione di direzionalità spaziale.
- **Stereo:** Il flusso audio si scompone in due canali distinti e sincronizzati (destro e sinistro), permettendo di distribuire il suono nello spazio orizzontale.

A partire da questi concetti di base, il dominio modella le entità principali del sistema:

- **AudioChunk:** Rappresenta una porzione di campioni audio discretizzati, costituendo l'unità fondamentale per l'elaborazione sequenziale del flusso.
- **AudioTrack:** L'entità principale che astrae il brano musicale indipendentemente dal formato del file. Incapsula i metadati fondamentali (quali la frequenza di campionamento e la configurazione dei canali) e una sequenza ordinata di `AudioChunk`.
- **AudioEffect:** L'astrazione che modella le trasformazioni applicabili al flusso audio. Ciascun effetto riceve in ingresso un `AudioChunk` e restituisce una versione elaborata dello stesso, consentendo di concatenare più trasformazioni sequenziali per la manipolazione del suono.

Dal punto di vista dinamico, il sistema include diversi cicli di vita:

- **Riproduzione ed effetti:** il riproduttore audio si trova in uno di tre stati (fermo, in riproduzione, in pausa). L'avvio richiede una traccia caricata, la pausa è possibile solo durante la riproduzione, l'arresto riporta l'avanzamento all'inizio mentre a fine traccia la riproduzione si ferma da sola. Durante la riproduzione ogni chunk viene elaborato prima di essere riprodotto, applicando gli effetti attivi (al più uno per tipo) sempre nello stesso ordine di priorità.
- **Importazione ed esportazione:** attraversano ciascuna gli stati di inattività, in corso, riuscita o fallita. Un'importazione riuscita sostituisce la traccia corrente, ferma la riproduzione e azzera l'avanzamento; l'esportazione è invece indipendente dalla riproduzione.

### Specifica degli effetti e dei formati

Ogni effetto ha uno o più parametri regolabili dall'utente. La tabella riporta, per ciascuno, l'intervallo di valori ammesso e il valore predefinito:

| Effetto    | Parametro    | Intervallo    | Predefinito |
|------------|--------------|---------------|-------------|
| Gain       | fattore      | [0, 2]        | 1           |
| Volume     | livello      | [0, 1]        | 1           |
| Distortion | intensità    | [0, 10]       | 1           |
| Echo       | attenuazione | [0, 0.9]      | 0.5         |
| Echo       | ritardo      | [10, 1000] ms | 100         |
| Pan        | posizione    | [-1, 1]       | 0           |
| Autopan    | frequenza    | [0, 20] Hz    | 1           |

Priorità di applicazione: l'ordine è fisso perché il risultato di una catena di effetti dipende dall'ordine in cui vengono applicati. La priorità scelta ha una motivazione sonora:

- il guadagno precede la distorsione, perché è il livello del segnale in ingresso a determinare quanto questa satura;
- l'eco segue la distorsione, in modo che le ripetizioni riproducano il suono già distorto;
- panning e autopan seguono l'eco, così da spostare nello spazio anche le sue ripetizioni;
- il volume è ultimo, perché regola il livello finale dell'intera catena e non deve alterare il timbro prodotto dagli altri effetti.

Comportamento degli effetti:

- **Gain:** moltiplica i campioni e limita il risultato a $[-1, 1]$.
- **Volume:** si limita a moltiplicare i campioni.
- **Distortion:** applica a ogni campione $x$ la funzione $\tanh(i \cdot x)$ oppure $\sin(i \cdot x)$, con intensità $i$, a scelta dell'utente.
- **Echo:** somma a ogni campione quello emesso un ritardo prima, moltiplicato per l'attenuazione, e limita il risultato a $[-1, 1]$.
- **Pan:** distribuisce il suono tra i due canali con una legge a potenza costante: al centro ciascun canale vale circa 0.707 del segnale, agli estremi si sente un solo canale.
- **Autopan:** fa oscillare la distribuzione del Pan alla frequenza indicata.
- **Pan e Autopan** producono sempre un segnale stereo, anche da una traccia mono, che viene duplicata sui due canali.

Formato dei file:

- Lettura e scrittura WAV in PCM con segno a 16 bit, little-endian, mono o stereo; gli altri formati vengono rifiutati con un errore.
- In esportazione i campioni fuori dall'intervallo $[-1, 1)$ vengono limitati agli estremi.

## Requisiti Funzionali

### Utente
- L'utente deve poter selezionare e caricare dal file system un file audio nel formato `.wav`.
- L'utente deve poter visualizzare la timeline, la durata totale della traccia e il momento attuale di riproduzione.
- L'utente deve poter avviare, mettere in pausa e arrestare la riproduzione in qualsiasi momento.
- L'utente deve poter spostare il cursore di riproduzione in un punto qualsiasi della traccia tramite la barra di avanzamento.
- L'utente deve poter manipolare in tempo reale i parametri degli effetti da applicare tramite appositi controlli(slider e bottoni).
- L'utente deve poter ripristinare istantaneamente tutti gli effetti ai valori di fabbrica.
- L'utente deve poter esportare su disco un nuovo file audio contenente l'elaborazione finale dei filtri applicati nel formato `.wav`.
- L'utente deve ricevere un feedback visivo immediato tramite l'interfaccia grafica in caso di errori di importazione o esportazione.

### Sistema
- Il sistema deve decodificare flussi audio in ingresso nel formato `.wav`, supportando nativamente sia configurazioni monofoniche che stereofoniche.
- Il sistema deve gestire in modo sicuro ed incapsulato eventuali anomalie di formato o di I/O durante la lettura e la scrittura, impedendo il crash dell'applicazione e notificando l'errore all'interfaccia utente.
- Il sistema deve elaborare in tempo reale le trasformazioni audio attraverso una libreria di effetti componibili, che include:
  - **Gain:** Amplificazione del segnale audio (con fattore scalare e gestione dei limiti di picco). L'utente percepisce un incremento di potenza e spinta del suono, che può tendere alla saturazione se spinto al massimo.
  - **Volume:** Regolazione lineare del livello complessivo d'ascolto. L'utente percepisce semplicemente il brano più forte o più silenzioso, senza alterazioni timbriche o di dinamica.
  - **Distortion:** Elaborazione non lineare della forma d'onda (tramite funzioni come la tangente iperbolica $\tanh$ o modulazioni sinusoidali). L'utente percepisce un'alterazione timbrica aggressiva, calda o graffiante.
  - **Echo:** Generazione di echi temporali configurabili tramite fattore di attenuazione, ritardo in millisecondi e frequenza di campionamento. L'utente percepisce la ripetizione a distanza di tempo del segnale sonoro, creando un effetto di riverbero acustico.
  - **Pan:** Gestione statica della spazialità stereofonica. L'utente percepisce lo spostamento del suono prevalentemente verso il canale sinistro o destro.
  - **Autopan:** Modulazione dinamica automatica della spazialità. L'utente percepisce il suono che si sposta fluidamente e ciclicamente da un auricolare (o cassa) all'altro in modo continuo.
- Il sistema deve supportare la composizione e l'applicazione sequenziale di tali trasformazioni, consentendo sia un'elaborazione basata sull'ordine dinamico sia l'applicazione di regole di prioritizzazione nella catena degli effetti.
- Il sistema deve gestire le transizioni di stato del riproduttore audio attraverso una macchina a stati finiti coerente.
- Il sistema deve consentire una sola importazione e una sola esportazione alla volta.
- Il sistema deve consentire l'esportazione del flusso audio elaborato salvandolo su disco nel formato `.wav`.

## Requisiti non funzionali
- L'applicativo deve poter essere eseguito su diversi sistemi operativi (quali Windows, macOS e Linux) sfruttando la portabilità dell'ecosistema Java.
- Il tempo impiegato per l'elaborazione di un `AudioChunk` deve essere contenuto entro soglie tali da garantire una riproduzione fluida, evitando interruzioni, scatti o artefatti acustici.

## Requisiti di implementazione
Lo sviluppo del sistema adotta le seguenti tecnologie e librerie di supporto:

- **Scala 3** come linguaggio di programmazione.
- **ScalaFX** per l'implementazione dell'interfaccia grafica.
- **Java Sound API** (`javax.sound.sampled`) per l'interoperabilità di basso livello con l'hardware audio e la gestione dei flussi I/O.

\newpage