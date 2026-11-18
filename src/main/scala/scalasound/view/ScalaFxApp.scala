package scalasound.view

import scalafx.application.JFXApp3
import scalafx.application.JFXApp3.PrimaryStage
import scalafx.scene.Scene
import scalafx.scene.layout.BorderPane
import scalasound.view.panels.Panels

class ScalaFxApp(dependencies: AppDependencies, onReady: () => Unit) extends JFXApp3:

  override def start(): Unit =
    val trackBox = Panels.trackBox()
    dependencies.trackBoxLogic.attach(trackBox)
    val effectsPanel = Panels.effectsPanel()
    dependencies.effectsLogic.attach(effectsPanel)

    stage = new PrimaryStage:
      title = "ScalaSound"
      scene = new Scene:
        root = new BorderPane:
          center = trackBox
          top =
            Panels.titlePane(stage, dependencies.loadSaveController, dependencies.loadSaveControls)
          bottom = effectsPanel

    onReady()
