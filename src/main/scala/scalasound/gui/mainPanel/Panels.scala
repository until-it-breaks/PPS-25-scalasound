package scalasound.gui.mainPanel

import scalafx.application.JFXApp3.PrimaryStage
import scalafx.scene.control.Slider

object Panels:
  
  def titlePane(stage: PrimaryStage): TitlePane = 
    new TitlePane(stage)

  def trackBox(stage: PrimaryStage, timeCursor: Slider): TrackBox = 
    new TrackBox(stage, timeCursor)
    
  def effectPane(stage: PrimaryStage): EffectPane =
    new EffectPane(stage)
  
  