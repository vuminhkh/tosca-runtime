import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport._
import sbt.Keys._
import sbtfilter.Plugin.FilterKeys._
import sbt._

emojiLogs

val commonSettings: Seq[Setting[_]] = Seq(
  organization := "com.toscaruntime",
  version := "1.0.0-SNAPSHOT",
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
  parallelExecution in Test := false,
  parallelExecution in IntegrationTest := false,
  parallelExecution in ThisBuild := false,
  fork in Test := false,
  stage <<= stage dependsOn publishLocal,
  dist <<= dist dependsOn stage
) ++ net.virtualvoid.sbt.graph.Plugin.graphSettings

lazy val root = project.in(file("."))
  .settings(commonSettings: _*)
  .settings(
    name := "toscaruntime-parent"
  ).aggregate(deployer, proxy, rest, runtime, compiler, providers, plugins, sdk, common, cli, itTest).enablePlugins(UniversalPlugin)

val testDependencies: Seq[ModuleID] = Seq(
  "junit" % "junit" % "4.12" % Test,
  "com.novocode" % "junit-interface" % "0.11" % Test exclude("junit", "junit-dep"),
  "org.mockito" % "mockito-all" % "1.9.5" % Test
)

val scalaTestDependencies: Seq[ModuleID] = Seq(
  "org.scalatest" % "scalatest_2.11" % "2.2.5" % "test"
)

val commonDependencies: Seq[ModuleID] = Seq(
  "commons-lang" % "commons-lang" % "2.6",
  "org.slf4j" % "slf4j-api" % "1.7.12",
  "ch.qos.logback" % "logback-classic" % "1.1.3"
)

lazy val common = project.in(file("common"))
  .settings(commonSettings: _*)
  .settings(
    name := "toscaruntime-common"
  ).aggregate(sshUtil, dockerUtil, fileUtil, miscUtil, gitUtil, compilationUtil, sharedContracts, restModel).enablePlugins(UniversalPlugin)

lazy val sharedContracts = project.in(file("common/shared-contracts"))
  .settings(commonSettings: _*)
  .settings(
    name := "toscaruntime-shared-contracts",
    libraryDependencies ++= commonDependencies
  ).enablePlugins(UniversalPlugin)

lazy val miscUtil = project.in(file("common/misc-util"))
  .settings(commonSettings: _*)
  .settings(
    name := "toscaruntime-misc-util",
    libraryDependencies ++= commonDependencies,
    libraryDependencies ++= testDependencies,
    libraryDependencies ++= scalaTestDependencies,
    libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % "2.6.4"
  ).dependsOn(sharedContracts).enablePlugins(UniversalPlugin)

lazy val sshUtil = project.in(file("common/ssh-util"))
  .settings(commonSettings: _*)
  .settings(
    name := "toscaruntime-ssh-util",
    libraryDependencies ++= commonDependencies,
    libraryDependencies += "com.hierynomus" % "sshj" % "0.15.0"
  ).dependsOn(sharedContracts % "provided", miscUtil % "provided").enablePlugins(UniversalPlugin)

lazy val dockerUtil = project.in(file("common/docker-util"))
  .settings(commonSettings: _*)
  .settings(
    name := "toscaruntime-docker-util",
    libraryDependencies ++= commonDependencies,
    libraryDependencies ++= testDependencies,
    libraryDependencies += "com.github.docker-java" % "docker-java" % "3.0.0-SNAPSHOT" exclude("org.glassfish.hk2", "hk2-api") exclude("org.glassfish.hk2.external", "javax.inject") exclude("org.glassfish.hk2", "hk2-locator"),
    libraryDependencies += "org.glassfish.hk2" % "hk2-api" % "2.4.0-b32",
    libraryDependencies += "org.glassfish.hk2.external" % "javax.inject" % "2.4.0-b32",
    libraryDependencies += "org.glassfish.hk2" % "hk2-locator" % "2.4.0-b32"
  ).dependsOn(sharedContracts % "provided", miscUtil % "provided").enablePlugins(UniversalPlugin)

lazy val fileUtil = project.in(file("common/file-util"))
  .settings(commonSettings: _*)
  .settings(
    name := "toscaruntime-file-util",
    libraryDependencies ++= commonDependencies,
    libraryDependencies += "org.apache.commons" % "commons-compress" % "1.9"
  ).dependsOn(sharedContracts).enablePlugins(UniversalPlugin)

lazy val compilationUtil = project.in(file("common/compilation-util"))
  .settings(commonSettings: _*)
  .settings(
    name := "toscaruntime-compilation-util",
    libraryDependencies ++= commonDependencies,
    sources in doc in Compile := List()
  ).dependsOn(sharedContracts).enablePlugins(UniversalPlugin)

lazy val gitUtil = project.in(file("common/git-util"))
  .settings(commonSettings: _*)
  .settings(
    name := "toscaruntime-git-util",
    libraryDependencies ++= commonDependencies,
    libraryDependencies += "org.eclipse.jgit" % "org.eclipse.jgit" % "4.1.1.201511131810-r"
  ).dependsOn(sharedContracts).enablePlugins(UniversalPlugin)

lazy val restModel = project.in(file("common/rest-model"))
  .settings(commonSettings: _*)
  .settings(
    name := "toscaruntime-rest-model",
    libraryDependencies ++= testDependencies,
    libraryDependencies ++= scalaTestDependencies,
    libraryDependencies += "com.typesafe.play" %% "play-json" % "2.4.6",
    libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0"
  ).enablePlugins(UniversalPlugin)

lazy val rest = project.in(file("rest"))
  .settings(commonSettings: _*)
  .settings(filterSettings: _*)
  .settings(
    name := "toscaruntime-rest",
    filterDirectoryName := "src/main/resources",
    includeFilter in(Compile, filterResources) ~= { f => f || "Dockerfile" },
    libraryDependencies += "com.typesafe.play" %% "play-ws" % "2.4.6",
    libraryDependencies += "org.yaml" % "snakeyaml" % "1.16",
    (packageBin in Compile) <<= (packageBin in Compile) dependsOn (filterResources in Compile)
  ).dependsOn(restModel, dockerUtil, fileUtil, miscUtil, sharedContracts).enablePlugins(UniversalPlugin)

lazy val compiler = project.in(file("compiler"))
  .settings(commonSettings: _*)
  .settings(
    name := "toscaruntime-compiler",
    libraryDependencies ++= testDependencies,
    libraryDependencies ++= scalaTestDependencies,
    libraryDependencies += "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4",
    libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
    libraryDependencies += "com.typesafe" % "config" % "1.3.0"
  ).dependsOn(sdk, commonProvider, compilationUtil, fileUtil, dockerUtil, miscUtil, gitUtil % "test", mock % "test").enablePlugins(SbtTwirl, UniversalPlugin)

lazy val runtime = project.in(file("runtime"))
  .settings(commonSettings: _*)
  .settings(
    name := "toscaruntime-runtime",
    libraryDependencies ++= testDependencies,
    libraryDependencies ++= scalaTestDependencies,
    libraryDependencies += "org.yaml" % "snakeyaml" % "1.16",
    libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
    sources in doc in Compile := List()
  ).dependsOn(sdk, commonProvider, fileUtil, compiler % "test->test;test->compile", docker % "test").enablePlugins(UniversalPlugin)

lazy val deployer = project.in(file("deployer"))
  .settings(commonSettings: _*)
  .settings(
    name := "toscaruntime-deployer",
    javaOptions in Universal += "-Dio.netty.noJavassist=true",
    routesGenerator := InjectedRoutesGenerator,
    libraryDependencies += "com.h2database" % "h2" % "1.4.191",
    libraryDependencies += "com.typesafe.play" %% "play-slick" % "1.1.1",
    libraryDependencies += "com.typesafe.play" %% "play-slick-evolutions" % "1.1.1",
    libraryDependencies += "org.scalatestplus" %% "play" % "1.4.0" % "test",
    packageName in Docker := "toscaruntime/deployer",
    dockerExposedPorts in Docker := Seq(9000, 9443),
    stage <<= stage dependsOn(publishLocal, publishLocal in Docker)
  ).dependsOn(runtime, restModel, mock, sdk).enablePlugins(PlayScala, DockerPlugin)

lazy val proxy = project.in(file("proxy"))
  .settings(commonSettings: _*)
  .settings(
    name := "toscaruntime-proxy",
    packageName in Docker := "toscaruntime/proxy",
    libraryDependencies += ws,
    routesGenerator := InjectedRoutesGenerator,
    libraryDependencies += cache,
    dockerExposedPorts in Docker := Seq(9000, 9443),
    stage <<= stage dependsOn(publishLocal, publishLocal in Docker)
  ).dependsOn(dockerUtil, rest).enablePlugins(PlayScala, DockerPlugin)

val pluginSettings: Seq[Setting[_]] = commonSettings ++ Seq(
  mappings in Universal <++= (packageBin in Compile, baseDirectory) map { (_, base) =>
    val java = base / "src" / "main" / "java"
    java.*** pair relativeTo(base)
  }, mappings in Universal <++= (packageBin in Compile, target, baseDirectory) map { (_, target, baseDirectory) =>
    val classes = target / "classes"
    val resources = classes / ("toscaruntime-" + baseDirectory.name + "-plugin-types")
    (resources.*** pair relativeTo(classes)).map {
      case (key, value) => (key, "src/main/resources/" + value)
    }
  })

val providerSettings: Seq[Setting[_]] = commonSettings ++ Seq(
  mappings in Universal <++= (packageBin in Compile, baseDirectory) map { (_, base) =>
    val java = base / "src" / "main" / "java"
    java.*** pair relativeTo(base)
  }, mappings in Universal <++= (packageBin in Compile, target, baseDirectory) map { (_, target, baseDirectory) =>
    val classes = target / "classes"
    val resources = classes / ("toscaruntime-" + baseDirectory.name + "-provider-types")
    (resources.*** pair relativeTo(classes)).map {
      case (key, value) => (key, "src/main/resources/" + value)
    }
  })

lazy val providers = project.in(file("providers")).settings(commonSettings: _*)
  .settings(
    name := "toscaruntime-providers"
  ).aggregate(commonProvider, docker, openstack, aws, mock).enablePlugins(UniversalPlugin)

lazy val commonProvider = project.in(file("providers/common"))
  .settings(providerSettings: _*)
  .settings(filterSettings: _*)
  .settings(
    name := "toscaruntime-providers-common",
    filterDirectoryName := "src/main/resources/toscaruntime-common-provider-types",
    includeFilter in(Compile, filterResources) ~= { f => f || "*.yaml" },
    (packageBin in Compile) <<= (packageBin in Compile) dependsOn (filterResources in Compile)
  ).dependsOn(sdk % "provided").enablePlugins(JavaAppPackaging)

lazy val docker = project.in(file("providers/docker"))
  .settings(providerSettings: _*)
  .settings(filterSettings: _*)
  .settings(
    name := "toscaruntime-docker",
    libraryDependencies ++= testDependencies,
    filterDirectoryName := "src/main/resources/toscaruntime-docker-provider-types",
    includeFilter in(Compile, filterResources) ~= { f => f || "*.yaml" },
    (packageBin in Compile) <<= (packageBin in Compile) dependsOn (filterResources in Compile)
  ).dependsOn(sdk % "provided", commonProvider % "provided", dockerUtil).enablePlugins(JavaAppPackaging)

lazy val mock = project.in(file("providers/mock"))
  .settings(providerSettings: _*)
  .settings(
    name := "toscaruntime-mock",
    libraryDependencies ++= testDependencies
  ).dependsOn(sdk % "provided").enablePlugins(JavaAppPackaging)

lazy val openstack = project.in(file("providers/openstack"))
  .settings(providerSettings: _*)
  .settings(filterSettings: _*)
  .settings(
    name := "toscaruntime-openstack",
    filterDirectoryName := "src/main/resources/toscaruntime-openstack-provider-types",
    includeFilter in(Compile, filterResources) ~= { f => f || "*.yaml" },
    libraryDependencies += "org.apache.jclouds.driver" % "jclouds-slf4j" % "1.9.1",
    libraryDependencies += "org.apache.jclouds.api" % "openstack-keystone" % "1.9.1",
    libraryDependencies += "org.apache.jclouds.api" % "openstack-nova" % "1.9.1",
    libraryDependencies += "org.apache.jclouds.api" % "openstack-cinder" % "1.9.1",
    libraryDependencies += "org.apache.jclouds.labs" % "openstack-neutron" % "1.9.1",
    libraryDependencies ++= testDependencies,
    (packageBin in Compile) <<= (packageBin in Compile) dependsOn (filterResources in Compile)
  ).dependsOn(sdk % "provided", commonProvider % "provided", sharedContracts % "provided", miscUtil % "provided").enablePlugins(JavaAppPackaging)

lazy val aws = project.in(file("providers/aws"))
  .settings(providerSettings: _*)
  .settings(filterSettings: _*)
  .settings(
    name := "toscaruntime-aws",
    filterDirectoryName := "src/main/resources/toscaruntime-aws-provider-types",
    includeFilter in(Compile, filterResources) ~= { f => f || "*.yaml" },
    libraryDependencies += "org.apache.jclouds.driver" % "jclouds-slf4j" % "1.9.3-SNAPSHOT",
    libraryDependencies += "org.apache.jclouds.provider" % "aws-ec2" % "1.9.3-SNAPSHOT",
    libraryDependencies += "org.apache.jclouds.provider" % "aws-s3" % "1.9.3-SNAPSHOT",
    libraryDependencies ++= testDependencies,
    (packageBin in Compile) <<= (packageBin in Compile) dependsOn (filterResources in Compile)
  ).dependsOn(sdk % "provided", commonProvider % "provided", sharedContracts % "provided", miscUtil % "provided").enablePlugins(JavaAppPackaging)

lazy val plugins = project.in(file("plugins")).settings(commonSettings: _*)
  .settings(
    name := "toscaruntime-plugins"
  ).aggregate(scriptPlugin, consulPlugin).enablePlugins(UniversalPlugin)

lazy val scriptPlugin = project.in(file("plugins/script"))
  .settings(pluginSettings: _*)
  .settings(filterSettings: _*)
  .settings(
    name := "toscaruntime-script",
    libraryDependencies ++= testDependencies,
    filterDirectoryName := "src/main/resources/toscaruntime-script-plugin-types",
    includeFilter in(Compile, filterResources) ~= { f => f || "*.yaml" }
  ).dependsOn(sdk % "provided", commonProvider % "provided", sharedContracts % "provided", miscUtil % "provided", sshUtil, dockerUtil).enablePlugins(JavaAppPackaging)

lazy val consulPlugin = project.in(file("plugins/consul"))
  .settings(pluginSettings: _*)
  .settings(filterSettings: _*)
  .settings(
    name := "toscaruntime-consul",
    filterDirectoryName := "src/main/resources/toscaruntime-consul-plugin-types",
    includeFilter in(Compile, filterResources) ~= { f => f || "*.yaml" },
    libraryDependencies += "com.orbitz.consul" % "consul-client" % "0.12.3"
  ).dependsOn(sdk % "provided", sharedContracts % "provided", miscUtil % "provided").enablePlugins(JavaAppPackaging)

lazy val sdk = project.in(file("sdk"))
  .settings(pluginSettings: _*)
  .settings(
    name := "toscaruntime-sdk",
    libraryDependencies ++= testDependencies,
    libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.12",
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.3"
  ).dependsOn(miscUtil, sharedContracts).enablePlugins(JavaAppPackaging)

lazy val downloadSbtLauncher = taskKey[Unit]("Downloads sbt launcher.")

lazy val cli = project.in(file("cli"))
  .settings(commonSettings: _*)
  .settings(filterSettings: _*)
  .settings(
    name := "toscaruntime-cli",
    libraryDependencies += "org.scala-sbt" % "command" % "0.13.8",
    filterDirectoryName := "src/main/resources/",
    includeFilter in(Compile, filterResources) ~= { f => f || "*.yaml" },
    downloadSbtLauncher := {
      val logFile = target.value / "prepare-stage" / "log" / "cli.log"
      logFile.getParentFile.mkdirs()
      IO.touch(logFile)
      val sbtLaunchTarget = target.value / "prepare-stage" / "bin" / "sbt-launch.jar"
      sbtLaunchTarget.getParentFile.mkdirs()
      IO.download(url("https://repo.typesafe.com/typesafe/ivy-releases/org.scala-sbt/sbt-launch/0.13.8/sbt-launch.jar"), sbtLaunchTarget)
      val toscaRuntimeBashScriptTarget = target.value / "prepare-stage" / "bin" / "tosca-runtime.sh"
      toscaRuntimeBashScriptTarget.getParentFile.mkdirs()
      IO.copyFile((resourceDirectory in Compile).value / "bin" / "tosca-runtime.sh", toscaRuntimeBashScriptTarget)
      toscaRuntimeBashScriptTarget.setExecutable(true)
      IO.copyFile((resourceDirectory in Compile).value / "bin" / "tosca-runtime.bat", target.value / "prepare-stage" / "bin" / "tosca-runtime.bat")

      // Launch config
      val launchConfigTarget = target.value / "prepare-stage" / "conf" / "launchConfig"
      launchConfigTarget.getParentFile.mkdirs()
      val launchConfigSource = (resourceDirectory in Compile).value / "conf" / "launchConfig.template"
      var launchConfigTemplateText = IO.read(launchConfigSource)
      launchConfigTemplateText = launchConfigTemplateText.replace("${organization}", organization.value)
      launchConfigTemplateText = launchConfigTemplateText.replace("${name}", name.value)
      launchConfigTemplateText = launchConfigTemplateText.replace("${version}", version.value)
      IO.write(launchConfigTarget, launchConfigTemplateText)

      // Copy common provider types, as classes are imported and used by toscaruntime then copy only yaml  definitions
      val commonProviderTarget = target.value / "prepare-stage" / "repository" / "toscaruntime-common-provider-types" / version.value / "src" / "main" / "resources"
      commonProviderTarget.mkdirs()
      IO.copyDirectory((stage in commonProvider).value / "src" / "main" / "resources", commonProviderTarget)

      // Copy docker provider
      val dockerProviderTarget = target.value / "prepare-stage" / "repository" / "toscaruntime-docker-provider-types" / version.value
      dockerProviderTarget.mkdirs()
      IO.copyDirectory((stage in docker).value, dockerProviderTarget)

      // Copy openstack provider
      val openstackProviderTarget = target.value / "prepare-stage" / "repository" / "toscaruntime-openstack-provider-types" / version.value
      openstackProviderTarget.mkdirs()
      IO.copyDirectory((stage in openstack).value, openstackProviderTarget)

      // Copy aws provider
      val awsProviderTarget = target.value / "prepare-stage" / "repository" / "toscaruntime-aws-provider-types" / version.value
      awsProviderTarget.mkdirs()
      IO.copyDirectory((stage in aws).value, awsProviderTarget)

      // Handle plugins
      // Copy consul plugin
      val consulPluginTarget = target.value / "prepare-stage" / "repository" / "toscaruntime-consul-plugin-types" / version.value
      consulPluginTarget.mkdirs()
      IO.copyDirectory((stage in consulPlugin).value, consulPluginTarget)

      // Copy script plugin
      val scriptPluginTarget = target.value / "prepare-stage" / "repository" / "toscaruntime-script-plugin-types" / version.value
      scriptPluginTarget.mkdirs()
      IO.copyDirectory((stage in scriptPlugin).value, scriptPluginTarget)

      // Copy providers configurations
      val providerConf = target.value / "prepare-stage" / "conf" / "providers"
      providerConf.mkdirs()
      IO.copyDirectory((resourceDirectory in Compile).value / "conf" / "providers", providerConf)

      // Copy plugins configurations
      val pluginConf = target.value / "prepare-stage" / "conf" / "plugins"
      pluginConf.mkdirs()
      IO.copyDirectory((resourceDirectory in Compile).value / "conf" / "plugins", pluginConf)

      // Copy bootstrap csars
      val bootstrapConf = target.value / "prepare-stage" / "bootstrap"
      bootstrapConf.mkdirs()
      val bootstrapConfSource = target.value / "classes" / "bootstrap"
      IO.copyDirectory(bootstrapConfSource, bootstrapConf)

      val sdkToscaTarget = target.value / "prepare-stage" / "csars"
      val sdkToscaTempDownloadTarget = sdkToscaTarget / "tosca-normative-types.zip"
      sdkToscaTempDownloadTarget.getParentFile.mkdirs()
      IO.download(url("https://github.com/vuminhkh/tosca-normative-types/archive/master.zip"), sdkToscaTempDownloadTarget)
      IO.unzip(sdkToscaTempDownloadTarget, sdkToscaTarget)
      IO.copyDirectory(target.value / "classes" / "csars", sdkToscaTarget)

      val command = s"java -jar $sbtLaunchTarget @$launchConfigTarget"
      Process(command, Some(sbtLaunchTarget.getParentFile)) ! match {
        case 0 =>
          println("Successfully loaded cli dependencies")
          IO.delete(sdkToscaTarget)
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
    dist <<= (packageZipTarball in Universal) dependsOn stage,
    (packageBin in Compile) <<= (packageBin in Compile) dependsOn (filterResources in Compile)
  ).dependsOn(compiler, runtime, rest).enablePlugins(UniversalPlugin)

lazy val copyProviders = taskKey[Unit]("Copy provider resources for integration tests.")

lazy val itTest = project.in(file("test"))
  .configs(IntegrationTest)
  .settings(commonSettings: _*)
  .settings(Defaults.itSettings: _*)
  .settings(
    name := "toscaruntime-it-test",
    libraryDependencies += "org.scalatest" % "scalatest_2.11" % "2.2.5" % "it,test",
    copyProviders := {
      val dockerProviderTarget = target.value / "prepare-test" / "docker-provider-types" / version.value
      dockerProviderTarget.mkdirs()
      val openstackProviderTarget = target.value / "prepare-test" / "openstack-provider-types" / version.value
      openstackProviderTarget.mkdirs()
      val awsProviderTarget = target.value / "prepare-test" / "aws-provider-types" / version.value
      awsProviderTarget.mkdirs()
      IO.copyDirectory((stage in docker).value, dockerProviderTarget)
      IO.copyDirectory((stage in openstack).value, openstackProviderTarget)
      IO.copyDirectory((stage in aws).value, awsProviderTarget)
    },
    stage <<= (stage in Universal) dependsOn copyProviders
  ).dependsOn(cli).enablePlugins(UniversalPlugin)