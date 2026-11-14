package scalasound.controller

import cats.effect.IO
import scalasound.model.processing.AudioEffect
import scalasound.model.state.AppStateStore
import scalasound.model.state.Command.{AddEffect, RemoveAll}

/**
 * The operations that the user can perform to
 * interact with the track and modify it are: add and removeAll.
 */
trait EffectsController:

  /**
   * Adds a new effect to the current effect
   * list that will be applied on the track.
   * @param effect the new effect to add to the list.
   */
  def addEffect(effect: AudioEffect): IO[Unit]

  /**
   * Clears the list of all existing effects,
   * so no effect will be applied to the current track.
   */
  def removeAll(): IO[Unit]

/**
 * A [[EffectsController]] that carries out each request by dispatching a command to the [[AppStateStore]].
 * @param store the [[AppStateStore]] that receives the playback commands.
 */
final class EffectsControllerImpl(store: AppStateStore) extends EffectsController:

  override def addEffect(effect: AudioEffect): IO[Unit] =
    store.dispatch(AddEffect(effect)).void

  override def removeAll(): IO[Unit] =
    store.dispatch(RemoveAll).void