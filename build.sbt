import sbt.Keys.mainClass
import sbtassembly.AssemblyPlugin.autoImport.assemblyMergeStrategy

lazy val root = project
  .in(file("."))
  .settings(
    name := "vertebrae",
    version := "0.1.0",

    scalaVersion := "2.13.6",

    resolvers ++= Seq(
      "Artima Maven Repository" at "https://repo.artima.com/releases",
      Resolver.sonatypeRepo("snapshots")
    ),

    libraryDependencies ++= Seq(
      "ch.unibas.cs.gravis" % "scalismo-native-all" % "4.0.0",
      "ch.unibas.cs.gravis" %% "scalismo-ui" % "develop-644894a3f93b9c61203c6d6c85b5d4e408a33317-SNAPSHOT",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4",
      "ch.qos.logback" % "logback-classic" % "1.2.3"
    ),

    dependencyOverrides += ("ch.unibas.cs.gravis" %% "scalismo" % "develop-820c713d0aadf855e5a3c9b9d0c43266d8600c33-SNAPSHOT"),

    assemblyJarName in assembly := "vertebrae-registration.jar",

    mainClass in assembly := Some("ui.CorrespondenceTracker"),

    assemblyMergeStrategy in assembly := {
          case PathList("META-INF", "MANIFEST.MF")                                                      => MergeStrategy.discard
          case PathList("META-INF", s) if s.endsWith(".SF") || s.endsWith(".DSA") || s.endsWith(".RSA") => MergeStrategy.discard
          case _                                                                                        => MergeStrategy.first
    }

  )
