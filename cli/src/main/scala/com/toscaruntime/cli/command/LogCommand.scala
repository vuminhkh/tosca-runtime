package com.toscaruntime.cli.command

import com.toscaruntime.cli.Attributes
import sbt.complete.DefaultParsers._
import sbt.{Command, Help}

import scala.io.{Source, StdIn}

/**
  * Showing container's log
  *
  * @author Minh Khang VU
  */
object LogCommand {

  private val containerIdOpt = "-c"

  private lazy val containerIdArg = token(containerIdOpt) ~ (Space ~> token(StringBasic))

  private lazy val containerIdArgsParser = (Space ?) ~> containerIdArg *

  private lazy val logHelp = Help("log", ("log", "Show log of a running container, execute 'help log' for more details"),
    """
      |log -c <docker container>
      |-c   : container's id to show log
    """.stripMargin
  )

  lazy val instance = Command("log", logHelp)(_ => containerIdArgsParser) { (state, args) =>
    val argsMap = args.toMap
    if (!argsMap.contains(containerIdOpt)) {
      for (line <- Source.fromFile(state.attributes.get(Attributes.basedirAttribute).get.resolve("log").resolve("cli.log").toFile).getLines())
        println(line)
    } else {
      val containerId = argsMap(containerIdOpt)
      val client = state.attributes.get(Attributes.clientAttribute).get
      println("***Press enter to stop following logs***")
      val logCallback = client.tailContainerLog(containerId, System.out)
      try {
        StdIn.readLine()
      } finally {
        logCallback.close()
      }
    }
    state
  }
}
