# Introduzione

Il presente documento descrive la progettazione e lo sviluppo di **ScalaSound**, un'applicazione software sviluppata nell'ambito del corso di Programmazione e Progettazione del Software (PPS).

**ScalaSound** è un sistema interattivo dedicato all'elaborazione e alla manipolazione audio in tempo reale. L'applicazione consente all'utente di importare file audio (nei formati Mono e Stereo), applicare una sequenza di effetti sonori e ascoltare un'anteprima immediata del risultato prima di procedere al salvataggio della traccia finale su disco.

Nel dettaglio, l'applicazione supporta:

* Caricamento delle tracce sorgente ed esportazione del file elaborato.
* Applicazione di effetti *stateless* (quali **Gain** per l'amplificazione e **Panning** per la spazializzazione) ed effetti *stateful* complessi (quali **Autopan** ed **Eco**).
* Controllo completo della riproduzione tramite funzioni di **Play**, **Pause**, **Stop** e **Seek** (per lo spostamento lungo la traccia), con un feedback acustico immediato alle modifiche dei parametri effettuate durante l'ascolto.

\newpage