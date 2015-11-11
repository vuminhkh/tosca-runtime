package com.toscaruntime.cli.command

import com.github.dockerjava.api.model.Frame
import com.github.dockerjava.core.command.LogContainerResultCallback
import com.toscaruntime.cli.Attributes
import com.toscaruntime.util.DockerUtil
import sbt.complete.DefaultParsers._
import sbt.{Command, Help}

import scala.io.StdIn

/**
 * Showing container's log
 *
 * @author Minh Khang VU
 */
object LogCommand {

  private val containerIdOpt = "-c"

  private lazy val containerIdArg = token(containerIdOpt) ~ (Space ~> token(StringBasic))

  private lazy val containerIdArgsParser = Space ~> containerIdArg +

  private lazy val logHelp = Help("log", ("log", "Show log of a running container, execute 'help log' for more details"),
    """
      |log -c <docker container>
      |-c   : container's id to show log
    """.stripMargin
  )

  lazy val instance = Command("log", logHelp)(_ => containerIdArgsParser) { (state, args) =>
    val argsMap = args.toMap
    var fail = false
    if (!argsMap.contains(containerIdOpt)) {
      println(containerIdOpt + " is mandatory")
      fail = true
    } else {
      val containerId = argsMap(containerIdOpt)
      val dockerClient = state.attributes.get(Attributes.dockerDaemonAttribute).get.dockerClient
      val logCallBack = new LogContainerResultCallback() {
        override def onNext(item: Frame): Unit = {
          print(new String(item.getPayload, "UTF-8"))
          super.onNext(item)
        }
      }
      println("***Press enter to stop following logs***")
      DockerUtil.showLog(dockerClient, containerId, true, 200, logCallBack)
      StdIn.readLine()
      logCallBack.close()
    }
    state
  }
}
