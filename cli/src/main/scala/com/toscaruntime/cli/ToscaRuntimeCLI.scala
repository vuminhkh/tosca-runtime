package com.toscaruntime.cli

import java.io.File
import java.nio.file.{Files, Path, Paths}
import java.util.function.{Consumer, Predicate}

import com.toscaruntime.cli.command._
import com.toscaruntime.cli.util.CompilationUtil
import com.toscaruntime.compiler.Compiler
import com.toscaruntime.exception.UnexpectedException
import com.toscaruntime.rest.client.ToscaRuntimeClient
import com.toscaruntime.util.DockerUtil
import sbt._

/**
  * Entry point for the cli
  *
  * @author Minh Khang VU
  */
class ToscaRuntimeCLI extends xsbti.AppMain {

  /** Defines the entry point for the application.
    * The call to `initialState` sets up the application.
    * The call to runLogged starts command processing. */
  def run(configuration: xsbti.AppConfiguration): xsbti.MainResult =
    MainLoop.runLogged(initialState(configuration))

  def buildDockerClient(basedir: Path) = {
    val existingConfiguration = UseCommand.getConfiguration(basedir)
    if (existingConfiguration.nonEmpty) {
      val url = existingConfiguration.get(DockerUtil.DOCKER_URL_KEY).get
      val cert = existingConfiguration.getOrElse(DockerUtil.DOCKER_CERT_PATH_KEY, null)
      new ToscaRuntimeClient(url, cert)
    } else {
      val defaultConfig = DockerUtil.getDefaultDockerDaemonConfig
      if ("true".equals(System.getProperty("toscaruntime.clientMode"))) {
        // Auto configure by copying default machine's certificates to provider default conf only when the flag toscaruntime.clientMode is set
        // This will ensure that when we perform sbt build, the cert of the machine will not be copied to the build
        UseCommand.switchConfiguration(defaultConfig.getUrl, defaultConfig.getCertPath, basedir)
      }
      new ToscaRuntimeClient(defaultConfig.getUrl, defaultConfig.getCertPath)
    }
  }

  private def installCsar(path: Path, repositoryDir: Path) = {
    println(s"Installing csar ${path.getFileName} to repository $repositoryDir")
    val compilationResult = Compiler.install(path, repositoryDir)
    CompilationUtil.showErrors(compilationResult)
    if (!compilationResult.isSuccessful) {
      throw new UnexpectedException(s"Csar compilation failed for $path")
    } else {
      println(s"Installed csar ${path.getFileName} to repository $repositoryDir")
    }
  }

  /** Sets up the application by constructing an initial State instance with the supported commands
    * and initial commands to run.  See the State API documentation for details. */
  def initialState(configuration: xsbti.AppConfiguration): State = {
    val commandDefinitions = Seq(
      CsarsCommand.instance,
      DeploymentsCommand.instance,
      UseCommand.instance,
      UseCommand.useDefaultInstance,
      BootStrapCommand.instance,
      TeardownCommand.instance,
      AgentsCommand.instance,
      BasicCommands.shell,
      BasicCommands.history,
      BasicCommands.nop,
      BasicCommands.help,
      BasicCommands.exit)
    val basedir = Paths.get(System.getProperty("toscaruntime.basedir", System.getProperty("user.dir") + "/..")).toAbsolutePath
    val osName = System.getProperty("os.name")
    println(s"Starting tosca runtime cli on [$osName] operating system from [$basedir]")
    val attributes = AttributeMap(
      AttributeEntry(Attributes.clientAttribute, buildDockerClient(basedir)),
      AttributeEntry(Attributes.basedirAttribute, basedir)
    )
    // FIXME Installing normative types and bootstrap types in repository should be done in the build and not in the code
    val repositoryDir = basedir.resolve("repository")
    if (!"true".equals(System.getProperty("toscaruntime.clientMode"))) {
      val csarsDir = basedir.resolve("csars")
      installCsar(csarsDir.resolve("tosca-normative-types-master"), repositoryDir)
      Files.list(csarsDir).filter(new Predicate[Path] {
        override def test(path: Path) = !path.getFileName.toString.startsWith("tosca-normative-types")
      }).forEach(new Consumer[Path] {
        override def accept(path: Path) = {
          installCsar(path, repositoryDir)
        }
      })
    }
    val workDir = basedir.resolve("work")
    if (!Files.exists(workDir)) {
      Files.createDirectories(workDir)
    }
    State(configuration, commandDefinitions, Set.empty, None, Seq("shell"), State.newHistory, attributes, initialGlobalLogging, State.Continue)
  }

  /** Configures logging to log to a temporary backing file as well as to the console.
    * An application would need to do more here to customize the logging level and
    * provide access to the backing file (like sbt's last command and logLevel setting). */
  /** The common interface to standard output, used for all built-in ConsoleLoggers. */
  def initialGlobalLogging: GlobalLogging = GlobalLogging.initial(MainLogging.globalDefault(ConsoleOut.systemOut), File.createTempFile("toscaruntime", ".log"), ConsoleOut.systemOut)
}
