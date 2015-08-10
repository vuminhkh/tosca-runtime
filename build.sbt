name := "tosca-runtime"

lazy val root = project.in(file(".")).settings(
  version := "1.0",
  scalaVersion := "2.11.6"
).aggregate(compiler, docker, sdk, common)

lazy val compiler = project.settings(
  version := "1.0",
  scalaVersion := "2.11.6",
  libraryDependencies += "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4",
  libraryDependencies += "org.abstractmeta" % "compilation-toolbox" % "0.3.3"
).dependsOn(docker).enablePlugins(SbtTwirl)

lazy val docker = project.settings(
  version := "1.0",
  scalaVersion := "2.11.6",
  libraryDependencies += "com.github.docker-java" % "docker-java" % "1.4.0"
).dependsOn(sdk)

lazy val sdk = project.settings(
  version := "1.0",
  scalaVersion := "2.11.6"
).dependsOn(common)

lazy val common = project.settings(
  version := "1.0",
  scalaVersion := "2.11.6",
  libraryDependencies += "org.bouncycastle" % "bcpkix-jdk15on" % "1.52",
  libraryDependencies += "org.apache.sshd" % "sshd-core" % "0.14.0",
  libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.12",
  libraryDependencies += "org.slf4j" % "slf4j-log4j12" % "1.7.12",
  libraryDependencies += "log4j" % "log4j" % "1.2.17",
  libraryDependencies += "commons-lang" % "commons-lang" % "2.6",
  libraryDependencies += "junit" % "junit" % "4.12" % "test",
  libraryDependencies += "com.google.guava" % "guava" % "18.0",
  libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0"
)