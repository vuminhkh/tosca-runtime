package com.mkv.tosca.cli

import java.io.File
import java.nio.file.{Files, Path, Paths}

import com.github.dockerjava.api.DockerClient
import com.mkv.tosca.cli.command._
import com.mkv.tosca.util.DockerUtil
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

  def buildDockerClient(url: String, certificatePath: String) = {
    println("Using docker daemon with url <" + url + "> and certificate at <" + certificatePath + ">")
    DockerUtil.buildDockerClient(url, certificatePath)
  }

  def buildDockerClient(basedir: Path): DockerClient = {
    val daemonUrlSystemProperty = "tosca-runtime.docker.daemon.url"
    System.getProperty(daemonUrlSystemProperty) match {
      case url: String =>
        println("Using daemon url configured from system property " + daemonUrlSystemProperty)
        buildDockerClient(url, System.getProperty("tosca-runtime.docker.daemon.cert"))
      case null => System.getProperty("os.name") match {
        case windowsOrMac if windowsOrMac.startsWith("Windows") || windowsOrMac.startsWith("Mac") =>
          val url = "https://192.168.99.100:2376"
          println("Using default docker daemon configuration for " + windowsOrMac)
          val certificatePath = System.getProperty("user.home") + "/.docker/machine/machines/default"
          val cfp = Paths.get(certificatePath)
          val dockerDefaultConfCert = basedir.resolve("conf").resolve("providers").resolve("docker").resolve("default").resolve("cert")
          if ("true".equals(System.getProperty("tosca-runtime.initConf")) && Files.exists(cfp) && !Files.exists(dockerDefaultConfCert)) {
            // Just in case the flag tosca-runtime.initConf is set, try to copy certificates for default configuration
            // For mac and windows, auto configure by copying default machine's certificates to provider default conf
            println("Copy certificates from <" + cfp + "> to <" + dockerDefaultConfCert + ">")
            Files.createDirectories(dockerDefaultConfCert)
            Files.copy(cfp.resolve("key.pem"), dockerDefaultConfCert.resolve("key.pem"))
            Files.copy(cfp.resolve("cert.pem"), dockerDefaultConfCert.resolve("cert.pem"))
            Files.copy(cfp.resolve("ca.pem"), dockerDefaultConfCert.resolve("ca.pem"))
          }
          buildDockerClient(url, certificatePath)
        case other: String =>
          println("Using default docker daemon configuration for " + other)
          val url = "unix:///var/run/docker.sock"
          buildDockerClient(url, null)
      }
    }
  }

  /** Sets up the application by constructing an initial State instance with the supported commands
    * and initial commands to run.  See the State API documentation for details. */
  def initialState(configuration: xsbti.AppConfiguration): State = {
    val commandDefinitions = Seq(
      CompileCommand.instance,
      PackageCommand.instance,
      DeployCommand.instance,
      ListDeploymentCommand.instance,
      UseCommand.instance,
      BootStrapCommand.instance,
      LogCommand.instance,
      BasicCommands.shell,
      BasicCommands.help,
      BasicCommands.exit)
    val basedir = Paths.get(System.getProperty("tosca-runtime.basedir", System.getProperty("user.dir") + "/..")).toAbsolutePath
    val osName = System.getProperty("os.name")
    println("Starting tosca runtime cli on <" + osName + "> operating system from <" + basedir + ">")
    val attributes = AttributeMap(
      AttributeEntry(Attributes.dockerDaemonAttribute, new DockerClientHolder(buildDockerClient(basedir))),
      AttributeEntry(Attributes.basedirAttribute, basedir)
    )
    State(configuration, commandDefinitions, Set.empty, None, Seq("shell"), State.newHistory, attributes, initialGlobalLogging, State.Continue)
  }

  /** Configures logging to log to a temporary backing file as well as to the console.
    * An application would need to do more here to customize the logging level and
    * provide access to the backing file (like sbt's last command and logLevel setting). */
  /** The common interface to standard output, used for all built-in ConsoleLoggers. */
  def initialGlobalLogging: GlobalLogging = GlobalLogging.initial(MainLogging.globalDefault(ConsoleOut.systemOut), File.createTempFile("tosca-runtime", ".log"), ConsoleOut.systemOut)
}
