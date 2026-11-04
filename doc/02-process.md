# Processo di Sviluppo

## Metodologia
Come metodologia di sviluppo, è stata adottata la metodologia SCRUM, proposta dalle regole d’esame al punto P8, adattata al coesistente carico di lezioni universitarie.

## Ruoli
- **Jiekai Sun** - Domain Expert / Developer
- **Nome Studente 2** - Product Owner / Developer
- **Nome Studente 3** - Developer

Tutti i membri ricoprono il ruolo di Developer e contribuiscono in modo equo all'implementazione del progetto.

## Gestione e Pianificazione del Lavoro
Le attività di progetto sono state organizzate in **Sprint** dalla durata flessibile di 7-10 giorni, con un impegno di 15 ore a testa per ciascun membro. La durata variabile è stata necessaria per conciliare lo sviluppo del progetto con il coesistente carico di lezioni universitarie.

Ciascuno Sprint si articola attraverso le seguenti fasi:
- **Pianificazione iniziale:** Analisi dello status quo del progetto e definizione dell'obiettivo dello Sprint, assegnando i task a tal fine.
- **Sviluppo e Verifica:** Ciascun task si considera completato solo quando rispetta la **Definition of Done**, ovvero quando il codice compila correttamente e l'intera suite di test automatizzati viene superata con successo.
- **Meeting finale:** Verifica del raggiungimento degli obiettivi prefissati, valutazione dei risultati ottenuti ed eventuali correzioni/aggiustamenti per gli Sprint successivi.

## Strumenti Utilizzati
| Categoria                   | Strumento                  |
|:----------------------------|:---------------------------|
| **IDE di Sviluppo**         | **IntelliJ IDEA**          |
| **Version Control System**  | **Git & GitHub**           |
| **Build Tool & Dipendenze** | **SBT (Scala Build Tool)** |
| **Testing Framework**       | **ScalaTest & MUnit**      |
| **CI/CD**                   | **GitHub Actions**         |
| **Documentazione & Report** | **Pandoc e XeLaTeX**       |

## Strategia di Branching e Controllo di Versione
Il repository è stato configurato con le seguenti regole:
- È vietata la scrittura diretta sul branch principale `main`. Ogni feature o refactor viene sviluppato su un branch secondario dedicato (`feature/...` o `refactor/...`).
- Il branch `main` è protetto da push manuali. L'integrazione del codice avviene esclusivamente tramite **Pull Request**, subordinata al superamento obbligatorio di tutti i test automatizzati (sia quelli esistenti che i nuovi test introdotti nella modifica) e all'approvazione di un membro del team.

## Pipeline di Continuous Integration e Continuous Delivery (CI/CD)
L'infrastruttura è supportata da due workflow in **GitHub Actions**:

1. **Scala CI (`ci.yml`):** Ad ogni push o apertura di Pull Request su `main`, la pipeline esegue:
   * Il comando di test `sbt testFull` per verificare il successo di tutti i test automatizzati.
   * La build dell'eseguibile tramite `sbt assembly` per verificare l'assenza di errori di compilazione e assemblaggio.

2. **PDF & Release (`release.yml`):** Gestisce la documentazione e i rilasci in modalità automatizzata:
   * A ogni modifica tracciata nella cartella `doc/`, un container Docker (`pandoc/extra:latest`) con motore XeLaTeX compila automaticamente i file Markdown in un unico report PDF, salvandolo come artefatto scaricabile dall'interfaccia delle GitHub Actions.
   * Al rilascio di un tag di versione (es. `v1.0.0`), la pipeline compila contestualmente l'eseguibile `app.jar` e il report finale `relazione.pdf`, pubblicandoli automaticamente come release ufficiale su GitHub.