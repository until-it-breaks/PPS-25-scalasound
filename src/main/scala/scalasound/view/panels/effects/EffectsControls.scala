package scalasound.view.panels.effects

import cats.effect.unsafe.IORuntime
import scalasound.controller.EffectsController
import scalasound.model.processing.AudioEffect

/**
 * Wrapper on [[EffectsController]] to execute commands on effects asynchronously.
 */
trait EffectsControls:

  /**
   * Adds a new effect to the current input (track or chunk).
   * @param effect the specific effect to apply.
   */
  def setEffect(effect: AudioEffect): Unit

  /**
   * Removes all the effects applied on the input.
   */
  def removeAll(): Unit

/**
 * ScalaFx implementation of [[EffectsControls]].
 * @param controller the controller responsible for handling operations based on the effects.
 */
final class EffectsControlsImpl(controller: EffectsController)(using IORuntime) extends EffectsControls:

  override def setEffect(effect: AudioEffect): Unit =
    controller.addEffect(effect).unsafeRunAndForget()

  override def removeAll(): Unit =
    controller.removeAll().unsafeRunAndForget()