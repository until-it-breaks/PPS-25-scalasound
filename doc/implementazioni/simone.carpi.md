---
title: Simone Carpi
parent: Implementation
---

# Implementazione - Simone Carpi

## Panoramica dei contributi
Il mio contributo al progetto si è focalizzato nelle seguenti aree:

- **Effetti**: definizione e implementazione degli effetti applicabili ai vari *chunk* e alla singola *traccia*, nello specifico:
`Identity`, `Gain`, `Distortion`, `Echo`, `Volume`, `Pan` e `Autopan`.
- **Processori Audio**: definizione e implementazione di `AudioProcessor`, componente dedito alla gestione e alla applicazione sequenziale degli effetti sull'input ricevuto.
- **Controller**: implementazione di `EffectsController` che espone le azione che l'utente esegue durante l'interazione con la traccia.
- **AudioADT**: parziale definizione e implementazione degli aspetti principali legati al dominio.
- **Interfaccia grafica**: realizzazione completa dei pannelli che costituiscono la GUI e gestione della logica associata agli effetti.

---

## Effetti
Gli effetti audio trasformazioni matematiche applicate alla traccia presa in carico (la quale costituita da un insieme di chunk).
Al fine di garantire uniformità all'interno della pipeline di elaborazione gestita dal `AudioProcessor`, la gerarchia è stata definita su un `<<sealed trait>>` denominato `AudioEffect`,
consentendo il *pattern matching* durante la fase di elaborazione.
A causa della presenza di effetti legati al tempo, si è considerato di specializzare ulteriormente `AudioEffect` in due contratti, `trait` separati: 
- `StatelessAudioEffect`: definisce l'interfaccia per gli effetti indipendenti dal tempo (`Identity`, `Gain`, `Distortion`, `Volume` e `Pan`), esponendo un metodo `apply` che richiede il chunk da elaborare.
- `TimeAudioEffect`: definisce l'interfaccia per gli effetti temporali (`Echo` e `Autopan`) e fornendo un metodo `apply` che prende in ingresso il chunk e il suo indice. Quest'ultimo agisce come punto di riferimento
temporale necessario al calcolo della trasformazione.

Per evitare di lanciare eccezioni a runtime ogni effetto restituisce un tipo `Either[AudioError, AudioChunk]`. In questo modo, in caso di eventuali situazioni di errore causate dall'immisione di parametri non validi o al di fuori degli intervalli
consentiti, il calcolo dell'effetto (trasformazione) verrà interrotto tramite il ritorno di un `Left` contenente un `AudioError` (definito tramite gli `enum` di Scala 3). 
Nel caso in cui succeda un errore verrà restituito un `AudioError` definito per mezzo di un `Algebraic Data Types` con gli **enum** di Scala 3.

```scala
sealed trait AudioEffect:
  protected def checkConstraints(isValid: Boolean, msg: String): Option[AudioError] =
    if isValid then Option.empty
    else Some(AudioError.AudioEffectsError(this, msg))

trait StatelessAudioEffect extends AudioEffect:
  def apply(chunk: AudioChunk): Either[AudioError, AudioChunk]

trait TimeAudioEffect extends AudioEffect:
  def apply(chunk: AudioChunk, chunkIndex: Int): Either[AudioError, AudioChunk]

```