# Introduzione

Il presente documento descrive la progettazione, l'architettura e lo sviluppo di **ScalaSound**, un'applicazione software realizzata nell'ambito del corso di Programmazione e Progettazione del Software (PPS).

**ScalaSound** si configura come un ambiente interattivo desktop dedicato alla riproduzione e all'elaborazione digitale del segnale audio in tempo reale (DSP). L'applicazione nasce per offrire agli utenti un sistema per l'ascolto e la modifica di file audio locali, permettendo di importare una traccia, configurare una catena di effetti personalizzati, e infine esportare il risultato elaborato su disco.

Dal punto di vista funzionale e strutturale, il sistema è progettato per gestire flussi audio sia monofonici che stereofonici. Nel dettaglio, le capacità centrali dell'applicativo comprendono:

- L'importazione e la decodifica di flussi audio dal file system e la successiva esportazione del brano elaborato.
- L'applicazione in tempo reale di una libreria di trasformazioni ed effetti audio componibili, tra cui regolazioni di guadagno e volume, distorsioni non lineari, echi temporali e gestione della spazialità stereofonica.
- Un controllo completo della riproduzione audio attraverso le funzioni di avvio, pausa, arresto e posizionamento temporale.

\newpage