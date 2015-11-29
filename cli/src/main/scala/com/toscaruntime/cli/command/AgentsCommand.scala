package com.toscaruntime.cli.command

import com.toscaruntime.cli.Attributes
import com.toscaruntime.cli.util.DeployUtil
import sbt.complete.DefaultParsers._
import sbt.{Command, Help}

/**
  * Command to handle list, delete, show information of an agent
  * @author Minh Khang VU
  */
object AgentsCommand {

  private val listOpt = "list"

  private val startOpt = "start"

  private val stopOpt = "stop"

  private val deleteOpt = "delete"

  private val deployOpt = "deploy"

  private val undeployOpt = "undeploy"

  private val infoOpt = "info"

  private lazy val agentsArgsParser = Space ~>
    (token(listOpt) |
      (token(startOpt) ~ (Space ~> token(StringBasic))) |
      (token(stopOpt) ~ (Space ~> token(StringBasic))) |
      (token(deleteOpt) ~ (Space ~> token(StringBasic))) |
      (token(deployOpt) ~ (Space ~> token(StringBasic))) |
      (token(undeployOpt) ~ (Space ~> token(StringBasic))) |
      (token(infoOpt) ~ (Space ~> token(StringBasic)))) +

  private lazy val agentsActionsHelp = Help("agents", ("agents", "List, stop or delete agent, execute 'help agents' for more details"),
    """
      |agents [list | [stop|delete|deploy|undeploy|info] <agent name>]
      |list     : list all agents
      |start    : start agent, agent will begin to manage deployment
      |stop     : stop agent, agent will not manage deployment anymore
      |deploy   : launch default deployment workflow
      |undeploy : launch default un-deployment workflow
      |delete   : delete agent
    """.stripMargin
  )

  lazy val instance = Command("agents", agentsActionsHelp)(_ => agentsArgsParser) { (state, args) =>
    // TODO multiple agents to manage a deployment ... For the moment one agent per deployment
    val toscaClient = state.attributes.get(Attributes.clientAttribute).get
    args.head match {
      case "list" =>
        DeployUtil.listDeploymentAgents(toscaClient)
      case ("info", deploymentId: String) =>
        DeployUtil.printDetails(toscaClient, deploymentId)
      case ("deploy", deploymentId: String) =>
        toscaClient.deploy(deploymentId)
      case ("undeploy", deploymentId: String) =>
        toscaClient.undeploy(deploymentId)
      case ("start", deploymentId: String) =>
        toscaClient.startDeploymentAgent(deploymentId)
        println("Started " + deploymentId)
      case ("stop", deploymentId: String) =>
        toscaClient.stopDeploymentAgent(deploymentId)
        println("Stopped " + deploymentId)
      case ("delete", deploymentId: String) =>
        toscaClient.deleteDeploymentAgent(deploymentId)
        println("Deleted " + deploymentId)
    }
    state
  }
}
