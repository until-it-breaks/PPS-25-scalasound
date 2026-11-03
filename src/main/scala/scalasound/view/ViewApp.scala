package scalasound.view

import scalafx.application.JFXApp3
import scalafx.application.JFXApp3.PrimaryStage
import scalafx.scene.Scene
import scalafx.scene.control.Slider
import scalafx.scene.layout.BorderPane
import scalasound.view.mainPanel.Panels

// TODO: Aggiungere Documentazione
object ViewApp extends JFXApp3:

  private val Width: Int = 1200
  private val Height: Int = 800
  private lazy val timeCursor = new Slider { max = 0; prefWidth = 500 }

  override def start(): Unit =
    stage = new PrimaryStage:
      title = "ScalaSound"
      width = Width
      height = Height

      scene = new Scene:
        root = new BorderPane:
          top = Panels.titlePane(stage)
          center = Panels.trackBox(stage, timeCursor)
          bottom = Panels.effectPane(stage)