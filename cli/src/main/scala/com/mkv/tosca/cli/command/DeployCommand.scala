package com.mkv.tosca.cli.command

import com.mkv.tosca.cli.Attributes
import sbt.complete.DefaultParsers._
import sbt.{Command, Help}

/**
 * Deploy an agent to manage the deployment
 *
 * @author Minh Khang VU
 */
object DeployCommand {

  private val imageIdOpt = "-i"

  private lazy val imageIdArg = token(imageIdOpt) ~ (Space ~> token(StringBasic))

  private lazy val imageIdArgsParser = Space ~> imageIdArg +

  private lazy val deployHelp = Help("deploy", ("deploy", "Deploy built deployment agent"),
    """
      |deploy -i <docker image of the agent>
      |-i   : image id of the agent docker's image
    """.stripMargin
  )

  lazy val instance = Command("deploy", deployHelp)(_ => imageIdArgsParser) { (state, args) =>
    val argsMap = args.toMap
    var fail = false
    var containerId = ""
    var ipAddress = ""
    if (!argsMap.contains(imageIdOpt)) {
      println(imageIdOpt + " is mandatory")
      fail = true
    } else {
      val dockerClient = state.attributes.get(Attributes.dockerDaemonAttribute).get.dockerClient
      val imageId = argsMap(imageIdOpt)
      containerId = dockerClient.createContainerCmd(imageId).withName(imageId + "_agent").exec.getId
      dockerClient.startContainerCmd(containerId).exec
      ipAddress = dockerClient.inspectContainerCmd(containerId).exec.getNetworkSettings.getIpAddress
    }
    if (fail) {
      state.fail
    } else {
      println("Started deployment agent <" + containerId + "> with ip address <" + ipAddress + ">")
      state
    }
  }
}
