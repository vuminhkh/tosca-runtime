import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport._
import sbt.Keys._

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

val buildResolvers = Seq(
  "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository",
  "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",
  "Typesafe Ivy repository" at "http://repo.typesafe.com/typesafe/ivy-releases/"
)

lazy val root = project.in(file(".")).settings(
  organization := "com.mkv.tosca",
  name := "tosca-runtime-parent",
  version := "1.0-SNAPSHOT",
  crossPaths := false,
  scalaVersion := "2.11.7",
  resolvers ++= buildResolvers,
  stage <<= stage dependsOn publishLocal,
  dist <<= dist dependsOn stage
).aggregate(deployer, test, runtime, compiler, docker, openstack, sdk, common, cli).enablePlugins(UniversalPlugin)

lazy val compiler = project.settings(
  organization := "com.mkv.tosca",
  name := "compiler",
  crossPaths := false,
  version := "1.0-SNAPSHOT",
  scalaVersion := "2.11.7",
  resolvers ++= buildResolvers,
  libraryDependencies += "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4",
  libraryDependencies += "com.typesafe" % "config" % "1.3.0",
  stage <<= stage dependsOn publishLocal,
  dist <<= dist dependsOn stage
).dependsOn(sdk).enablePlugins(SbtTwirl, UniversalPlugin)

lazy val runtime = project.settings(
  organization := "com.mkv.tosca",
  name := "runtime",
  crossPaths := false,
  version := "1.0-SNAPSHOT",
  scalaVersion := "2.11.7",
  resolvers ++= buildResolvers,
  libraryDependencies += "org.abstractmeta" % "compilation-toolbox" % "0.3.3",
  stage <<= stage dependsOn publishLocal,
  dist <<= dist dependsOn stage
).dependsOn(compiler).enablePlugins(UniversalPlugin)

lazy val deployer = project.settings(
  organization := "com.mkv.tosca",
  name := "deployer",
  crossPaths := false,
  version := "1.0-SNAPSHOT",
  scalaVersion := "2.11.7",
  resolvers ++= buildResolvers,
  packageName in Docker := "toscaruntime/deployer",
  version in Docker := "latest",
  dockerExposedPorts in Docker := Seq(9000, 9443),
  stage <<= stage dependsOn(publishLocal, publishLocal in Docker),
  dist <<= dist dependsOn stage
).dependsOn(runtime).enablePlugins(PlayScala, DockerPlugin)

lazy val test = project.settings(
  organization := "com.mkv.tosca",
  name := "test",
  crossPaths := false,
  version := "1.0-SNAPSHOT",
  scalaVersion := "2.11.7",
  resolvers ++= buildResolvers,
  stage <<= stage dependsOn publishLocal,
  dist <<= dist dependsOn stage
).dependsOn(runtime, docker, openstack).enablePlugins(UniversalPlugin)

lazy val docker = project.settings(
  organization := "com.mkv.tosca",
  name := "docker",
  crossPaths := false,
  version := "1.0-SNAPSHOT",
  scalaVersion := "2.11.7",
  resolvers ++= buildResolvers,
  mappings in Universal <++= (packageBin in Compile, baseDirectory) map { (_, base) =>
    val dir = base / "src" / "main"
    dir.*** pair relativeTo(base)
  },
  stage <<= stage dependsOn publishLocal,
  dist <<= dist dependsOn stage
).dependsOn(sdk % "provided").enablePlugins(JavaAppPackaging)

lazy val openstack = project.settings(
  organization := "com.mkv.tosca",
  name := "openstack",
  crossPaths := false,
  version := "1.0-SNAPSHOT",
  scalaVersion := "2.11.7",
  resolvers ++= buildResolvers,
  libraryDependencies += "org.apache.jclouds.driver" % "jclouds-slf4j" % "1.9.1",
  libraryDependencies += "org.apache.jclouds.driver" % "jclouds-sshj" % "1.9.1",
  libraryDependencies += "org.apache.jclouds.api" % "openstack-keystone" % "1.9.1",
  libraryDependencies += "org.apache.jclouds.api" % "openstack-nova" % "1.9.1",
  libraryDependencies += "org.apache.jclouds.api" % "openstack-cinder" % "1.9.1",
  libraryDependencies += "org.apache.jclouds.labs" % "openstack-neutron" % "1.9.1",
  mappings in Universal <++= (packageBin in Compile, baseDirectory) map { (_, base) =>
    val dir = base / "src" / "main"
    dir.*** pair relativeTo(base)
  },
  stage <<= stage dependsOn publishLocal,
  dist <<= dist dependsOn stage
).dependsOn(sdk % "provided").enablePlugins(JavaAppPackaging)

lazy val sdk = project.settings(
  organization := "com.mkv.tosca",
  name := "sdk",
  crossPaths := false,
  version := "1.0-SNAPSHOT",
  scalaVersion := "2.11.7",
  resolvers ++= buildResolvers,
  stage <<= stage dependsOn publishLocal,
  dist <<= dist dependsOn stage
).dependsOn(common).enablePlugins(UniversalPlugin)

lazy val common = project.settings(
  organization := "com.mkv.tosca",
  name := "common",
  crossPaths := false,
  version := "1.0-SNAPSHOT",
  scalaVersion := "2.11.7",
  resolvers ++= buildResolvers,
  libraryDependencies += "org.bouncycastle" % "bcpkix-jdk15on" % "1.52",
  libraryDependencies += "org.apache.sshd" % "sshd-core" % "0.14.0",
  libraryDependencies += "commons-lang" % "commons-lang" % "2.6",
  libraryDependencies += "junit" % "junit" % "4.12" % "test",
  libraryDependencies += "com.google.guava" % "guava" % "18.0",
  libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  libraryDependencies += "org.apache.commons" % "commons-compress" % "1.9",
  libraryDependencies += "org.yaml" % "snakeyaml" % "1.16",
  libraryDependencies += "com.github.docker-java" % "docker-java" % "2.1.2-SNAPSHOT",
  libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.12",
  libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.3",
  stage <<= stage dependsOn publishLocal,
  dist <<= dist dependsOn stage
).enablePlugins(UniversalPlugin)

lazy val downloadSbtLauncher = taskKey[Unit]("Downloads sbt launcher.")

lazy val cli = project.settings(
  organization := "com.mkv.tosca",
  name := "cli",
  crossPaths := false,
  version := "1.0-SNAPSHOT",
  scalaVersion := "2.11.7",
  resolvers ++= buildResolvers,
  libraryDependencies += "org.scala-sbt" % "command" % "0.13.8",
  downloadSbtLauncher := {
    val logFile = target.value / "prepare-stage" / "log" / "cli.log"
    logFile.getParentFile.mkdirs()
    IO.touch(logFile)
    val sbtLaunchTarget = target.value / "prepare-stage" / "bin" / "sbt-launch.jar"
    sbtLaunchTarget.getParentFile.mkdirs()
    IO.download(url("https://repo.typesafe.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/0.13.8/sbt-launch.jar"), sbtLaunchTarget)
    val toscaRuntimeScriptTarget = target.value / "prepare-stage" / "bin" / "tosca-runtime.sh"
    toscaRuntimeScriptTarget.getParentFile.mkdirs()
    IO.copyFile((resourceDirectory in Compile).value / "bin" / "tosca-runtime.sh", toscaRuntimeScriptTarget)
    toscaRuntimeScriptTarget.setExecutable(true)
    val launchConfigTarget = target.value / "prepare-stage" / "conf" / "launchConfig"
    launchConfigTarget.getParentFile.mkdirs()
    val launchConfigSource = (resourceDirectory in Compile).value / "conf" / "launchConfig.template"
    var launchConfigTemplateText = IO.read(launchConfigSource)
    launchConfigTemplateText = launchConfigTemplateText.replaceFirst("@organization", organization.value)
    launchConfigTemplateText = launchConfigTemplateText.replaceFirst("@name", name.value)
    launchConfigTemplateText = launchConfigTemplateText.replaceFirst("@version", version.value)
    IO.write(launchConfigTarget, launchConfigTemplateText)
    val command = "java -jar " + sbtLaunchTarget.toString + " @" + launchConfigTarget
    Process(command, Some(sbtLaunchTarget.getParentFile)) ! match {
      case 0 => println("Successfully loaded cli dependencies")
      case n => sys.error(s"Could not load cli dependencies, exit code: $n")
    }
  },
  mappings in Universal <++= (packageBin in Compile, baseDirectory) map { (_, base) =>
    val dir = base / "target" / "prepare-stage"
    val allPaths = dir.**(new FileFilter {
      override def accept(pathname: File): Boolean = pathname.isFile
    })
    allPaths pair relativeTo(dir)
  },
  downloadSbtLauncher <<= downloadSbtLauncher dependsOn publishLocal,
  stage <<= (stage in Universal) dependsOn downloadSbtLauncher,
  dist <<= (packageZipTarball in Universal) dependsOn stage
).dependsOn(runtime).enablePlugins(UniversalPlugin)