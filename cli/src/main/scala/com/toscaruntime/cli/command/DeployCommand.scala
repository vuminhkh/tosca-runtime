package com.toscaruntime.cli.command

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

  private val deploymentIdOpt = "-d"

  private lazy val imageIdArg = token(deploymentIdOpt) ~ (Space ~> token(StringBasic))

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
    if (!argsMap.contains(deploymentIdOpt)) {
      println(deploymentIdOpt + " is mandatory")
      fail = true
    } else {
      val client = state.attributes.get(Attributes.clientAttribute).get
      val deploymentId = argsMap(deploymentIdOpt)
      containerId = client.createDeploymentAgent(deploymentId).getId
      DeployUtil.waitForDeploymentAgent(client, deploymentId)
      client.deploy(deploymentId)
    }
    if (fail) {
      state.fail
    } else {
      println("Started deployment agent <" + containerId + "> to follow the deployment 'log -c " + containerId + "'")
      state
    }
  }
}
