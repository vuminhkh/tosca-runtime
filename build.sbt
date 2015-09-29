import WebKeys._

organization := "com.mkv"
name := "tosca-runtime"

scalacOptions ++= Seq(
  "-target:jvm-1.8",
  "-encoding", "UTF-8",
  "-deprecation", // warning and location for usages of deprecated APIs
  "-feature", // warning and location for usages of features that should be imported explicitly
  "-unchecked", // additional warnings where generated code depends on assumptions
  "-Xlint", // recommended additional warnings
  "-Xcheckinit", // runtime error when a val is not initialized due to trait hierarchies (instead of NPE somewhere else)
  "-Ywarn-adapted-args", // Warn if an argument list is modified to match the receiver
  "-Ywarn-value-discard", // Warn when non-Unit expression results are unused
  "-Ywarn-inaccessible",
  "-Ywarn-dead-code"
)

emojiLogs

lazy val root = project.in(file(".")).settings(
  version := "1.0",
  scalaVersion := "2.11.7"
).aggregate(deployer, test, runtime, compiler, docker, openstack, sdk, common)

lazy val compiler = project.settings(
  version := "1.0",
  scalaVersion := "2.11.7",
  libraryDependencies += "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4"
).dependsOn(sdk).enablePlugins(SbtTwirl)

lazy val runtime = project.settings(
  version := "1.0",
  scalaVersion := "2.11.7",
  libraryDependencies += "org.abstractmeta" % "compilation-toolbox" % "0.3.3"
).dependsOn(compiler)

lazy val deployer = project.settings(
  version := "1.0",
  scalaVersion := "2.11.7",
  net.virtualvoid.sbt.graph.Plugin.graphSettings
).dependsOn(runtime).enablePlugins(PlayScala)

lazy val test = project.settings(
  version := "1.0",
  scalaVersion := "2.11.7",
  libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.12",
  libraryDependencies += "org.slf4j" % "slf4j-log4j12" % "1.7.12",
  libraryDependencies += "log4j" % "log4j" % "1.2.17"
).dependsOn(runtime, docker)

lazy val docker = project.settings(
  version := "1.0",
  scalaVersion := "2.11.7",
  libraryDependencies += "com.github.docker-java" % "docker-java" % "2.0.1",
  mappings in Universal <++= (packageBin in Compile, baseDirectory) map { (_, base) =>
    val dir = base / "src" / "main"
    dir.*** pair relativeTo(base)
  }
).dependsOn(sdk % "provided").enablePlugins(JavaAppPackaging)

lazy val openstack = project.settings(
  version := "1.0",
  scalaVersion := "2.11.7",
  libraryDependencies += "org.apache.jclouds.driver" % "jclouds-slf4j" % "1.9.1",
  libraryDependencies += "org.apache.jclouds.driver" % "jclouds-sshj" % "1.9.1",
  libraryDependencies += "org.apache.jclouds.api" % "openstack-keystone" % "1.9.1",
  libraryDependencies += "org.apache.jclouds.api" % "openstack-nova" % "1.9.1",
  libraryDependencies += "org.apache.jclouds.api" % "openstack-cinder" % "1.9.1",
  libraryDependencies += "org.apache.jclouds.labs" % "openstack-neutron" % "1.9.1",
  mappings in Universal <++= (packageBin in Compile, baseDirectory) map { (_, base) =>
    val dir = base / "src" / "main"
    dir.*** pair relativeTo(base)
  }
).dependsOn(sdk % "provided").enablePlugins(JavaAppPackaging)

lazy val sdk = project.settings(
  version := "1.0",
  scalaVersion := "2.11.7"
).dependsOn(common)

lazy val common = project.settings(
  version := "1.0",
  scalaVersion := "2.11.7",
  libraryDependencies += "org.bouncycastle" % "bcpkix-jdk15on" % "1.52",
  libraryDependencies += "org.apache.sshd" % "sshd-core" % "0.14.0",
  libraryDependencies += "commons-lang" % "commons-lang" % "2.6",
  libraryDependencies += "junit" % "junit" % "4.12" % "test",
  libraryDependencies += "com.google.guava" % "guava" % "18.0",
  libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  libraryDependencies += "org.apache.commons" % "commons-compress" % "1.9",
  libraryDependencies += "org.yaml" % "snakeyaml" % "1.16"
)