# Requisiti

## Business
L'applicazione nasce per offrire un ambiente desktop di riproduzione e manipolazione audio, rivolto agli appassionati che desiderano ascoltare file audio applicando effetti regolabili.

L'applicazione è concepita come un riproduttore audio a traccia singola, privo di funzionalità di gestione multi-traccia o di editing sofisticato tipiche delle workstation professionali. Per questa ragione, ciascun effetto disponibile viene applicato globalmente all'intera traccia corrente, anziché su porzioni temporali o regioni specifiche.

## Modello di dominio

Dal punto di vista concettuale, il suono nel mondo reale è un'onda continua; per poter essere interpretato da un computer, viene convertito in un segnale audio digitale, ovvero una sequenza discreta di **campioni numerici** registrati ad una determinata **frequenza di campionamento** (es. 44.1 kHz). Ciascun campione è un valore numerico che esprime l'ampiezza del segnale e può essere rappresentato in modi diversi, ad esempio con numeri interi, come nei file WAV a 16 bit, oppure in virgola mobile, normalizzato nell'intervallo $[-1, 1]$ e indipendente dal formato del file, in cui lo zero corrisponde al silenzio e i due estremi sono i picchi massimi di ampiezza, in direzioni opposte.

![Campionamento di un'onda sonora](doc/img/03-requirements/campionamento.png){ width=85% }

La spazialità del suono si distingue principalmente in:

- **Mono:** Il flusso audio è composto da un unico canale sonoro, per cui il segnale viene riprodotto in modo identico su tutti i canali del dispositivo audio, senza percezione della direzionalità spaziale.
- **Stereo:** Il flusso audio si scompone in due canali distinti e sincronizzati (sinistro e destro), permettendo di distribuire il suono nello spazio orizzontale.

\newpage

![Una traccia mono ha una sola sequenza di campioni, una stereo ne ha due](doc/img/03-requirements/mono-stereo.png){ width=85% }

La frequenza di campionamento indica quanti campioni descrivono un secondo di audio, per ciascun canale: a 44.1 kHz un secondo è composto da 44100 campioni per canale, quindi 44100 in totale in una traccia mono e 88200 in una stereo. La durata di una traccia si ricava con la seguente formula:

$$\text{durata (s)} = \frac{\text{campioni di un canale}}{\text{frequenza di campionamento (Hz)}}$$

A partire da questi concetti di base sono state individuate le entità di dominio principali:

- **AudioChunk:** Rappresenta una porzione di campioni discretizzati, costituendo l'unità fondamentale per l'elaborazione e riproduzione del flusso audio.
- **AudioTrack:** L'entità principale che astrae una traccia audio indipendentemente dal formato del file. Incapsula i metadati fondamentali (quali la frequenza di campionamento e la configurazione dei canali) e una sequenza ordinata di `AudioChunk`.
- **AudioEffect:** Rappresenta una trasformazione applicabile al flusso audio. Ciascun effetto riceve in ingresso un `AudioChunk` e restituisce una versione elaborata dello stesso, consentendo di concatenare più trasformazioni in sequenza.

## Requisiti funzionali

### Utente
- L'utente deve poter selezionare e caricare dal file system un file audio nel formato `.wav`.
- L'utente deve poter visualizzare la durata totale della traccia e il punto in cui si trova la riproduzione.
- L'utente deve poter avviare, mettere in pausa e arrestare la riproduzione in qualsiasi momento.
- L'utente deve poter far proseguire l'ascolto da un punto qualsiasi della traccia, selezionandolo sulla barra di avanzamento.
- L'utente deve poter manipolare i parametri degli effetti tramite appositi controlli anche durante la riproduzione, senza interromperla. Le modifiche si percepiscono dal momento in cui vengono effettuate.
- L'utente deve poter ripristinare istantaneamente tutti i parametri degli effetti ai valori predefiniti con cui l'applicazione si avvia.
- L'utente deve poter esportare sul file system un nuovo file audio contenente l'elaborazione di tutti gli effetti selezionati nel formato `.wav`.
- L'utente deve ricevere un feedback visivo immediato tramite l'interfaccia grafica in caso di errori di importazione o esportazione, con un messaggio che ne indichi la causa.

### Sistema
- Il sistema deve decodificare flussi audio in ingresso nel formato `.wav`, supportando nativamente sia configurazioni monofoniche che stereofoniche. Sono accettati solo file con campioni PCM a 16 bit con segno; i file con altre codifiche o con più di due canali vengono rifiutati con un errore.
- Il sistema deve gestire in modo sicuro ed incapsulato eventuali anomalie di formato o di I/O, impedendo il crash dell'applicazione e notificando l'errore all'interfaccia utente.
- Il sistema deve elaborare in tempo reale le trasformazioni audio attraverso una libreria di effetti componibili, ciascuno con parametri regolabili dall'utente entro un intervallo di valori ammesso:
  - **Gain:** amplificazione del segnale per un fattore in $[0, 2]$. L'utente percepisce un incremento di potenza e spinta del suono, che può tendere alla saturazione se spinto al massimo.
  - **Volume:** moltiplicazione lineare dei campioni per un livello in $[0, 1]$. L'utente percepisce il brano più forte o più silenzioso.
  - **Distortion:** applicazione a ogni campione $x$ della funzione $\tanh(i \cdot x)$ oppure $\sin(i \cdot x)$, a scelta dell'utente, con intensità $i$ in $[0, 10]$. L'utente percepisce un'alterazione timbrica aggressiva, calda o graffiante.
  - **Echo:** somma a ogni campione quello emesso un ritardo prima, in $[10, 1000]$ ms, moltiplicato per un'attenuazione in $[0, 0.9]$. L'utente percepisce la ripetizione a distanza di tempo del segnale sonoro, creando un effetto di riverbero acustico.
  - **Pan:** distribuzione statica del suono tra i due canali, con una posizione in $[-1, 1]$ e una legge a potenza costante: al centro ciascun canale vale circa 0.707 del segnale, agli estremi si sente un solo canale. L'utente percepisce lo spostamento del suono verso il canale sinistro o destro.
  - **Autopan:** oscillazione automatica della posizione del Pan, con una frequenza in $[0, 20]$ Hz. L'utente percepisce il suono che si sposta ciclicamente da un auricolare (o cassa) all'altro in modo continuo.

  Nota: Pan e Autopan producono sempre un segnale stereo. Se applicati ad una traccia mono, quest'ultima viene duplicata su due canali.
- Il sistema deve applicare gli effetti impostati (al più uno per tipo) prima della riproduzione, in un ordine fisso, perché il risultato di una catena di effetti dipende dall'ordine in cui vengono applicati. Ogni effetto riceve il segnale già elaborato da quelli che lo precedono, nell'ordine: Gain, Distortion, Echo, Pan, Autopan, Volume. I motivi sono i seguenti:
  - il Gain precede la Distortion, perché è il livello del segnale in ingresso a determinare quanto questa satura;
  - l'Echo segue la Distortion, in modo che le ripetizioni riproducano il suono già distorto;
  - Pan e Autopan seguono l'Echo, così da spostare nello spazio anche le sue ripetizioni;
  - il Volume è l'ultimo, perché regola il livello finale dell'intera catena senza influire sul modo in cui gli altri effetti elaborano il segnale.
- Il sistema deve gestire la riproduzione attraverso tre stati (fermo, in riproduzione, in pausa): l'avvio richiede una traccia caricata, la pausa è possibile solo durante la riproduzione, l'arresto riporta l'avanzamento all'inizio e a fine traccia la riproduzione si ferma da sola.
- Il sistema deve gestire importazione ed esportazione attraverso gli stati: inattivo, in corso, riuscito o fallito. Un'importazione riuscita carica/sostituisce la traccia corrente, ferma la riproduzione e azzera l'avanzamento; l'esportazione non influisce sulla riproduzione.
- Il sistema deve consentire una sola importazione e una sola esportazione alla volta.
- Il sistema deve esportare file `.wav` PCM a 16 bit con segno, little-endian, con la stessa frequenza di campionamento della traccia originale.

## Requisiti non funzionali
- L'applicativo deve poter essere eseguito su diversi sistemi operativi (quali Windows, macOS e Linux) sfruttando la portabilità dell'ecosistema Java.
- Il tempo impiegato per l'elaborazione di un `AudioChunk` deve essere contenuto entro soglie tali da garantire una riproduzione fluida, evitando interruzioni, scatti o artefatti acustici.

## Requisiti di implementazione
Lo sviluppo del sistema adotta le seguenti tecnologie e librerie di supporto:

- **Scala 3** come linguaggio di programmazione.
- **ScalaFX** per l'implementazione dell'interfaccia grafica.
- **Java Sound API** (`javax.sound.sampled`) per l'interoperabilità di basso livello con l'hardware audio e la gestione dei flussi I/O.

\newpage