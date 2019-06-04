organization := "one.shn"
name         := "dog"
version      := "1.0"

scalaVersion := "2.12.8"
scalacOptions ++= "-feature" :: "-deprecation" :: "-Ypartial-unification" :: Nil

val root = project in file(".")

val fs2 = "fs2-core" :: "fs2-io" :: Nil map ("co.fs2" %% _ % "1.0.4")
val scalaTest = "org.scalatest" % "scalatest_2.12" % "3.0.5" % Test :: Nil
libraryDependencies ++= fs2 ++ scalaTest

enablePlugins(JavaAppPackaging)
enablePlugins(DockerPlugin)

mainClass in Compile := Some("one.shn.dog.AccessLogMonitor")