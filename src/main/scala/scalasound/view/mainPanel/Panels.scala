package scalasound.view.mainPanel

import scalafx.application.JFXApp3.PrimaryStage
import scalafx.scene.control.Slider
import scalasound.controller.LoadSaveController
import scalasound.view.loadsave.LoadSaveControls

object Panels:

  def titlePane(
                 stage: PrimaryStage,
                 loadSaveController: LoadSaveController,
                 loadSaveControls: LoadSaveControls
               ): TitlePane =
    new TitlePane(stage, loadSaveController, loadSaveControls)

  def trackBox(stage: PrimaryStage, timeCursor: Slider): TrackBox =
    new TrackBox(stage, timeCursor)

  def effectPane(stage: PrimaryStage): EffectPane =
    new EffectPane(stage)
