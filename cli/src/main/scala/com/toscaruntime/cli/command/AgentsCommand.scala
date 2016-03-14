package com.toscaruntime.cli.command

import com.toscaruntime.cli.Attributes
import com.toscaruntime.cli.util.AgentUtil
import com.toscaruntime.rest.client.ToscaRuntimeClient
import sbt.complete.DefaultParsers._
import sbt.{Command, Help}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.StdIn
import scala.language.postfixOps

/**
  * Command to handle list, delete, show information of an agent
  *
  * @author Minh Khang VU
  */
object AgentsCommand {

  val commandName = "agents"

  private val createOpt = "create"

  private val listOpt = "list"

  private val logOpt = "log"

  private val startOpt = "start"

  private val stopOpt = "stop"

  private val deleteOpt = "delete"

  private val forceOpt = "-f"

  private val deployOpt = "deploy"

  private val undeployOpt = "undeploy"

  private val scaleOpt = "scale"

  private val infoOpt = "info"

  private val nodeNameOpt = "node"

  private val instancesCountOpt = "to"

  private val nodesInfoOpt = "nodes"

  private val outputsInfoOpt = "outputs"

  private val relationshipsInfoOpt = "relationships"

  private val nodeInfoOpt = "node"

  private val relationshipInfoOpt = "relationship"

  private val instanceInfoOpt = "instance"

  private val relationshipInstanceInfoOpt = "relationshipInstance"

  private lazy val scaleArgsParser = Space ~> ((token(nodeNameOpt) ~ (Space ~> token(StringBasic))) | (token(instancesCountOpt) ~ (Space ~> token(IntBasic)))) +

  private lazy val infoNodeArgsParser = token(nodeInfoOpt) ~ (Space ~> token(StringBasic))

  private lazy val relationshipArgsParser = token(relationshipInfoOpt) ~ (Space ~> token(StringBasic)) ~ (Space ~> token(StringBasic))

  private lazy val infoInstanceArgsParser = token(instanceInfoOpt) ~ (Space ~> token(StringBasic))

  private lazy val relationshipInstanceArgsParser = token(relationshipInstanceInfoOpt) ~ (Space ~> token(StringBasic)) ~ (Space ~> token(StringBasic))

  private lazy val infoExtraArgsParser = (Space ~> (token(nodesInfoOpt) | token(relationshipsInfoOpt) | token(outputsInfoOpt) | infoNodeArgsParser | infoInstanceArgsParser | relationshipArgsParser | relationshipInstanceArgsParser)) ?

  private lazy val agentsArgsParser = Space ~>
    (token(listOpt) |
      (token(createOpt) ~ (Space ~> token(StringBasic))) |
      (token(startOpt) ~ (Space ~> token(StringBasic))) |
      (token(logOpt) ~ (Space ~> token(StringBasic))) |
      (token(stopOpt) ~ (Space ~> token(StringBasic))) |
      (token(deleteOpt) ~ ((Space ~> token(forceOpt)) ?) ~ (Space ~> token(StringBasic))) |
      (token(deployOpt) ~ (Space ~> token(StringBasic))) |
      (token(scaleOpt) ~ (Space ~> token(StringBasic)) ~ scaleArgsParser) |
      (token(undeployOpt) ~ (Space ~> token(StringBasic))) |
      (token(infoOpt) ~ (Space ~> token(StringBasic) ~ infoExtraArgsParser))) +

  private lazy val agentsActionsHelp = Help(commandName, (commandName, s"List, stop or delete agent asynchronously, execute 'help $commandName' for more details"),
    s"""
       |$commandName [$listOpt| [$createOpt|$startOpt|$stopOpt|$deleteOpt|$deployOpt|$undeployOpt|$scaleOpt|$infoOpt|$logOpt] <deployment id> [other options]
       |$listOpt     : list all agents
       |$createOpt   : create an agent to manage the given deployment and run immediately install workflow to deploy it
       |$logOpt      : show the agent's log
       |$infoOpt     : show the agent's deployment details
       |              $infoOpt $outputsInfoOpt: show only outputs details
       |              $infoOpt $nodesInfoOpt: show only nodes details
       |              $infoOpt $relationshipsInfoOpt: show only relationships details
       |              $infoOpt $nodeInfoOpt <node id>: show node details
       |              $infoOpt $instanceInfoOpt <instance id>: show instance details
       |              $infoOpt $relationshipInfoOpt <source> <target>: show relationship node details
       |              $infoOpt $relationshipInstanceInfoOpt <source instance> <target instance>: show relationship instance details
       |$startOpt    : start agent, agent will begin to manage deployment
       |$stopOpt     : stop agent, agent will stop to manage deployment
       |$deployOpt   : launch default deployment workflow
       |$undeployOpt : launch default un-deployment workflow
       |$scaleOpt    : launch default scale workflow on the given node
       |              $scaleOpt <deployment id> $nodeNameOpt <node name> $instancesCountOpt <instances count>
       |$deleteOpt   : delete agent
       |              $deleteOpt $forceOpt : force the delete of the agent without undeploying application first
    """.stripMargin
  )

  def createAgent(client: ToscaRuntimeClient, deploymentId: String) = {
    val containerId = client.createDeploymentAgent(deploymentId).getId
    AgentUtil.waitForDeploymentAgent(client, deploymentId)
    containerId
  }

  def deploy(client: ToscaRuntimeClient, deploymentId: String) = {
    val containerId = createAgent(client, deploymentId)
    (containerId, launchInstallWorkflow(client, deploymentId))
  }

  def undeploy(client: ToscaRuntimeClient, deploymentId: String, force: Boolean) = {
    client.getDeploymentAgentInfo(deploymentId).flatMap { details =>
      if (AgentUtil.hasLivingNodes(details)) {
        if (force) launchTeardownInfrastructure(client, deploymentId) else launchUninstallWorkflow(client, deploymentId)
      }
      client.waitForRunningExecutionToFinish(deploymentId).map { deletedDetails =>
        delete(client, deploymentId)
        deletedDetails
      }
    }
  }

  def launchInstallWorkflow(client: ToscaRuntimeClient, deploymentId: String) = {
    AgentUtil.deploy(client, deploymentId)
  }

  def launchUninstallWorkflow(client: ToscaRuntimeClient, deploymentId: String) = {
    AgentUtil.undeploy(client, deploymentId)
  }

  def launchTeardownInfrastructure(client: ToscaRuntimeClient, deploymentId: String) = {
    AgentUtil.teardownInfrastructure(client, deploymentId)
  }

  def cancelExecution(client: ToscaRuntimeClient, deploymentId: String) = {
    AgentUtil.cancelExecution(client, deploymentId)
  }

  def resumeExecution(client: ToscaRuntimeClient, deploymentId: String) = {
    AgentUtil.resumeExecution(client, deploymentId)
  }

  def listAgents(client: ToscaRuntimeClient) = {
    AgentUtil.listDeploymentAgents(client)
  }

  def scale(client: ToscaRuntimeClient, deploymentId: String, nodeToScale: String, newInstancesCount: Int) = {
    AgentUtil.scale(client, deploymentId, nodeToScale, newInstancesCount)
  }

  def start(client: ToscaRuntimeClient, deploymentId: String) = {
    client.startDeploymentAgent(deploymentId)
    AgentUtil.waitForDeploymentAgent(client, deploymentId)
  }

  def stop(client: ToscaRuntimeClient, deploymentId: String) = {
    client.stopDeploymentAgent(deploymentId)
  }

  def delete(client: ToscaRuntimeClient, deploymentId: String) = {
    client.deleteDeploymentAgent(deploymentId)
  }

  def getNodesDetails(client: ToscaRuntimeClient, deploymentId: String) = {
    AgentUtil.getNodesDetails(AgentUtil.getDeploymentDetails(client, deploymentId))
  }

  def getRelationshipsDetails(client: ToscaRuntimeClient, deploymentId: String) = {
    AgentUtil.getRelationshipsDetails(AgentUtil.getDeploymentDetails(client, deploymentId))
  }

  def getOutputsDetails(client: ToscaRuntimeClient, deploymentId: String) = {
    AgentUtil.getOutputsDetails(AgentUtil.getDeploymentDetails(client, deploymentId))
  }

  lazy val instance = Command("agents", agentsActionsHelp)(_ => agentsArgsParser) { (state, args) =>
    // TODO multiple agents to manage a deployment ... For the moment one agent per deployment
    val client = state.attributes.get(Attributes.clientAttribute).get
    var fail = false
    args.head match {
      case ("create", deploymentId: String) =>
        val deployResult = deploy(client, deploymentId)
        println(deployResult._2)
        println(s"Agent with docker id [${deployResult._1}] has been created to manage deployment [$deploymentId]")
        println(s"Execute 'agents log $deploymentId' to tail the log of deployment agent")
      case "list" =>
        AgentUtil.printDeploymentAgentsList(listAgents(client))
      case ("info", (deploymentId: String, extraArgs: Option[Any])) =>
        extraArgs match {
          case Some("nodes") => AgentUtil.printNodesDetails(deploymentId, getNodesDetails(client, deploymentId))
          case Some("relationships") => AgentUtil.printRelationshipsDetails(deploymentId, getRelationshipsDetails(client, deploymentId))
          case Some("outputs") => AgentUtil.printOutputsDetails(deploymentId, getOutputsDetails(client, deploymentId))
          case Some(("node", nodeId: String)) => AgentUtil.printNodeDetails(client, deploymentId, nodeId)
          case Some((("relationship", source: String), target: String)) => AgentUtil.printRelationshipDetails(client, deploymentId, source, target)
          case Some(("instance", instanceId: String)) => AgentUtil.printInstanceDetails(client, deploymentId, instanceId)
          case Some((("relationshipInstance", source: String), target: String)) => AgentUtil.printRelationshipInstanceDetails(client, deploymentId, source, target)
          case _ => AgentUtil.printDetails(client, deploymentId)
        }
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
          val response = scale(client, deploymentId, nodeName.get.asInstanceOf[String], newInstancesCount.get.asInstanceOf[Int])
          println(response)
          println(s"Execute 'agents log $deploymentId' to tail the log of deployment agent")
        }
      case ("deploy", deploymentId: String) =>
        val response = launchInstallWorkflow(client, deploymentId)
        println(response)
        println(s"Execute 'agents log $deploymentId' to tail the log of deployment agent")
      case ("cancel", deploymentId: String) =>
        val response = cancelExecution(client, deploymentId)
        println(response)
        println(s"Execute 'agents log $deploymentId' to tail the log of deployment agent")
      case ("resume", deploymentId: String) =>
        val response = resumeExecution(client, deploymentId)
        println(response)
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
        val response = launchUninstallWorkflow(client, deploymentId)
        println(response)
        println(s"Execute 'agents log $deploymentId' to tail the log of deployment agent")
      case ("start", deploymentId: String) =>
        start(client, deploymentId)
        println(s"Started $deploymentId")
      case ("stop", deploymentId: String) =>
        stop(client, deploymentId)
        println(s"Stopped $deploymentId")
      case (("delete", force: Option[String]), deploymentId: String) =>
        undeploy(client, deploymentId, force.nonEmpty)
        println(s"Launched deletion of $deploymentId")
    }
    if (fail) state.fail else state
  }
}
