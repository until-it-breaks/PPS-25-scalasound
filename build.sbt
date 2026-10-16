scalaVersion := "3.8.4"

lazy val root = rootProject
  .settings(
    name := "PPS-25-scalasound",
    mainClass := Some("scalasound.main"),
    assembly / assemblyJarName := "app.jar",
    assembly / assemblyOutputPath := file("target/app.jar"),
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.20" % Test,
    )
  )
