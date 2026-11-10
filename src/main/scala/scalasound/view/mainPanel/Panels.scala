package scalasound.view.mainPanel

import cats.effect.unsafe.IORuntime
import scalafx.application.JFXApp3.PrimaryStage
import scalafx.scene.control.Slider
import scalasound.controller.IOController
import scalasound.view.loadsave.FxLoadSaveView

object Panels:
  
  def titlePane(stage: PrimaryStage, controller: IOController, view: FxLoadSaveView)
               (using IORuntime): TitlePane =
    new TitlePane(stage, controller, view)

  def trackBox(stage: PrimaryStage, timeCursor: Slider)(using IORuntime): TrackBox =
    new TrackBox(stage, timeCursor)
    
  def effectPane(stage: PrimaryStage): EffectPane =
    new EffectPane(stage)
