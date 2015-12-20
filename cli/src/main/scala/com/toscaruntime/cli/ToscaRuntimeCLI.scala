package com.toscaruntime.cli

import java.io.File
import java.nio.file.{Path, Paths}

import com.toscaruntime.cli.command._
import com.toscaruntime.rest.client.ToscaRuntimeClient
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
      val url = existingConfiguration.get("docker.io.url").get
      val cert = existingConfiguration.getOrElse("docker.io.dockerCertPath", null)
      new ToscaRuntimeClient(url, cert)
    } else {
      val daemonUrlSystemProperty = "toscaruntime.docker.daemon.url"
      System.getProperty(daemonUrlSystemProperty) match {
        case url: String =>
          println("Using daemon url configured from system property " + daemonUrlSystemProperty)
          val cert = System.getProperty("toscaruntime.docker.daemon.cert")
          UseCommand.switchConfiguration(url, cert, basedir)
          new ToscaRuntimeClient(url, cert)
        case null => System.getProperty("os.name") match {
          case windowsOrMac if windowsOrMac.startsWith("Windows") || windowsOrMac.startsWith("Mac") =>
            val url = "https://192.168.99.100:2376"
            println("Using default docker daemon configuration for " + windowsOrMac)
            val certificatePath = System.getProperty("user.home") + "/.docker/machine/machines/default"
            if ("true".equals(System.getProperty("toscaruntime.initConf"))) {
              // Just in case the flag toscaruntime.initConf is set, try to copy certificates for default configuration
              // For mac and windows, auto configure by copying default machine's certificates to provider default conf
              // This will ensure that when we perform sbt build, the cert of the machine will not be copied to the build
              UseCommand.switchConfiguration(url, certificatePath, basedir)
            }
            println("Using docker daemon with url <" + url + "> and certificate at <" + certificatePath + ">")
            new ToscaRuntimeClient(url, certificatePath)
          case other: String =>
            println("Using default docker daemon configuration for " + other)
            val url = "unix:///var/run/docker.sock"
            UseCommand.switchConfiguration(url, null, basedir)
            println("Using docker daemon with url <" + url + "> and no certificate")
            new ToscaRuntimeClient(url, null)
        }
      }
    }
  }

  /** Sets up the application by constructing an initial State instance with the supported commands
    * and initial commands to run.  See the State API documentation for details. */
  def initialState(configuration: xsbti.AppConfiguration): State = {
    val commandDefinitions = Seq(
      CompileCommand.instance,
      PackageCommand.instance,
      DeploymentsCommand.instance,
      UseCommand.instance,
      BootStrapCommand.instance,
      TeardownCommand.instance,
      LogCommand.instance,
      AgentsCommand.instance,
      BasicCommands.shell,
      BasicCommands.history,
      BasicCommands.nop,
      BasicCommands.help,
      BasicCommands.exit)
    val basedir = Paths.get(System.getProperty("toscaruntime.basedir", System.getProperty("user.dir") + "/..")).toAbsolutePath
    val osName = System.getProperty("os.name")
    println("Starting tosca runtime cli on <" + osName + "> operating system from <" + basedir + ">")
    val attributes = AttributeMap(
      AttributeEntry(Attributes.clientAttribute, buildDockerClient(basedir)),
      AttributeEntry(Attributes.basedirAttribute, basedir)
    )
    State(configuration, commandDefinitions, Set.empty, None, Seq("shell"), State.newHistory, attributes, initialGlobalLogging, State.Continue)
  }

  /** Configures logging to log to a temporary backing file as well as to the console.
    * An application would need to do more here to customize the logging level and
    * provide access to the backing file (like sbt's last command and logLevel setting). */
  /** The common interface to standard output, used for all built-in ConsoleLoggers. */
  def initialGlobalLogging: GlobalLogging = GlobalLogging.initial(MainLogging.globalDefault(ConsoleOut.systemOut), File.createTempFile("toscaruntime", ".log"), ConsoleOut.systemOut)
}
