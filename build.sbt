scalaVersion := "3.9.0"

val javaFxVersion   = "27"
val javaFxPlatforms = Seq("win", "linux", "mac-aarch64")
val javaFxModules   = Seq("base", "controls", "graphics")

lazy val root = rootProject
  .settings(
    name := "PPS-25-scalasound",
    mainClass := Some("scalasound.Main"),
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Wunused:all"
    ),
    coverageExcludedPackages := """scalasound\.(?!model|controller).*""",
    coverageDataDir := (Compile / classDirectory).value,
    assembly / assemblyJarName := "scalasound.jar",
    assembly / assemblyOutputPath := file("target/scalasound.jar"),
    assembly / assemblyMergeStrategy := {
      case "module-info.class" => MergeStrategy.discard
      case "javafx-swt.jar" => MergeStrategy.discard
      case path if path.endsWith(".bss") || path == "com/sun/javafx/runtime/VersionInfo.class" =>
        MergeStrategy.first
      case path =>
        val oldStrategy = (assembly / assemblyMergeStrategy).value
        oldStrategy(path)
    },
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.2.20" % Test,
      "org.scalameta" %% "munit" % "1.3.6" % Test,
      "org.typelevel" %% "munit-cats-effect" % "2.2.1" % Test,
      "org.typelevel" %% "cats-effect" % "3.7.1",
      "co.fs2" %% "fs2-core" % "3.16.0",
      "org.scalafx" % "scalafx_3" % "27.0.0-R39"
    ),
    libraryDependencies ++= (
      for
        module   <- javaFxModules
        platform <- javaFxPlatforms
      yield ("org.openjfx" % s"javafx-$module" % javaFxVersion).classifier(platform)
    ),
    excludeDependencies ++= Seq("javafx-fxml", "javafx-media", "javafx-swing", "javafx-web", "jdk-jsobject")
      .map(ExclusionRule("org.openjfx", _))
  )
