package scalasound.view.panels.effects

import cats.effect.unsafe.IORuntime
import scalafx.scene.control.Slider
import scalasound.controller.EffectsController
import scalasound.model.processing.AudioEffect
import scalasound.view.panels.effects.DistortionEnum.{Sin, Tanh}

object EffectsLogic:
  /*
   * It contains the listener that needs to be associated to the specific effect.
   * Everytime the slider is moved it needs to update itself and perform the action.
   */
  extension (slider: Slider)
    private def sliderListener(onUpdate: Float => Unit): Unit =
      slider.valueChanging.onChange { (_, wasChanging, isChanging) =>
        if wasChanging && !isChanging then onUpdate(slider.value.value.toFloat)
      }
      slider.onMouseReleased = _ =>
        if !slider.valueChanging.value then onUpdate(slider.value.value.toFloat)

/**
 * Wrapper on [[EffectsController]] to execute commands on effects asynchronously.
 * Event actions are triggered when the sliders values change,
 * and only once when the user releases the slider after dragging or
 * the user clicks on the specific point onto the slider.
 * @param controller the controller responsible for handling operations based on the effects.
 */
final class EffectsLogic(controller: EffectsController)(using IORuntime):
  import EffectsLogic.sliderListener

  /*
   * Adds a new effect to the current input (track or chunk).
   */
  private def setEffect(effect: AudioEffect): Unit =
    controller.addEffect(effect).unsafeRunAndForget()

  /*
   * Removes all the effects applied on the input.
   */
  private def removeAll(): Unit =
    controller.removeAll().unsafeRunAndForget()

  /**
   * Connects the buttons and the sliders of the given pane to the [[EffectsController]].
   * Must be called once, on the JavaFX thread, before the box is shown.
   * @param effectPanel the panel containing the "views" of each effect.
   */
  def attach(effectPanel: EffectsPanelContainer): Unit =
    val volumeGainBox = effectPanel.volumeGainBox
    val distortionPanBox = effectPanel.distortionPanBox
    val echoAutopanBox = effectPanel.echoAutopanBox

    effectPanel.resetAllButton.onAction = _ =>
      controller.removeAll()
      volumeGainBox.resetSlidersAndValues()
      distortionPanBox.resetSlidersAndValues()
      echoAutopanBox.resetSlidersAndValues()

    volumeGainLogic(volumeGainBox)
    distortionPanLogic(distortionPanBox)
    echoAutopanLogic(echoAutopanBox)

  /*
   * Handles the logic of volume and gain sliders.
   */
  private def volumeGainLogic(vg: VolumeGainBox): Unit =
    vg.volumeSlider.sliderListener(v => setEffect(AudioEffect.volume(v)))
    vg.gainSlider.sliderListener(v => setEffect(AudioEffect.gain(v)))

  /*
   * Handles the logic of distortion and pan sliders, plus the distortion filter button.
   */
  private def distortionPanLogic(dp: DistortionPanBox): Unit =
    val distortionFilter = dp.distortionFilter
    distortionFilter.onChange { (_, _, newFilter) =>
      updateDistortion(dp.distortionSlider.value.value.toFloat, newFilter)
    }
    dp.distortionSlider.sliderListener(v => updateDistortion(v, distortionFilter.value))
    dp.panSlider.sliderListener(v => setEffect(AudioEffect.pan(v)))

  /*
   * Defines how to treat the clipping function when a
   * specific DistortionEnum value is inserted.
   */
  private def updateDistortion(value: Float, filter: DistortionEnum): Unit =
    val isSoft = filter match
      case Tanh => true
      case Sin => false
    setEffect(AudioEffect.distortion(value, isSoftClipping = isSoft))

  /*
   * Handles the logic of echo and autopan sliders.
   */
  private def echoAutopanLogic(ea: EchoAutopanBox): Unit =
    val alphaSlider = ea.alphaSlider
    val delaySlider = ea.delaySlider
    alphaSlider.sliderListener(_ => updateEcho(alphaSlider, delaySlider))
    delaySlider.sliderListener(_ => updateEcho(alphaSlider, delaySlider))
    ea.autopanSlider.sliderListener(v => setEffect(AudioEffect.autopan(v)))

  /*
   * Defines how to treat the echo effect when one of
   * the two parameters, alpha or delay, changes.
   */
  private def updateEcho(alphaSlider: Slider, delaySlider: Slider): Unit =
    val alpha = alphaSlider.value.value.toFloat
    val delay = delaySlider.value.value.toInt
    setEffect(AudioEffect.echo(alpha, delay))