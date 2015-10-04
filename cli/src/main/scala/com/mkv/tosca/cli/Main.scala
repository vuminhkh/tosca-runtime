package com.mkv.tosca.cli

import java.io.File

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
    val commandDefinitions = Seq(hello, helloAll, failIfTrue, printState, BasicCommands.shell)
    State(configuration, commandDefinitions, Set.empty, None, Seq("shell"), State.newHistory,
      AttributeMap.empty, initialGlobalLogging, State.Continue)
  }

  // A simple, no-argument command that prints "Hi",
  //  leaving the current state unchanged.
  def hello = Command.command("hello") { state =>
    println("Hi!")
    state
  }


  // A simple, multiple-argument command that prints "Hi" followed by the arguments.
  //   Again, it leaves the current state unchanged.
  def helloAll = Command.args("hello-all", "<name>") { (state, args) =>
    println("Hi " + args.mkString(" "))
    state
  }


  // A command that demonstrates failing or succeeding based on the input
  def failIfTrue = Command.single("fail-if-true") {
    case (state, "true") => state.fail
    case (state, _) => state
  }

  // A command that demonstrates getting information out of State.
  def printState = Command.command("print-state") { state =>
    import state._
    println(definedCommands.size + " registered commands")
    println("commands to run: " + show(remainingCommands))
    println()

    println("original arguments: " + show(configuration.arguments))
    println("base directory: " + configuration.baseDirectory)
    println()

    println("sbt version: " + configuration.provider.id.version)
    println("Scala version (for sbt): " + configuration.provider.scalaProvider.version)
    println()
    state
  }

  def show[T](s: Seq[T]) =
    s.map("'" + _ + "'").mkString("[", ", ", "]")

  /** Configures logging to log to a temporary backing file as well as to the console.
    * An application would need to do more here to customize the logging level and
    * provide access to the backing file (like sbt's last command and logLevel setting). */
  /** The common interface to standard output, used for all built-in ConsoleLoggers. */
  def initialGlobalLogging: GlobalLogging = GlobalLogging.initial(MainLogging.globalDefault(ConsoleOut.systemOut), File.createTempFile("tosca-runtime", ".log"), ConsoleOut.systemOut)
}
