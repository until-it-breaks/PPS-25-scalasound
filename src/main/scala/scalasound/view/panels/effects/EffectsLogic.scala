package scalasound.view.panels.effects

import scalafx.scene.control.Slider
import scalasound.model.processing.AudioEffect
import scalasound.view.panels.effects.DistortionEnum.Tanh

object EffectsLogic:
  /*
   * It contains the listener that needs to be associated to the specific effect.
   * Everytime the slider is moved it needs to update itself and perform the action.
   */
  extension (slider: Slider)

    private def sliderValue: Float = slider.value.value.toFloat

    private def sliderListener(onUpdate: Float => Unit): Unit =
      slider.valueChanging.onChange { (_, wasChanging, isChanging) =>
        if wasChanging && !isChanging then onUpdate(slider.sliderValue)
      }
      slider.onMouseReleased = _ =>
        if !slider.valueChanging.value then onUpdate(slider.sliderValue)

  extension (filter: DistortionEnum)
    /* Checks if the distortion to be applied is SoftClipping or not. */
    private def isSoftClipping: Boolean = filter == Tanh

/** Execute commands on effects asynchronously. Event actions
  * are triggered when the sliders values change, and only once when
  * the user releases the slider after dragging or the user clicks on
  * the specific point onto the slider.
  * @param controller
  *   the controller responsible for handling operations based on the effects.
  */
final class EffectsLogic(controller: EffectsControllerWrapper):

  import EffectsLogic.*

  /** Connects the buttons and the sliders of the given pane to the controller. Must be
    * called once, on the JavaFX thread, before the box is shown.
    * @param effectPanel
    *   the panel containing the "views" of each effect.
    */
  def attach(effectPanel: EffectsPanelContainer): Unit =
    val volumeGainBox    = effectPanel.volumeGainBox
    val distortionPanBox = effectPanel.distortionPanBox
    val echoAutopanBox   = effectPanel.echoAutopanBox

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
  private def volumeGainLogic(volumeGain: VolumeGainBox): Unit =
    volumeGain.volumeSlider.sliderListener(v => controller.addEffect(AudioEffect.volume(v)))
    volumeGain.gainSlider.sliderListener(v => controller.addEffect(AudioEffect.gain(v)))

  /*
   * Handles the logic of distortion and pan sliders, plus the distortion filter button.
   */
  private def distortionPanLogic(distortionPan: DistortionPanBox): Unit =
    val distortionFilter = distortionPan.distortionFilter
    distortionFilter.onChange: (_, _, newFilter) =>
      updateDistortion(distortionPan.distortionSlider.sliderValue, newFilter)
    distortionPan.distortionSlider.sliderListener(v => updateDistortion(v, distortionFilter.value))
    distortionPan.panSlider.sliderListener(v => controller.addEffect(AudioEffect.pan(v)))

  /*
   * Defines how to treat the clipping function when a
   * specific DistortionEnum value is inserted.
   */
  private def updateDistortion(value: Float, filter: DistortionEnum): Unit =
    controller.addEffect(AudioEffect.distortion(value, isSoftClipping = filter.isSoftClipping))

  /*
   * Handles the logic of echo and autopan sliders.
   */
  private def echoAutopanLogic(echoAutopan: EchoAutopanBox): Unit =
    val alphaSlider = echoAutopan.alphaSlider
    val delaySlider = echoAutopan.delaySlider
    val update = () => updateEcho(alphaSlider.sliderValue, delaySlider.sliderValue.toInt)
    alphaSlider.sliderListener(_ => update())
    delaySlider.sliderListener(_ => update())
    echoAutopan.autopanSlider.sliderListener(v => controller.addEffect(AudioEffect.autopan(v)))

  /*
   * Defines how to treat the echo effect when one of
   * the two parameters, alpha or delay, changes.
   */
  private def updateEcho(alpha: Float, delay: Int): Unit =
    controller.addEffect(AudioEffect.echo(alpha, delay))