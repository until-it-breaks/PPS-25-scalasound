package scalasound.view.panels.effects

import cats.effect.unsafe.IORuntime
import scalasound.controller.EffectsController
import scalasound.model.processing.AudioEffect

/**
 * Wrapper for the [[EffectsController]] hiding the `IO` effect execution.
 */
trait EffectsControllerWrapper:

  /**
   * Adds a new effect to the current effect
   * list that will be applied on the track.
   *
   * @param effect the new effect to add to the list.
   */
  def addEffect(effect: AudioEffect): Unit

  /**
   * Clears the list of all existing effects,
   * so no effect will be applied to the current track.
   */
  def removeAll(): Unit

object EffectsControllerWrapper:
  /**
   * @param controller the [[EffectsController]] that needs to be wrapped.
   * @return a new EffectsControllerWrapper.
   */
  def apply(controller: EffectsController)(using IORuntime): EffectsControllerWrapper =
    EffectsControllerWrapperImpl(controller)

/* Hidden implementation of the wrapper. */
private final class EffectsControllerWrapperImpl(controller: EffectsController)(using IORuntime) extends EffectsControllerWrapper:

  override def addEffect(effect: AudioEffect): Unit =
    controller.addEffect(effect).unsafeRunAndForget()

  override def removeAll(): Unit =
    controller.removeAll().unsafeRunAndForget()