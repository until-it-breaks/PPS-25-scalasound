package scalasound.view.panels.effects

import cats.effect.unsafe.IORuntime
import scalasound.controller.EffectsController
import scalasound.model.processing.AudioEffect

/**
 * Proxy for the [[EffectsController]] hiding the `IO` effect execution.
 */
trait EffectsControllerProxy:

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

object EffectsControllerProxy:
  /**
   * @param controller the [[EffectsController]] that needs to be wrapped.
   * @return a new EffectsControllerProxy.
   */
  def apply(controller: EffectsController)(using IORuntime): EffectsControllerProxy =
    EffectsControllerProxyImpl(controller)

/* Hidden implementation of the proxy. */
private final class EffectsControllerProxyImpl(controller: EffectsController)(using IORuntime) extends EffectsControllerProxy:

  override def addEffect(effect: AudioEffect): Unit =
    controller.addEffect(effect).unsafeRunAndForget()

  override def removeAll(): Unit =
    controller.removeAll().unsafeRunAndForget()