import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport._
import sbt.Keys._

emojiLogs

val commonSettings: Seq[Setting[_]] = Seq(
  organization := "com.toscaruntime",
  version := "1.0-SNAPSHOT",
  crossPaths := false,
  scalaVersion := "2.11.7",
  javacOptions ++= Seq(
    "-source", "1.8",
    "-target", "1.8"
  ),
  javacOptions in doc := Seq("-source", "1.8"),
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
  ),
  resolvers ++= Seq(
    "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository",
    "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",
    "Typesafe Ivy repository" at "http://repo.typesafe.com/typesafe/ivy-releases/"
  ),
  stage <<= stage dependsOn publishLocal,
  dist <<= dist dependsOn stage
) ++ net.virtualvoid.sbt.graph.Plugin.graphSettings

lazy val root = project.in(file("."))
  .settings(commonSettings: _*)
  .settings(
    name := "tosca-runtime-parent"
  ).aggregate(deployer, proxy, rest, test, runtime, compiler, docker, openstack, sdk, common, cli).enablePlugins(UniversalPlugin)

val commonDependencies: Seq[ModuleID] = Seq(
  "commons-lang" % "commons-lang" % "2.6",
  "junit" % "junit" % "4.12" % "test",
  "org.slf4j" % "slf4j-api" % "1.7.12",
  "ch.qos.logback" % "logback-classic" % "1.1.3",
  "com.google.guava" % "guava" % "18.0"
)

lazy val common = project
  .settings(commonSettings: _*)
  .settings(
    name := "common"
  ).aggregate(sshUtil, dockerUtil, fileUtil, miscUtil, constant, exception).enablePlugins(UniversalPlugin)

lazy val constant = project.in(file("common/constant"))
  .settings(commonSettings: _*)
  .settings(
    name := "constant"
  ).enablePlugins(UniversalPlugin)

lazy val exception = project.in(file("common/exception"))
  .settings(commonSettings: _*)
  .settings(
    name := "exception"
  ).enablePlugins(UniversalPlugin)

lazy val miscUtil = project.in(file("common/misc-util"))
  .settings(commonSettings: _*)
  .settings(
    name := "misc-util",
    libraryDependencies ++= commonDependencies
  ).dependsOn(exception).enablePlugins(UniversalPlugin)

lazy val sshUtil = project.in(file("common/ssh-util"))
  .settings(commonSettings: _*)
  .settings(
    name := "ssh-util",
    libraryDependencies ++= commonDependencies,
    libraryDependencies += "org.bouncycastle" % "bcpkix-jdk15on" % "1.52",
    libraryDependencies += "org.apache.sshd" % "sshd-core" % "1.0.0"
  ).dependsOn(exception).enablePlugins(UniversalPlugin)

lazy val dockerUtil = project.in(file("common/docker-util"))
  .settings(commonSettings: _*)
  .settings(
    name := "docker-util",
    libraryDependencies ++= commonDependencies,
    libraryDependencies += "com.github.docker-java" % "docker-java" % "3.0.0-SNAPSHOT" exclude("org.glassfish.hk2", "hk2-api") exclude("org.glassfish.hk2.external", "javax.inject") exclude("org.glassfish.hk2", "hk2-locator"),
    libraryDependencies += "org.glassfish.hk2" % "hk2-api" % "2.4.0-b32",
    libraryDependencies += "org.glassfish.hk2.external" % "javax.inject" % "2.4.0-b32",
    libraryDependencies += "org.glassfish.hk2" % "hk2-locator" % "2.4.0-b32"
  ).dependsOn(exception).enablePlugins(UniversalPlugin)

lazy val fileUtil = project.in(file("common/file-util"))
  .settings(commonSettings: _*)
  .settings(
    name := "file-util",
    libraryDependencies ++= commonDependencies,
    libraryDependencies += "org.apache.commons" % "commons-compress" % "1.9"
  ).dependsOn(exception).enablePlugins(UniversalPlugin)

lazy val rest = project.in(file("rest"))
  .settings(commonSettings: _*)
  .settings(
    name := "rest",
    libraryDependencies += "com.typesafe.play" %% "play-json" % "2.4.2",
    libraryDependencies += "com.typesafe.play" %% "play-ws" % "2.4.2",
    libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
    libraryDependencies += "org.yaml" % "snakeyaml" % "1.16"
  ).dependsOn(dockerUtil, fileUtil, constant, exception).enablePlugins(UniversalPlugin)

lazy val compiler = project
  .settings(commonSettings: _*)
  .settings(
    name := "compiler",
    libraryDependencies += "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4",
    libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
    libraryDependencies += "com.typesafe" % "config" % "1.3.0"
  ).dependsOn(sdk, fileUtil, dockerUtil, miscUtil).enablePlugins(SbtTwirl, UniversalPlugin)

lazy val runtime = project
  .settings(commonSettings: _*)
  .settings(
    name := "runtime",
    libraryDependencies += "org.yaml" % "snakeyaml" % "1.16",
    sources in doc in Compile := List()
  ).dependsOn(sdk).enablePlugins(UniversalPlugin)

lazy val deployer = project
  .settings(commonSettings: _*)
  .settings(
    name := "deployer",
    packageName in Docker := "toscaruntime/deployer",
    version in Docker := "latest",
    dockerExposedPorts in Docker := Seq(9000, 9443),

    stage <<= stage dependsOn(publishLocal, publishLocal in Docker)
  ).dependsOn(runtime, rest).enablePlugins(PlayScala, DockerPlugin)

lazy val proxy = project
  .settings(commonSettings: _*)
  .settings(
    name := "proxy",
    packageName in Docker := "toscaruntime/proxy",
    libraryDependencies += ws,
    routesGenerator := InjectedRoutesGenerator,
    libraryDependencies += cache,
    version in Docker := "latest",
    dockerExposedPorts in Docker := Seq(9000, 9443),
    stage <<= stage dependsOn(publishLocal, publishLocal in Docker)
  ).dependsOn(dockerUtil, rest).enablePlugins(PlayScala, DockerPlugin)

lazy val test = project
  .settings(commonSettings: _*)
  .settings(
    name := "test"
  ).dependsOn(compiler, runtime, docker, openstack).enablePlugins(JavaAppPackaging)

val providerSettings: Seq[Setting[_]] = commonSettings ++ Seq(
  mappings in Universal <++= (packageBin in Compile, baseDirectory) map { (_, base) =>
    val dir = base / "src" / "main"
    dir.*** pair relativeTo(base)
  })

lazy val docker = project
  .settings(providerSettings: _*)
  .settings(
    name := "docker"
  ).dependsOn(sdk, dockerUtil).enablePlugins(JavaAppPackaging)

lazy val openstack = project
  .settings(providerSettings: _*)
  .settings(
    name := "openstack",
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
  ).dependsOn(sdk, sshUtil).enablePlugins(JavaAppPackaging)

lazy val sdk = project
  .settings(providerSettings: _*)
  .settings(
    name := "sdk",
    libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.12",
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.3"
  ).dependsOn(miscUtil, constant, exception).enablePlugins(JavaAppPackaging)

lazy val downloadSbtLauncher = taskKey[Unit]("Downloads sbt launcher.")

lazy val cli = project
  .settings(commonSettings: _*)
  .settings(
    name := "cli",
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
      val dockerProviderTarget = target.value / "prepare-stage" / "providers" / "docker"
      dockerProviderTarget.mkdirs()
      val openstackProviderTarget = target.value / "prepare-stage" / "providers" / "openstack"
      openstackProviderTarget.mkdirs()
      val providerConf = target.value / "prepare-stage" / "conf" / "providers"
      providerConf.mkdirs()
      IO.copyDirectory((resourceDirectory in Compile).value / "conf" / "providers", providerConf)
      val bootstrapConf = target.value / "prepare-stage" / "bootstrap"
      bootstrapConf.mkdirs()
      IO.copyDirectory((resourceDirectory in Compile).value / "bootstrap", bootstrapConf)
      IO.copyDirectory((stage in docker).value, dockerProviderTarget)
      IO.copyDirectory((stage in openstack).value, openstackProviderTarget)
      val sdkTarget = target.value / "prepare-stage" / "sdk"
      sdkTarget.mkdirs()
      IO.copyDirectory((stage in sdk).value / "src" / "main" / "resources", sdkTarget)
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
  ).dependsOn(compiler, runtime, rest).enablePlugins(UniversalPlugin)