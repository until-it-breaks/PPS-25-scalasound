package scalasound.view

import scalafx.application.JFXApp3
import scalafx.application.JFXApp3.PrimaryStage
import scalafx.scene.Scene
import scalafx.scene.control.Slider
import scalafx.scene.layout.BorderPane
import scalasound.controller.LoadSaveController
import scalasound.view.loadsave.LoadSaveControls
import scalasound.view.mainPanel.Panels

// TODO: Add ScalaDoc comments to this class.
class ViewApp(
               loadSaveController: LoadSaveController,
               loadSaveControls: LoadSaveControls,
               onReady: () => Unit
             ) extends JFXApp3:

  private val Width: Int = 1200
  private val Height: Int = 800
  private lazy val timeCursor = new Slider { max = 0; prefWidth = 500 }

  override def start(): Unit =
    stage = new PrimaryStage:
      title = "ScalaSound"
      width = Width
      height = Height

      scene = new Scene:
        root = BorderPane(
          center = Panels.trackBox(stage, timeCursor),
          top = Panels.titlePane(stage, loadSaveController, loadSaveControls),
          bottom = Panels.effectPane(stage),
          left = null,
          right = null
        )

    onReady()