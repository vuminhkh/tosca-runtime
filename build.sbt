name := "tosca-runtime"

lazy val root = project.in(file(".")).settings(
  version := "1.0"
).aggregate(compiler, docker, sdk, common)

lazy val compiler = project.settings(
  version := "1.0",
  scalaVersion := "2.11.6",
  libraryDependencies += "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4"
).dependsOn(docker)

lazy val docker = project.settings(
  version := "1.0",
  scalaVersion := "2.11.6",
  libraryDependencies += "com.github.docker-java" % "docker-java" % "1.4.0"
).dependsOn(sdk, common)

lazy val sdk = project.settings(
  version := "1.0",
  scalaVersion := "2.11.6",
  libraryDependencies += "org.springframework" % "spring-core" % "4.1.7.RELEASE",
  libraryDependencies += "org.springframework" % "spring-context" % "4.1.7.RELEASE",
  libraryDependencies += "org.springframework" % "spring-beans" % "4.1.7.RELEASE"
)

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
  libraryDependencies += "com.google.guava" % "guava" % "18.0"
)