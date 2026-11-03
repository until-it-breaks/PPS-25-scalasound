scalaVersion := "3.9.0"

lazy val root = rootProject
  .settings(
    name := "PPS-25-scalasound",
    mainClass := Some("scalasound.Main"),
    assembly / assemblyJarName := "app.jar",
    assembly / assemblyOutputPath := file("target/app.jar"),
    assembly / assemblyMergeStrategy := {
      case "module-info.class" => MergeStrategy.discard
      case path =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(path)
    },
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.20" % Test,
      "org.scalameta" %% "munit" % "1.3.6" % Test,
      "org.typelevel" %% "munit-cats-effect" % "2.2.1" % Test,
      "org.typelevel" %% "cats-effect" % "3.7.1",
      "co.fs2" %% "fs2-core" % "3.14.0",
      "org.scalafx" %% "scalafx" % "27.0.0-R39"
    )
  )
