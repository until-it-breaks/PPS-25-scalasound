package scalasound.view

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import scalafx.application.JFXApp3
import scalafx.application.JFXApp3.PrimaryStage
import scalafx.scene.Scene
import scalafx.scene.control.Slider
import scalafx.scene.layout.BorderPane
import scalasound.controller.IOController
import scalasound.view.loadsave.FxLoadSaveView
import scalasound.view.mainPanel.Panels

/** The main window of the application.
 *
 *  @param controller performs the import and export requests made through the buttons
 *  @param loadSaveView holds what the buttons and the labels show about import and export
 *  @param started run once the window has been created, i.e. when the GUI is ready to be updated
 */
class ViewApp(
               controller: IOController,
               loadSaveView: FxLoadSaveView,
               started: IO[Unit]
             )(using IORuntime) extends JFXApp3:

  private val Width: Int = 1200
  private val Height: Int = 800
  private lazy val timeCursor = new Slider { max = 0; prefWidth = 500 }

  override def start(): Unit =
    loadSaveView.trackDurationSeconds.onChange { (_, _, duration) =>
      timeCursor.max = duration.doubleValue
      timeCursor.value = 0
    }

    stage = new PrimaryStage:
      title = "ScalaSound"
      width = Width
      height = Height

      scene = new Scene:
        root = new BorderPane:
          top = Panels.titlePane(stage, controller, loadSaveView)
          center = Panels.trackBox(stage, timeCursor)
          bottom = Panels.effectPane(stage)

    started.unsafeRunAndForget()
