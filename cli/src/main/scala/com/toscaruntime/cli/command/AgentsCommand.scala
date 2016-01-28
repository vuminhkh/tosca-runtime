package com.toscaruntime.cli.command

import com.toscaruntime.cli.Attributes
import com.toscaruntime.cli.util.DeployUtil
import sbt.complete.DefaultParsers._
import sbt.{Command, Help}

import scala.io.StdIn
import scala.language.postfixOps

/**
  * Command to handle list, delete, show information of an agent
  *
  * @author Minh Khang VU
  */
object AgentsCommand {

  val commandName = "agents"

  private val listOpt = "list"

  private val logOpt = "log"

  private val startOpt = "start"

  private val stopOpt = "stop"

  private val deleteOpt = "delete"

  private val deployOpt = "deploy"

  private val undeployOpt = "undeploy"

  private val scaleOpt = "scale"

  private val infoOpt = "info"

  private val nodeNameOpt = "-n"

  private val instancesCountOpt = "-c"

  private lazy val scaleArgsParser = Space ~> ((token(nodeNameOpt) ~ (Space ~> token(StringBasic))) | (token(instancesCountOpt) ~ (Space ~> token(IntBasic)))) +

  private lazy val agentsArgsParser = Space ~>
    (token(listOpt) |
      (token(startOpt) ~ (Space ~> token(StringBasic))) |
      (token(logOpt) ~ (Space ~> token(StringBasic))) |
      (token(stopOpt) ~ (Space ~> token(StringBasic))) |
      (token(deleteOpt) ~ (Space ~> token(StringBasic))) |
      (token(deployOpt) ~ (Space ~> token(StringBasic))) |
      (token(scaleOpt) ~ (Space ~> token(StringBasic)) ~ scaleArgsParser) |
      (token(undeployOpt) ~ (Space ~> token(StringBasic))) |
      (token(infoOpt) ~ (Space ~> token(StringBasic)))) +

  private lazy val agentsActionsHelp = Help(commandName, (commandName, s"List, stop or delete agent, execute 'help $commandName' for more details"),
    s"""
       |$commandName [$listOpt| [$startOpt|$stopOpt|$deleteOpt|$deployOpt|$undeployOpt|$scaleOpt|$infoOpt|$logOpt] <deployment id> [other options]
       |$listOpt     : list all agents
       |$logOpt      : show the agent's log
       |$infoOpt     : show the agent's deployment details
       |$startOpt    : start agent, agent will begin to manage deployment
       |$stopOpt     : stop agent, agent will not manage deployment anymore
       |$deployOpt   : launch default deployment workflow
       |$undeployOpt : launch default un-deployment workflow
       |$scaleOpt    : launch default scale workflow on the given node
       |               $scaleOpt <deployment id> $nodeNameOpt <node name> $instancesCountOpt <instances count>
       |$deleteOpt   : delete agent
    """.stripMargin
  )

  lazy val instance = Command("agents", agentsActionsHelp)(_ => agentsArgsParser) { (state, args) =>
    // TODO multiple agents to manage a deployment ... For the moment one agent per deployment
    val client = state.attributes.get(Attributes.clientAttribute).get
    var fail = false
    args.head match {
      case "list" =>
        DeployUtil.listDeploymentAgents(client)
      case ("info", deploymentId: String) =>
        DeployUtil.printDetails(client, deploymentId)
      case (("scale", deploymentId: String), scaleOpts: Seq[(String, _)]) =>
        val scaleArgs = scaleOpts.toMap
        val nodeName = scaleArgs.get(nodeNameOpt)
        val newInstancesCount = scaleArgs.get(instancesCountOpt)
        if (nodeName.isEmpty) {
          println("Node name is mandatory for scale workflow")
          fail = true
        } else if (newInstancesCount.isEmpty) {
          println("Instances count is mandatory for scale workflow")
          fail = true
        } else {
          client.scale(deploymentId, nodeName.get.asInstanceOf[String], newInstancesCount.get.asInstanceOf[Int])
          println(s"Execute 'agents log $deploymentId' to tail the log of deployment agent")
        }
      case ("deploy", deploymentId: String) =>
        client.deploy(deploymentId)
        println(s"Execute 'agents log $deploymentId' to tail the log of deployment agent")
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
        println(s"Undeployed $deploymentId")
        println(s"Execute 'agents log $deploymentId' to tail the log of deployment agent")
      case ("start", deploymentId: String) =>
        client.startDeploymentAgent(deploymentId)
        println(s"Started $deploymentId")
      case ("stop", deploymentId: String) =>
        client.stopDeploymentAgent(deploymentId)
        println(s"Stopped $deploymentId")
      case ("delete", deploymentId: String) =>
        client.deleteDeploymentAgent(deploymentId)
        println(s"Deleted $deploymentId")
    }
    state
  }
}
