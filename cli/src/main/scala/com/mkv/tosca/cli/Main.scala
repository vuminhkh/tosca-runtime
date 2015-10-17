package com.mkv.tosca.cli

import java.io.File

import com.mkv.tosca.cli.command.{ListDeploymentCommand, DeployCommand, CompileCommand, PackageCommand}
import sbt._

/**
 * Entry point for the cli
 *
 * @author Minh Khang VU
 */
class Main extends xsbti.AppMain {
  /** Defines the entry point for the application.
    * The call to `initialState` sets up the application.
    * The call to runLogged starts command processing. */
  def run(configuration: xsbti.AppConfiguration): xsbti.MainResult =
    MainLoop.runLogged(initialState(configuration))

  /** Sets up the application by constructing an initial State instance with the supported commands
    * and initial commands to run.  See the State API documentation for details. */
  def initialState(configuration: xsbti.AppConfiguration): State = {
    val commandDefinitions = Seq(
      CompileCommand.instance,
      PackageCommand.instance,
      DeployCommand.instance,
      ListDeploymentCommand.instance,
      BasicCommands.shell,
      BasicCommands.help,
      BasicCommands.exit)
    State(configuration, commandDefinitions, Set.empty, None, Seq("shell"), State.newHistory,
      AttributeMap.empty, initialGlobalLogging, State.Continue)
  }

  /** Configures logging to log to a temporary backing file as well as to the console.
    * An application would need to do more here to customize the logging level and
    * provide access to the backing file (like sbt's last command and logLevel setting). */
  /** The common interface to standard output, used for all built-in ConsoleLoggers. */
  def initialGlobalLogging: GlobalLogging = GlobalLogging.initial(MainLogging.globalDefault(ConsoleOut.systemOut), File.createTempFile("tosca-runtime", ".log"), ConsoleOut.systemOut)
}
