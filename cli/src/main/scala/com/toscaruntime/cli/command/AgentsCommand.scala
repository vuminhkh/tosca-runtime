package com.toscaruntime.cli.command

import java.nio.file.Path

import com.toscaruntime.cli.Attributes
import com.toscaruntime.cli.util.AgentUtil
import com.toscaruntime.exception.client.BadRequestException
import com.toscaruntime.rest.client.ToscaRuntimeClient
import sbt.complete.DefaultParsers._
import sbt.{Command, Help}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
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

  private val restartOpt = "restart"

  private val deleteOpt = "delete"

  private val forceOpt = "-f"

  private val installOpt = "install"

  private val cancelOpt = "cancel"

  private val resumeOpt = "resume"

  private val uninstallOpt = "uninstall"

  private val updateOpt = "update"

  private val scaleOpt = "scale"

  private val infoOpt = "info"

  private val nodeOpt = "node"

  private val instancesCountOpt = "to"

  private val nodesInfoOpt = "nodes"

  private val executionsInfoOpt = "executions"

  private val executionInfoOpt = "execution"

  private val pauseOpt = "pause"

  private val outputsInfoOpt = "outputs"

  private val relationshipsInfoOpt = "relationships"

  private val relationshipInfoOpt = "relationship"

  private val instanceOpt = "instance"

  private val interfaceOpt = "interface"

  private val relationshipInstanceInfoOpt = "relationshipInstance"

  private val executeOpt = "execute"

  private val operationOpt = "operation"

  private val inputsOpt = "-i"

  private lazy val scaleArgsParser = Space ~> ((token(nodeOpt) ~ (Space ~> token(StringBasic))) | (token(instancesCountOpt) ~ (Space ~> token(IntBasic)))) +

  private lazy val inputsArgsParser = (Space ~> (token(StringBasic) ~ ("=" ~> token(StringBasic)))) +

  private lazy val executeArgsParser = Space ~> ((token(nodeOpt) ~ (Space ~> token(StringBasic)))
    | token(instanceOpt) ~ (Space ~> token(StringBasic))
    | token(interfaceOpt) ~ (Space ~> token(StringBasic))
    | (token(operationOpt) ~ (Space ~> token(StringBasic)))
    | (token(inputsOpt) ~ inputsArgsParser)) +

  private lazy val infoNodeArgsParser = token(nodeOpt) ~ (Space ~> token(StringBasic))

  private lazy val relationshipArgsParser = token(relationshipInfoOpt) ~ (Space ~> token(StringBasic)) ~ (Space ~> token(StringBasic))

  private lazy val infoInstanceArgsParser = token(instanceOpt) ~ (Space ~> token(StringBasic))

  private lazy val relationshipInstanceArgsParser = token(relationshipInstanceInfoOpt) ~ (Space ~> token(StringBasic)) ~ (Space ~> token(StringBasic))

  private lazy val executionArgsParser = token(executionInfoOpt) ~ (Space ~> token(StringBasic))

  private lazy val infoExtraArgsParser = (Space ~> (token(nodesInfoOpt) | token(relationshipsInfoOpt) | token(outputsInfoOpt) | token(executionsInfoOpt) | infoNodeArgsParser | infoInstanceArgsParser | relationshipArgsParser | relationshipInstanceArgsParser | executionArgsParser)) ?

  private lazy val agentsArgsParser = Space ~>
    (token(listOpt) |
      (token(createOpt) ~ (Space ~> token(StringBasic))) |
      (token(startOpt) ~ (Space ~> token(StringBasic))) |
      (token(logOpt) ~ (Space ~> token(StringBasic))) |
      (token(stopOpt) ~ (Space ~> token(StringBasic))) |
      (token(restartOpt) ~ (Space ~> token(StringBasic))) |
      (token(updateOpt) ~ (Space ~> token(StringBasic))) |
      (token(deleteOpt) ~ (Space ~> token(StringBasic)) ~ ((Space ~> token(forceOpt)) ?)) |
      (token(installOpt) ~ (Space ~> token(StringBasic))) |
      (token(cancelOpt) ~ (Space ~> token(StringBasic)) ~ ((Space ~> token(forceOpt)) ?)) |
      (token(resumeOpt) ~ (Space ~> token(StringBasic))) |
      (token(pauseOpt) ~ (Space ~> token(StringBasic)) ~ ((Space ~> token(forceOpt)) ?)) |
      (token(scaleOpt) ~ (Space ~> token(StringBasic)) ~ scaleArgsParser) |
      (token(executeOpt) ~ (Space ~> token(StringBasic)) ~ executeArgsParser) |
      (token(uninstallOpt) ~ (Space ~> token(StringBasic)) ~ ((Space ~> token(forceOpt)) ?)) |
      (token(infoOpt) ~ (Space ~> token(StringBasic) ~ infoExtraArgsParser))) +

  private lazy val agentsActionsHelp = Help(commandName, (commandName, s"List, stop or delete agent asynchronously, execute 'help $commandName' for more details"),
    s"""
       |$commandName [$listOpt| [$createOpt|$startOpt|$stopOpt|$deleteOpt|$installOpt$cancelOpt$resumeOpt|$uninstallOpt|$scaleOpt|$infoOpt|$logOpt] <deployment id> [other options]
       |$listOpt     : list all agents
       |$createOpt   : create an agent to manage the given deployment and run immediately install workflow to deploy it
       |$logOpt      : show the agent's log
       |$infoOpt     : show the agent's deployment details
       |              $infoOpt $outputsInfoOpt: show only outputs details
       |              $infoOpt $nodesInfoOpt: show only nodes details
       |              $infoOpt $relationshipsInfoOpt: show only relationships details
       |              $infoOpt $executionsInfoOpt: show only executions details
       |              $infoOpt $nodeOpt <node id>: show node details
       |              $infoOpt $instanceOpt <instance id>: show instance details
       |              $infoOpt $relationshipInfoOpt <source> <target>: show relationship node details
       |              $infoOpt $relationshipInstanceInfoOpt <source instance> <target instance>: show relationship instance details
       |              $infoOpt $executionInfoOpt <execution id>: show execution details
       |$startOpt    : start agent, agent will begin to manage deployment
       |$stopOpt     : stop agent, agent will stop to manage deployment
       |$restartOpt  : restart the agent, it's useful to refresh the agent with new recipe content
       |$updateOpt   : update the agent's deployment recipe with the one in 'work' directory
       |$installOpt  : launch default install workflow
       |$pauseOpt    : pause current running execution by waiting for all running tasks to finish and not launching new tasks
       |              $pauseOpt <deployment id> $forceOpt try to interrupt current running tasks
       |$cancelOpt   : cancel current running execution
       |              $cancelOpt <deployment id> $forceOpt cancel without waiting for running tasks to finish
       |$resumeOpt   : resume current running execution (which was stopped due to error)
       |$uninstallOpt: launch default uninstall workflow
       |              $uninstallOpt <deployment id> $forceOpt : uninstall only IAAS resources
       |$scaleOpt    : launch default scale workflow on the given node
       |              $scaleOpt <deployment id> $nodeOpt <node name> $instancesCountOpt <instances count>
       |$executeOpt  : launch a particular operation on a particular node or instance
       |              $executeOpt <deployment id> $nodeOpt <node name> $instanceOpt <instance id> $interfaceOpt <interface> $operationOpt <operation>
       |$deleteOpt   : delete agent, if the deployment has living nodes, '$uninstallOpt $forceOpt' will be called before the deletion
       |              $deleteOpt <deployment id> $forceOpt : force the delete of the agent without un-deploying application first
    """.stripMargin
  )

  def createAgent(client: ToscaRuntimeClient, deploymentId: String) = {
    val containerId = client.createDeploymentAgent(deploymentId).getId
    AgentUtil.waitForDeploymentAgent(client, deploymentId)
    containerId
  }

  def deploy(client: ToscaRuntimeClient, deploymentId: String) = {
    val containerId = createAgent(client, deploymentId)
    (s"Created container $containerId, " + launchInstallWorkflow(client, deploymentId), client.waitForRunningExecutionToEnd(deploymentId))
  }

  def undeploy(client: ToscaRuntimeClient, deploymentId: String, force: Boolean): (String, Future[_]) = {
    if (force) {
      deleteAgent(client, deploymentId)
      (s"Deleted by force [$deploymentId]", Future.successful(None))
    } else {
      val details = AgentUtil.getDeploymentDetails(client, deploymentId)
      if (details.executions.nonEmpty && details.executions.head.endTime.isEmpty) {
        ("Deployment has unfinished execution, please wait or cancel execution first", Future.failed(new BadRequestException("Deployment has unfinished execution, please wait or cancel execution first")))
      } else if (AgentUtil.hasLivingNodes(details)) {
        AgentUtil.undeploy(client, deploymentId)
        val deleteFuture = client.waitForRunningExecutionToEnd(deploymentId).map { _ =>
          deleteAgent(client, deploymentId)
        }
        (s"Uninstall workflow has been launched, [$deploymentId] will be deleted once the execution finishes", deleteFuture)
      } else {
        deleteAgent(client, deploymentId)
        (s"Deleted [$deploymentId]", Future.successful(None))
      }
    }
  }

  def launchInstallWorkflow(client: ToscaRuntimeClient, deploymentId: String) = {
    AgentUtil.deploy(client, deploymentId)
  }

  def launchUninstallWorkflow(client: ToscaRuntimeClient, deploymentId: String, force: Boolean) = {
    if (force) AgentUtil.teardownInfrastructure(client, deploymentId) else AgentUtil.undeploy(client, deploymentId)
  }

  def cancelExecution(client: ToscaRuntimeClient, deploymentId: String, force: Boolean) = {
    AgentUtil.cancelExecution(client, deploymentId, force)
  }

  def resumeExecution(client: ToscaRuntimeClient, deploymentId: String) = {
    AgentUtil.resumeExecution(client, deploymentId)
  }

  def stopExecution(client: ToscaRuntimeClient, deploymentId: String, force: Boolean) = {
    AgentUtil.stopExecution(client, deploymentId, force)
  }

  def listAgents(client: ToscaRuntimeClient) = {
    AgentUtil.listDeploymentAgents(client)
  }

  def scaleExecution(client: ToscaRuntimeClient, deploymentId: String, nodeToScale: String, newInstancesCount: Int) = {
    AgentUtil.scaleExecution(client, deploymentId, nodeToScale, newInstancesCount)
  }

  def executeNodeOperation(client: ToscaRuntimeClient, deploymentId: String, nodeName: String, instanceId: Option[String], interface: Option[String], operation: String, inputs: Option[Map[String, String]]) = {
    AgentUtil.executeNodeOperation(client, deploymentId, nodeName, instanceId, interface, operation, inputs)
  }

  def startAgent(client: ToscaRuntimeClient, deploymentId: String) = {
    client.startDeploymentAgent(deploymentId)
    AgentUtil.waitForDeploymentAgent(client, deploymentId)
  }

  def restartAgent(client: ToscaRuntimeClient, deploymentId: String) = {
    client.restartDeploymentAgent(deploymentId)
    AgentUtil.waitForDeploymentAgent(client, deploymentId)
  }

  def stopAgent(client: ToscaRuntimeClient, deploymentId: String) = {
    client.stopDeploymentAgent(deploymentId)
  }

  def deleteAgent(client: ToscaRuntimeClient, deploymentId: String) = {
    client.deleteDeploymentAgent(deploymentId)
  }

  def getNodesDetails(client: ToscaRuntimeClient, deploymentId: String) = {
    AgentUtil.getNodesDetails(AgentUtil.getDeploymentDetails(client, deploymentId))
  }

  def getExecutionsDetails(client: ToscaRuntimeClient, deploymentId: String) = {
    AgentUtil.getExecutionsDetails(AgentUtil.getDeploymentDetails(client, deploymentId))
  }

  def getRelationshipsDetails(client: ToscaRuntimeClient, deploymentId: String) = {
    AgentUtil.getRelationshipsDetails(AgentUtil.getDeploymentDetails(client, deploymentId))
  }

  def getOutputsDetails(client: ToscaRuntimeClient, deploymentId: String) = {
    AgentUtil.getOutputsDetails(AgentUtil.getDeploymentDetails(client, deploymentId))
  }

  def updateDeploymentRecipe(client: ToscaRuntimeClient, deploymentId: String, basedir: Path) = {
    val workDir = basedir.resolve("work")
    AgentUtil.updateDeploymentRecipe(client, deploymentId, workDir.resolve(deploymentId))
  }

  lazy val instance = Command("agents", agentsActionsHelp)(_ => agentsArgsParser) { (state, args) =>
    // TODO multiple agents to manage a deployment ... For the moment one agent per deployment
    val client = state.attributes.get(Attributes.clientAttribute).get
    var fail = false
    try {
      args.head match {
        case ("create", deploymentId: String) =>
          val deployResult = deploy(client, deploymentId)
          println(deployResult._1)
          println(s"Execute 'agents log $deploymentId' to tail the log of deployment agent")
        case "list" =>
          AgentUtil.printDeploymentAgentsList(listAgents(client))
        case ("info", (deploymentId: String, extraArgs: Option[Any])) =>
          extraArgs match {
            case Some("nodes") => AgentUtil.printNodesDetails(deploymentId, getNodesDetails(client, deploymentId))
            case Some("relationships") => AgentUtil.printRelationshipsDetails(deploymentId, getRelationshipsDetails(client, deploymentId))
            case Some("outputs") => AgentUtil.printOutputsDetails(deploymentId, getOutputsDetails(client, deploymentId))
            case Some("executions") => AgentUtil.printExecutionsDetails(deploymentId, getExecutionsDetails(client, deploymentId))
            case Some(("execution", executionId: String)) => AgentUtil.printExecutionDetails(client, deploymentId, executionId)
            case Some(("node", nodeId: String)) => AgentUtil.printNodeDetails(client, deploymentId, nodeId)
            case Some((("relationship", source: String), target: String)) => AgentUtil.printRelationshipDetails(client, deploymentId, source, target)
            case Some(("instance", instanceId: String)) => AgentUtil.printInstanceDetails(client, deploymentId, instanceId)
            case Some((("relationshipInstance", source: String), target: String)) => AgentUtil.printRelationshipInstanceDetails(client, deploymentId, source, target)
            case _ => AgentUtil.printDetails(client, deploymentId)
          }
        case (("scale", deploymentId: String), scaleOpts: Seq[(String, _)]) =>
          val scaleArgs = scaleOpts.toMap
          val nodeName = scaleArgs.get(nodeOpt)
          val newInstancesCount = scaleArgs.get(instancesCountOpt)
          if (nodeName.isEmpty) {
            println("Node name is mandatory for scale workflow")
            fail = true
          } else if (newInstancesCount.isEmpty) {
            println("Instances count is mandatory for scale workflow")
            fail = true
          } else {
            println(scaleExecution(client, deploymentId, nodeName.get.asInstanceOf[String], newInstancesCount.get.asInstanceOf[Int]))
            println(s"Execute 'agents log $deploymentId' to tail the log of deployment agent")
          }
        case (("execute", deploymentId: String), executeOpts: Seq[(String, _)]) =>
          val executeArgs = executeOpts.toMap
          val nodeName = executeArgs(nodeOpt).asInstanceOf[String]
          val instanceId = executeArgs.get(instanceOpt).asInstanceOf[Option[String]]
          val interface = executeArgs.get(interfaceOpt).asInstanceOf[Option[String]]
          val operation = executeArgs(operationOpt).asInstanceOf[String]
          val inputs = executeArgs.get(inputsOpt).asInstanceOf[Option[Seq[(String, String)]]].map(_.toMap)
          println(executeNodeOperation(client, deploymentId, nodeName, instanceId, interface, operation, inputs))
          println(s"Execute 'agents log $deploymentId' to tail the log of deployment agent")
        case ("install", deploymentId: String) =>
          println(launchInstallWorkflow(client, deploymentId))
          println(s"Execute 'agents log $deploymentId' to tail the log of deployment agent")
        case (("cancel", deploymentId: String), force: Option[String]) =>
          println(cancelExecution(client, deploymentId, force.isDefined))
          println(s"Execute 'agents log $deploymentId' to tail the log of deployment agent")
        case ("resume", deploymentId: String) =>
          println(resumeExecution(client, deploymentId))
          println(s"Execute 'agents log $deploymentId' to tail the log of deployment agent")
        case (("pause", deploymentId: String), force: Option[String]) =>
          println(stopExecution(client, deploymentId, force.isDefined))
          println(s"Execute 'agents log $deploymentId' to tail the log of deployment agent")
        case ("log", deploymentId: String) =>
          println("***Press enter to stop following logs***")
          val logCallback = client.tailLog(deploymentId, System.out)
          try {
            StdIn.readLine()
          } finally {
            logCallback.close()
          }
        case (("uninstall", deploymentId: String), force: Option[String]) =>
          println(launchUninstallWorkflow(client, deploymentId, force.nonEmpty))
          println(s"Execute 'agents log $deploymentId' to tail the log of deployment agent")
        case ("start", deploymentId: String) =>
          startAgent(client, deploymentId)
          println(s"Started $deploymentId")
        case ("restart", deploymentId: String) =>
          restartAgent(client, deploymentId)
          println(s"Restarted $deploymentId")
        case ("update", deploymentId: String) =>
          val basedir = state.attributes.get(Attributes.basedirAttribute).get
          println(updateDeploymentRecipe(client, deploymentId, basedir))
          println(s"Changes in scripts are immediately recognized, changes in yaml need agent restart with 'agents restart $deploymentId'")
        case ("stop", deploymentId: String) =>
          stopAgent(client, deploymentId)
          println(s"Stopped $deploymentId")
        case (("delete", deploymentId: String), force: Option[String]) =>
          println(undeploy(client, deploymentId, force.nonEmpty)._1)
      }
    } catch {
      case e: Throwable =>
        println(e.getMessage)
        fail = true
    }
    if (fail) state.fail else state
  }
}
