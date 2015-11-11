package com.toscaruntime.cli.command

import com.google.common.collect.Maps
import com.toscaruntime.cli.Attributes
import com.toscaruntime.cli.util.DeployUtil
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

  private lazy val deployHelp = Help("deploy", ("deploy", "Deploy built deployment agent, execute 'help deploy' for more details"),
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
    var agentName = ""
    if (!argsMap.contains(imageIdOpt)) {
      println(imageIdOpt + " is mandatory")
      fail = true
    } else {
      val dockerClient = state.attributes.get(Attributes.dockerDaemonAttribute).get.dockerClient
      val imageId = argsMap(imageIdOpt)
      agentName = imageId + "_agent"
      val labels = Maps.newHashMap[String, String]()
      labels.put("organization", "toscaruntime")
      labels.put("agentType", "bootstrap")
      containerId = dockerClient.createContainerCmd(imageId).withName(agentName).withLabels(labels).exec.getId
      dockerClient.startContainerCmd(containerId).exec
      ipAddress = dockerClient.inspectContainerCmd(containerId).exec.getNetworkSettings.getIpAddress
      DeployUtil.waitForDeploymentAgent(dockerClient, containerId)
      DeployUtil.deploy(dockerClient, containerId)
    }
    if (fail) {
      state.fail
    } else {
      println("Started deployment agent <" + agentName + "> to follow the deployment 'log -c " + agentName + "'")
      state
    }
  }
}
