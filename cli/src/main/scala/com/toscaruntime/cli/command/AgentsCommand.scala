package com.toscaruntime.cli.command

import com.toscaruntime.cli.Attributes
import com.toscaruntime.cli.util.DeployUtil
import sbt.complete.DefaultParsers._
import sbt.{Command, Help}

import scala.io.StdIn

/**
  * Command to handle list, delete, show information of an agent
  * @author Minh Khang VU
  */
object AgentsCommand {

  private val listOpt = "list"

  private val logOpt = "log"

  private val startOpt = "start"

  private val stopOpt = "stop"

  private val deleteOpt = "delete"

  private val deployOpt = "deploy"

  private val undeployOpt = "undeploy"

  private val infoOpt = "info"

  private lazy val agentsArgsParser = Space ~>
    (token(listOpt) |
      (token(startOpt) ~ (Space ~> token(StringBasic))) |
      (token(logOpt) ~ (Space ~> token(StringBasic))) |
      (token(stopOpt) ~ (Space ~> token(StringBasic))) |
      (token(deleteOpt) ~ (Space ~> token(StringBasic))) |
      (token(deployOpt) ~ (Space ~> token(StringBasic))) |
      (token(undeployOpt) ~ (Space ~> token(StringBasic))) |
      (token(infoOpt) ~ (Space ~> token(StringBasic)))) +

  private lazy val agentsActionsHelp = Help("agents", ("agents", "List, stop or delete agent, execute 'help agents' for more details"),
    """
      |agents [list | [stop|delete|deploy|undeploy|info|log] <deployment id>]
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
    val client = state.attributes.get(Attributes.clientAttribute).get
    args.head match {
      case "list" =>
        DeployUtil.listDeploymentAgents(client)
      case ("info", deploymentId: String) =>
        DeployUtil.printDetails(client, deploymentId)
      case ("deploy", deploymentId: String) =>
        client.deploy(deploymentId)
        println("Execute 'agents log " + deploymentId + "' to tail the log of deployment agent")
      case ("log", deploymentId: String) =>
        println("***Press enter to stop following logs***")
        val logCallback = client.tailLog(deploymentId, System.out)
        try {
          StdIn.readLine()
        } finally {
          logCallback.close()
        }
      case ("undeploy", deploymentId: String) =>
        client.undeploy(deploymentId)
        println("Execute 'agents log " + deploymentId + "' to tail the log of deployment agent")
      case ("start", deploymentId: String) =>
        client.startDeploymentAgent(deploymentId)
        println("Started " + deploymentId)
      case ("stop", deploymentId: String) =>
        client.stopDeploymentAgent(deploymentId)
        println("Stopped " + deploymentId)
      case ("delete", deploymentId: String) =>
        client.deleteDeploymentAgent(deploymentId)
        println("Deleted " + deploymentId)
    }
    state
  }
}
