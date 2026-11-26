# Processo di Sviluppo

## Metodologia
La metodologia di sviluppo applicata su questo progetto è di tipo Agile, in particolare si è adottata la variante Scrum, proposta dalle regole d’esame al punto P8.

## Ruoli
- **Jiekai Sun** - Domain Expert / Developer
- **Simone Carpi** - Product Owner / Developer
- **Martin Tomassi** - Developer

Tutti i membri ricoprono il ruolo di Developer e contribuiscono in modo equo all'implementazione del progetto.

## Gestione e pianificazione del lavoro
Le attività di progetto sono state organizzate in **Sprint** con cadenza di 10 giorni, con un impegno di 15 ore a testa per ciascun membro. Si è scelta una cadenza più lunga di quella tipica (7 giorni) per far fronte a impegni personali già previsti e al coesistente carico di lezioni universitarie.

Ciascuno Sprint si articola attraverso le seguenti fasi:

- **Pianificazione iniziale:** Analisi dello status quo del progetto e definizione dell'obiettivo dello Sprint, assegnando i task a tal fine.
- **Sviluppo e Verifica:** Ciascun task si considera completato solo quando rispetta la **Definition of Done**, ovvero nel momento in cui il codice compila correttamente e l'intera suite di test automatizzati viene superata con successo.
- **Meeting finale:** Verifica del raggiungimento degli obiettivi prefissati, valutazione dei risultati ottenuti ed eventuali aggiustamenti per gli Sprint successivi.

## Strumenti utilizzati
| Categoria                   | Strumento             |
|:----------------------------|:----------------------|
| **IDE di Sviluppo**         | **IntelliJ IDEA**     |
| **Version Control System**  | **Git e GitHub**      |
| **Build Tool**              | **SBT**               |
| **Formattazione Codice**    | **scalafmt**          |
| **Testing Framework**       | **ScalaTest e MUnit** |
| **Code Coverage**           | **Scoverage**         |
| **CI/CD**                   | **GitHub Actions**    |
| **Documentazione e Report** | **Pandoc e XeLaTeX**  |

## Strategia di branching
Il repository è stato configurato con le seguenti regole:

- È vietata la scrittura diretta sul branch principale `main`. Ogni modifica viene sviluppata su un branch secondario dedicato (`feature/...`, `refactor/...`, `chore/...`).
- Il branch `main` è protetto da push diretti. L'integrazione del codice avviene esclusivamente tramite **Pull Request**, subordinata al superamento di tutti i test automatizzati (sia quelli esistenti che i nuovi test introdotti nella modifica) e all'approvazione di un membro del team.

## Continuous Integration e Continuous Delivery
L'infrastruttura è supportata da due workflow in **GitHub Actions**:

1. **`ci.yml`**: Ad ogni push o apertura di Pull Request su `main`, la pipeline esegue:
   * Il comando `sbt testFull` per verificare il successo di tutti i test automatizzati.
   * La build dell'eseguibile tramite `sbt assembly` per verificare l'assenza di errori di compilazione e assemblaggio.

2. **`release.yml`**: Gestisce la documentazione e i rilasci in modalità automatizzata:
   * A ogni modifica tracciata nella cartella `doc/`, la pipeline compila automaticamente i file Markdown in un unico report PDF, tramite Pandoc e il motore XeLaTeX, salvandolo come artefatto scaricabile dall'interfaccia di GitHub Actions.
   * Compila anche l'eseguibile con `sbt assembly` e, al rilascio di un tag di versione (es. `v1.0.0`), pubblica automaticamente come release ufficiale su GitHub l'eseguibile `scalasound.jar` e il documento `report.pdf`.

\newpage