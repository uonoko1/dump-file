ThisBuild / scalaVersion := "2.13.18"
ThisBuild / organization := "example"

lazy val root = (project in file("."))
  .settings(
    name := "heap-demo",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % "10.5.3",
      "com.typesafe.akka" %% "akka-stream" % "2.8.5",
      "com.typesafe.akka" %% "akka-actor-typed" % "2.8.5",
      "ch.qos.logback" % "logback-classic" % "1.4.11"
    )
  )
