# Introduzione

Il presente documento descrive la progettazione, l'architettura e lo sviluppo di **ScalaSound**, un'applicazione
software realizzata nell'ambito del corso di Paradigmi di Programmazione e Sviluppo (PPS).

**ScalaSound** si configura come un ambiente interattivo desktop dedicato alla riproduzione e all'elaborazione digitale
del segnale audio. L'applicazione nasce per offrire agli utenti una piattaforma per l'ascolto e la
modifica di file audio, permettendo di importare una traccia, configurare gli effetti da applicare, e
infine esportare il risultato elaborato su disco.

Nel dettaglio, le capacità principali dell'applicativo comprendono:

- L'importazione e la decodifica di file audio e la successiva esportazione della traccia elaborata.
- L'applicazione di una serie di effetti audio componibili, tra cui regolazioni di
  guadagno e volume, distorsioni non lineari, echi temporali e gestione della spazialità stereofonica.
- La riproduzione dell'audio, con gli effetti configurati se presenti, e il suo controllo attraverso le
  funzioni di avvio, pausa, arresto e cambiamento dello stato di avanzamento.

L'applicazione è pensata come un semplice riproduttore audio e non si intende replicare i controlli di una tipica workstation audio
professionale. Di conseguenza lavora su una sola traccia alla volta, non offre editing avanzato e applica ogni effetto
all'intera traccia, non alle sue porzioni.

\newpage