package scalasound.gui

import scalafx.application.JFXApp3
import scalafx.application.JFXApp3.PrimaryStage
import scalafx.beans.property.{DoubleProperty, StringProperty}
import scalafx.event.ActionEvent
import scalafx.geometry.{Insets, Orientation, Pos}
import scalafx.scene.{Cursor, Scene}
import scalafx.scene.control.{Button, Label, Slider, ToggleButton}
import scalafx.scene.effect.{DropShadow, Light, Lighting, Reflection}
import scalafx.scene.layout.{BorderPane, HBox, StackPane, VBox}
import scalafx.scene.media.AudioClip
import scalafx.scene.paint.Color.*
import scalafx.scene.paint.{Color, LinearGradient, Stops}
import scalafx.scene.shape.{Circle, Line}
import scalafx.scene.text.Text
import scalafx.stage.FileChooser

import java.beans.EventHandler
import java.io.File
import scala.math.{atan2, toDegrees}

// TODO: Aggiungere Documentazione
object ViewApp extends JFXApp3:

  private val Width: Int = 1200
  private val Height: Int = 800
  private val BackgroundColor = "#4287f5"
  private lazy val timeCursor = new Slider { max = 0; prefWidth = 500 }

  override def start(): Unit =
    stage = new PrimaryStage:
      title = "ScalaSound"
      width = Width
      height = Height

      scene = new Scene {
        root = new BorderPane {
          top = titlePane(stage)
          center = trackBox(stage)
          bottom = effectPane(stage)
        }
      }

  /*
   * Container for the upper panel of the GUI,
   * containing the Title and the Import button.
   */
  private def titlePane(stage: PrimaryStage): BorderPane =
    new BorderPane {
      padding = Insets(25)
      style = s"-fx-background-color: ${BackgroundColor};"

      private val titleLabel = new Label("ScalaSound") {
        style = """
            -fx-text-fill: #ecf0f1;
            -fx-font-size: 30px;
            -fx-font-weight: bold;
          """
      }

      private val importButton = new Button("Import") {
        cursor = Cursor.Hand
        padding = Insets(5, 10, 5, 10)
        style = """
            -fx-background-color: white;
            -fx-text-fill: black;
            -fx-font-size: 16px;
            -fx-font-weight: bold;
            -fx-border-color: black;
            -fx-border-width: 2px;
            -fx-border-radius: 5px;
            -fx-background-radius: 5px;
            -fx-alignment: center;
        """
        onAction = _ => {
          val chooser = new FileChooser {
            title = "Choose .wav file"
            extensionFilters.add(new FileChooser.ExtensionFilter("Audio Files", "*.wav"))
          }
          val selectedFile: File = chooser.showOpenDialog(stage)
          if (selectedFile != null) {
            // TODO: Complete
            println("File" + selectedFile.getName)
          }
        }
      }
      left = titleLabel
      right = importButton
      BorderPane.setAlignment(titleLabel, Pos.CenterLeft)
      BorderPane.setAlignment(importButton, Pos.CenterRight)
    }

  /*
   * Container for the track and for the buttons that
   * manage the track itself (Restart, Stop, Play, Save).
   */
  private def trackBox(stage: PrimaryStage): VBox = {

    /* Box containing the slider for the track. */
    def audioBox: HBox = {
      new HBox {
        alignment = Pos.Center
        spacing = 10

        private def audioLabel: Label =
          new Label("-:--") {
            style = """
              -fx-font-size: 20px;
            """
          }

        private val startTimeLabel = audioLabel
        private val endTimeLabel = audioLabel

        private def formatTime(value: Int): String =
          val minute = value / 60
          val seconds = value % 60
          f"$minute%d:$seconds%02d"

        startTimeLabel.text <== timeCursor.value.map(v => formatTime(v.intValue()))
        endTimeLabel.text <== timeCursor.max.map(m => formatTime(m.intValue()))

        children = Seq(startTimeLabel, timeCursor, endTimeLabel)
      }
    }

    /* Box containing the buttons to manage the track. */
    def audioButtonsBox: HBox = {
      new HBox {
        alignment = Pos.Center
        spacing = 10

        private def createAudioButton(text: String): Button =
          new Button(text) {
            cursor = Cursor.Hand
            //padding = Insets(5, 10, 5, 10)
            prefWidth = 70
            prefHeight = 40
            style = """
              -fx-background-color: #3c6382;
              -fx-text-fill: white;
              -fx-font-size: 20px;
              -fx-font-weight: bold;
              -fx-border-color: #60a3bc;
              -fx-border-width: 2px;
              -fx-border-radius: 5px;
              -fx-background-radius: 5px;
              -fx-alignment: center;
            """
          }

        private val restartButton = createAudioButton("◀◀")
        restartButton.onAction = _ => println("Restart")

        private val stopButton = createAudioButton("■")
        stopButton.onAction = _ => println("Stop")

        private val playButton = createAudioButton("▶")
        playButton.onAction = _ => println("Play")

        private val saveButton = createAudioButton("\uD83D\uDCBE")
        saveButton.onAction = _ => println("Save")

        children = Seq(
          restartButton,
          stopButton,
          playButton,
          saveButton)
      }
    }

    new VBox {
      alignment = Pos.Center
      spacing = 30
      children = Seq(audioBox, audioButtonsBox)
    }
  }

  /* Container for all the effects that can be applied on the current track. */
  private def effectPane(stage: PrimaryStage): BorderPane = {

    /* Box for the Volume-Gain effects. */
    def volumeGainBox: HBox = {

      def container(text: String, minVal: Float, maxVal: Float, defaultVal: Float): VBox = {
        new VBox {
          alignment = Pos.Center
          spacing = 10

          private val slider: Slider =
            new Slider {
              min = minVal
              max = maxVal
              value = defaultVal
              orientation = Orientation.Vertical
              prefHeight = 300
              prefWidth = 20
            }

          private def label(label: String): Label =
            new Label(label) {
              style =
                """
                    -fx-font-size: 16px;
                  """
            }

          private val valueLabel = label(defaultVal.toString)
          slider.value.onChange { (_, _, newValue) =>
            valueLabel.text = f"${newValue.floatValue()}%.2f"
          }

          children = Seq(label(text), slider, valueLabel)
        }
      }

      new HBox {
        private val gain = container("Gain", 0, 0, 0) // TODO: CAPIRE COME COLLEGARLI AI COMPANION OBJECT
        private val volume = container("Volume", 0, 0, 0)
        alignment = Pos.Center
        padding = Insets(10, 20, 10, 20)
        spacing = 20
        style = """
          -fx-background-color: white;
          -fx-text-fill: black;
          -fx-font-size: 16px;
          -fx-font-weight: bold;
          -fx-border-color: black;
          -fx-border-width: 2px;
          -fx-border-radius: 5px;
          -fx-background-radius: 5px;
          -fx-alignment: center;
        """
        children = Seq(gain, volume)
      }
    }

    /* Box for the Distortion effect. */
    def distortionBox: VBox = {

      /* Simplifies the management of the switch. */
      enum DistortionEnum(val text: String):
        case Off extends DistortionEnum("OFF")
        case Tanh extends DistortionEnum("Tanh")
        case Sin extends DistortionEnum("Sin")
      
      def container(minVal: Float, maxVal: Float, defaultVal: Float): HBox = {
        new HBox {
          alignment = Pos.Center
          spacing = 20

          private def slider: Slider =
            new Slider {
              min = minVal
              max = maxVal
              value = defaultVal
              prefWidth = 200
            }

          private def label(label: String): Label =
            new Label(label) {
              style =
                """
                  -fx-font-size: 14px;
                """
            }

          private val valueLabel = label(defaultVal.toString)
          valueLabel.text <== slider.value.map(v => f"${v.floatValue()}%.2f")

          children = Seq(valueLabel, slider, label(maxVal.toString))
        }
      }

      def switch(default: DistortionEnum, tanh: DistortionEnum, sin: DistortionEnum): HBox = {
        new HBox {
          alignment = Pos.Center

          private def styleButton(isSelected: Boolean): String =
            val bkg = if isSelected then BackgroundColor else "gray"
            val frg = if isSelected then "white" else "#bdc3c7"
            s"""
              -fx-background-color: $bkg; 
              -fx-text-fill: $frg;
              -fx-font-size: 12px;
              -fx-font-weight: bold;
              -fx-padding: 6px 15px 6px 15px;
            """

          private val borderLeft = """
            -fx-background-radius: 15px 0px 0px 15px;
            -fx-border-radius: 15px 0px 0px 15px;
          """

          private val borderRight = """
            -fx-background-radius: 0px 15px 15px 0px;
            -fx-border-radius: 0px 15px 15px 0px;
          """

          private def getBorder(distType: String): String = distType match {
            case DistortionEnum.Tanh.text => borderLeft
            case DistortionEnum.Sin.text => borderRight
            case _ => ""
          }

          private def createButtonSwitch(distType: DistortionEnum, isSelected: Boolean = false): ToggleButton =
            new ToggleButton(distType.text) {
              cursor = Cursor.Hand
              selected = isSelected
              style = styleButton(isSelected) + getBorder(distType.text)
            }

          private val defaultButton = createButtonSwitch(default, true)
          private val tanhButton = createButtonSwitch(tanh)
          private val sinButton = createButtonSwitch(sin)
          private val buttons: Seq[ToggleButton] = Seq(tanhButton, defaultButton, sinButton)

          tanhButton.style.value += borderLeft
          sinButton.style.value += borderRight
          buttons.foreach { btn =>
            btn.selected.onChange { (_, _, isSelected) =>
              if (isSelected) {
                buttons.filter(_ != btn).foreach(ntb => {
                  ntb.selected = false
                  ntb.style = styleButton(false) + getBorder(ntb.text.value)
                })
                btn.style = styleButton(true) + getBorder(btn.text.value)
                println(btn.text.value)
              }
            }
          }

          children = buttons
        }
      }

      new VBox {
        private val distortionContainer = container(0, 0, 0)
        private val distortionSwitch = switch(DistortionEnum.Off,
          DistortionEnum.Tanh, DistortionEnum.Sin)
        private val distortionLabel = new Label("Distortion") {
          style = """
            -fx-font-size: 16px;
          """
        }
        alignment = Pos.Center
        padding = Insets(10, 20, 10, 20)
        spacing = 10
        style = """
          -fx-background-color: white;
          -fx-border-color: black;
          -fx-border-width: 2px;
          -fx-border-radius: 5px;
          -fx-background-radius: 5px;
          -fx-alignment: center;
        """
        children = Seq(distortionLabel, distortionContainer, distortionSwitch)
      }
    }

    new BorderPane {
      padding = Insets(20)
      style = "-fx-background-color: #4287f5;"
      right = volumeGainBox
      left = distortionBox
    }
  }

end ViewApp
