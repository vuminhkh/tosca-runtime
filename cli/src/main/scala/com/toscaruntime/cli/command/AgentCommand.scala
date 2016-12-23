package com.toscaruntime.cli.command

import java.nio.file.Path

import com.toscaruntime.cli.Args._
import com.toscaruntime.cli.Attributes
import com.toscaruntime.cli.util.AgentUtil
import com.toscaruntime.exception.client.BadRequestException
import com.toscaruntime.rest.client.ToscaRuntimeClient
import com.typesafe.scalalogging.LazyLogging
import sbt.complete.DefaultParsers._
import sbt.{Command, Help}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.StdIn
import scala.language.postfixOps

/**
  * Command to interact with deployment agent
  *
  * @author Minh Khang VU
  */
object AgentCommand extends LazyLogging {

  val commandName = "agent"

  private val createCmd = "create"

  private val listCmd = "list"

  private val logCmd = "log"

  private val startCmd = "start"

  private val stopCmd = "stop"

  private val restartCmd = "restart"

  private val deleteCmd = "delete"

  private val forceOpt = "-f"

  private val installCmd = "install"

  private val cancelCmd = "cancel"

  private val resumeCmd = "resume"

  private val uninstallCmd = "uninstall"

  private val updateCmd = "update"

  private val scaleCmd = "scale"

  private val infoCmd = "info"

  private val pauseCmd = "pause"

  private val outputsOpt = "--outputs"

  private val nodesOpt = "--nodes"

  private val executionsOpt = "--executions"

  private val relationshipsOpt = "--relationships"

  private val interfaceOpt = "--interface"

  private val inputOpt = "--input"

  private val transientOpt = "--transient"

  private val ignoreDeploymentOpt = "--ignore-deployment"

  private lazy val infoNodeCmd = "infoNode"

  private lazy val infoNodeInstanceCmd = "infoNodeInstance"

  private lazy val infoExecutionCmd = "infoExecution"

  private lazy val infoRelationshipCmd = "infoRelationship"

  private lazy val infoRelationshipInstanceCmd = "infoRelationshipInstance"

  private lazy val executeNodeCmd = "executeNode"

  private lazy val executeNodeInstanceCmd = "executeNodeInstance"

  private lazy val executeRelationshipCmd = "executeRelationship"

  private lazy val executeRelationshipInstanceCmd = "executeRelationshipInstance"

  private lazy val infoOptsParser = (Space ~> (token(nodesOpt) | token(relationshipsOpt) | token(outputsOpt) | token(executionsOpt))) *

  private lazy val executeInputOptParser = token(inputOpt) ~ (token("=") ~> (token(StringBasic) ~ (token(":") ~> token(StringBasic))))

  private lazy val executeOptsParser = (Space ~> ((token(interfaceOpt) ~ (token("=") ~> token(StringBasic))) | token(transientOpt) | executeInputOptParser)) *

  private lazy val scaleCmdParser = token(scaleCmd) ~ (Space ~> token(StringBasic)) ~ (Space ~> token(StringBasic)) ~ (Space ~> token(IntBasic))

  private lazy val deleteCmdParser = token(deleteCmd) ~ (((Space ~> token(forceOpt)) | (Space ~> token(ignoreDeploymentOpt))) ?) ~ (Space ~> token(StringBasic))

  private lazy val cancelCmdParser = token(cancelCmd) ~ ((Space ~> token(forceOpt)) ?) ~ (Space ~> token(StringBasic))

  private lazy val pauseCmdParser = token(pauseCmd) ~ ((Space ~> token(forceOpt)) ?) ~ (Space ~> token(StringBasic))

  private lazy val installCmdParser = token(installCmd) ~ (Space ~> token(StringBasic))

  private lazy val uninstallCmdParser = token(uninstallCmd) ~ ((Space ~> token(forceOpt)) ?) ~ (Space ~> token(StringBasic))

  private lazy val infoCmdParser = token(infoCmd) ~ infoOptsParser ~ (Space ~> token(StringBasic))

  private lazy val infoNodeCmdParser = token(infoNodeCmd) ~ (Space ~> token(StringBasic)) ~ (Space ~> token(StringBasic))

  private lazy val executeNodeCmdParser = token(executeNodeCmd) ~ executeOptsParser ~ (Space ~> token(StringBasic)) ~ (Space ~> token(StringBasic)) ~ (Space ~> token(StringBasic))

  private lazy val infoNodeInstanceCmdParser = token(infoNodeInstanceCmd) ~ (Space ~> token(StringBasic)) ~ (Space ~> token(StringBasic))

  private lazy val executeNodeInstanceCmdParser = token(executeNodeInstanceCmd) ~ executeOptsParser ~ (Space ~> token(StringBasic)) ~ (Space ~> token(StringBasic)) ~ (Space ~> token(StringBasic))

  private lazy val infoExecutionCmdParser = token(infoExecutionCmd) ~ (Space ~> token(StringBasic)) ~ (Space ~> token(StringBasic))

  private lazy val infoRelationshipCmdParser = token(infoRelationshipCmd) ~ (Space ~> token(StringBasic)) ~ (Space ~> token(StringBasic)) ~ (Space ~> token(StringBasic)) ~ (Space ~> token(StringBasic))

  private lazy val executeRelationshipCmdParser = token(executeRelationshipCmd) ~ executeOptsParser ~ (Space ~> token(StringBasic)) ~ (Space ~> token(StringBasic)) ~ (Space ~> token(StringBasic)) ~ (Space ~> token(StringBasic)) ~ (Space ~> token(StringBasic))

  private lazy val infoRelationshipInstanceCmdParser = token(infoRelationshipInstanceCmd) ~ (Space ~> token(StringBasic)) ~ (Space ~> token(StringBasic)) ~ (Space ~> token(StringBasic)) ~ (Space ~> token(StringBasic))

  private lazy val executeRelationshipInstanceCmdParser = token(executeRelationshipInstanceCmd) ~ executeOptsParser ~ (Space ~> token(StringBasic)) ~ (Space ~> token(StringBasic)) ~ (Space ~> token(StringBasic)) ~ (Space ~> token(StringBasic)) ~ (Space ~> token(StringBasic))

  private lazy val listCmdParser = token(listCmd)

  private lazy val createCmdParser = token(createCmd) ~ (Space ~> token(StringBasic))

  private lazy val startCmdParser = token(startCmd) ~ (Space ~> token(StringBasic))

  private lazy val logCmdParser = token(logCmd) ~ (Space ~> token(StringBasic))

  private lazy val stopCmdParser = token(stopCmd) ~ (Space ~> token(StringBasic))

  private lazy val restartCmdParser = token(restartCmd) ~ (Space ~> token(StringBasic))

  private lazy val updateCmdParser = token(updateCmd) ~ (Space ~> token(StringBasic))

  private lazy val resumeCmdParser = token(resumeCmd) ~ (Space ~> token(StringBasic))

  private lazy val agentsCmdParser = Space ~>
    (listCmdParser |
      createCmdParser |
      startCmdParser |
      logCmdParser |
      stopCmdParser |
      restartCmdParser |
      updateCmdParser |
      deleteCmdParser |
      installCmdParser |
      uninstallCmdParser |
      pauseCmdParser |
      resumeCmdParser |
      cancelCmdParser |
      scaleCmdParser |
      infoCmdParser |
      infoNodeCmdParser |
      infoNodeInstanceCmdParser |
      infoExecutionCmdParser |
      infoRelationshipCmdParser |
      infoRelationshipInstanceCmdParser |
      executeNodeCmdParser |
      executeRelationshipCmdParser |
      executeRelationshipInstanceCmdParser |
      executeNodeInstanceCmdParser)

  private lazy val agentsActionsHelp = Help(commandName, (commandName, s"Interact with deployment agent, execute 'help $commandName' for more details"),
    f"""
       |$commandName <sub command> [OPTIONS] <deployment id> [ARGS]
       |
       |Sub commands:
       |
       |  $listCmd%-30s list all agents
       |
       |  $createCmd%-30s create an agent to manage the given deployment and run immediately install workflow to deploy it
       |  $synopsisToken%-30s $createCmd <deployment id>
       |
       |  $deleteCmd%-30s delete agent, if the deployment has living nodes, $uninstallCmd will be called before the deletion
       |  $synopsisToken%-30s $deleteCmd [DELETE_OPTIONS] <deployment id>
       |  DELETE_OPTIONS:
       |    $forceOpt%-28s uninstall deployment with '$uninstallCmd $forceOpt' then delete the agent
       |    $ignoreDeploymentOpt%-28s ignore the deployment, just delete the agent
       |
       |  $startCmd%-30s start agent, agent will begin to manage deployment
       |  $synopsisToken%-30s $startCmd <deployment id>
       |
       |  $stopCmd%-30s stop agent, agent will stop to manage deployment
       |  $synopsisToken%-30s $stopCmd <deployment id>
       |
       |  $restartCmd%-30s restart the agent, it's useful to refresh the agent with new recipe content
       |  $synopsisToken%-30s $restartCmd <deployment id>
       |
       |  $logCmd%-30s show the agent's log
       |  $synopsisToken%-30s $logCmd <deployment id>
       |
       |  $updateCmd%-30s update the agent's deployment recipe with the one in 'work' directory
       |  $synopsisToken%-30s $updateCmd <deployment id>

       |  $installCmd%-30s launch default install workflow
       |  $synopsisToken%-30s $installCmd <deployment id>

       |  $uninstallCmd%-30s launch default uninstall workflow
       |  $synopsisToken%-30s $uninstallCmd [UNINSTALL_OPTIONS] <deployment id>
       |  UNINSTALL_OPTIONS:
       |    $forceOpt%-28s uninstall only IAAS resources
       |
       |  $scaleCmd%-30s launch default scale workflow on the given node
       |  $synopsisToken%-30s $scaleCmd <deployment id> <node name> <instances count>
       |
       |  $pauseCmd%-30s pause current running execution, wait for all running tasks to finish and not launching new tasks
       |  $synopsisToken%-30s $pauseCmd [PAUSE_OPTIONS] <deployment id>
       |  PAUSE_OPTIONS:
       |    $forceOpt%-28s interrupt current running tasks
       |
       |  $cancelCmd%-30s cancel current running execution, wait for all running tasks to finish
       |  $synopsisToken%-30s $cancelCmd [CANCEL_OPTIONS] <deployment id>
       |  CANCEL_OPTIONS:
       |    $forceOpt%-28s interrupt all running tasks
       |
       |  $resumeCmd%-30s resume current running execution (which was stopped due to error or by $pauseCmd)
       |  $synopsisToken%-30s $resumeCmd <deployment id>
       |
       |  $infoCmd%-30s show the agent's deployment details
       |  $synopsisToken%-30s $infoCmd [INFO_OPTIONS] <deployment id>
       |  INFO_OPTIONS:
       |    $outputsOpt%-28s show only outputs details
       |    $nodesOpt%-28s show only nodes details
       |    $relationshipsOpt%-28s show only relationships details
       |    $executionsOpt%-28s show only executions details
       |
       |  $infoNodeCmd%-30s show node details
       |  $synopsisToken%-30s $infoNodeCmd <deployment id> <node id>
       |
       |  $infoNodeInstanceCmd%-30s show instance details
       |  $synopsisToken%-30s $infoNodeInstanceCmd <deployment id> <instance id>
       |
       |  $infoRelationshipCmd%-30s show relationship node details
       |  $synopsisToken%-30s $infoRelationshipCmd <deployment id> <source> <target> <relationship_type>
       |
       |  $infoRelationshipInstanceCmd%-30s show relationship instance details
       |  $synopsisToken%-30s $infoRelationshipInstanceCmd <deployment id> <source> <target> <relationship_type>
       |
       |  $infoExecutionCmd%-30s show execution details
       |  $synopsisToken%-30s $infoExecutionCmd <deployment id> <execution id>
       |
       |  $executeNodeCmd%-30s execute a particular operation on a particular node
       |  $synopsisToken%-30s $executeNodeCmd [EXECUTE_OPTIONS] <deployment id> <node id> <operation>
       |
       |  $executeNodeInstanceCmd%-30s execute a particular operation on a particular node instance
       |  $synopsisToken%-30s $executeNodeInstanceCmd [EXECUTE_OPTIONS] <deployment id> <instance id> <operation>
       |
       |  $executeRelationshipCmd%-30s execute a particular operation on a particular relationship
       |  $synopsisToken%-30s $executeRelationshipCmd [EXECUTE_OPTIONS] <deployment id> <source> <target> <relationship type> <operation>
       |
       |  $executeRelationshipInstanceCmd%-30s execute a particular operation on a particular relationship instance
       |  $synopsisToken%-30s $executeRelationshipInstanceCmd [EXECUTE_OPTIONS] <deployment id> <source instance> <target instance> <relationship type> <operation>
       |
       |  EXECUTE_OPTIONS:
       |    $inputOpt%-28s define operation input "$inputOpt=key:value" (can be repeated for multiple inputs)
       |    $transientOpt%-28s an execution is transient then can be executed even if there's unfinished execution
       |    $interfaceOpt%-28s custom interface for the operation if it's not a standard lifecycle
    """.stripMargin
  )

  def createAgent(client: ToscaRuntimeClient, deploymentId: String) = {
    val containerId = client.createDeploymentAgent(deploymentId).getId
    println(s"Deployment agent created for [$deploymentId], wait for agent REST service to be fully initialized")
    AgentUtil.waitForDeploymentAgent(client, deploymentId)
    containerId
  }

  def deploy(client: ToscaRuntimeClient, deploymentId: String) = {
    val containerId = createAgent(client, deploymentId)
    (s"Created container $containerId, " + launchInstallWorkflow(client, deploymentId), client.waitForRunningExecutionToEnd(deploymentId))
  }

  def undeploy(client: ToscaRuntimeClient, deploymentId: String, force: Boolean, teardown: Boolean): (String, Future[_]) = {
    if (force) {
      deleteAgent(client, deploymentId)
      (s"Deleted by force [$deploymentId]", Future.successful(None))
    } else {
      val details = AgentUtil.getDeploymentDetails(client, deploymentId)
      if (details.executions.nonEmpty && details.executions.head.endTime.isEmpty) {
        ("Deployment has unfinished execution, please wait or cancel execution first", Future.failed(new BadRequestException("Deployment has unfinished execution, please wait or cancel execution first")))
      } else if (AgentUtil.hasLivingNodes(details)) {
        if (teardown) {
          AgentUtil.teardownInfrastructure(client, deploymentId)
        } else {
          AgentUtil.undeploy(client, deploymentId)
        }
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

  def executeNodeOperation(client: ToscaRuntimeClient, deploymentId: String, nodeName: Option[String], instanceId: Option[String], interface: Option[String], operation: String, inputs: Map[String, String], transient: Boolean) = {
    AgentUtil.executeNodeOperation(client, deploymentId, nodeName, instanceId, interface, operation, inputs, transient)
  }

  def executeRelationshipOperation(client: ToscaRuntimeClient, deploymentId: String, sourceNodeName: Option[String], sourceInstanceId: Option[String], targetNodeName: Option[String], targetInstanceId: Option[String], relationshipType: String, interfaceName: Option[String], operationName: String, inputs: Map[String, Any], transient: Boolean) = {
    AgentUtil.executeRelationshipOperation(client, deploymentId, sourceNodeName, sourceInstanceId, targetNodeName, targetInstanceId, relationshipType, interfaceName, operationName, inputs, transient)
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

  lazy val instance = Command(commandName, agentsActionsHelp)(_ => agentsCmdParser) { (state, args) =>
    val client = state.attributes.get(Attributes.clientAttribute).get
    var fail = false
    try {
      args match {
        case (`createCmd`, deploymentId: String) =>
          println(s"Creating agent for [$deploymentId], it might take some minutes...")
          val deployResult = deploy(client, deploymentId)
          println(deployResult._1)
          println(s"Execute 'agent log $deploymentId' to tail the log of deployment agent")
        case `listCmd` =>
          AgentUtil.printDeploymentAgentsList(listAgents(client))
        case ((`infoCmd`, options: Seq[String]), deploymentId: String) =>
          if (options.nonEmpty) {
            options.foreach {
              case `nodesOpt` => AgentUtil.printNodesDetails(deploymentId, getNodesDetails(client, deploymentId))
              case `relationshipsOpt` => AgentUtil.printRelationshipsDetails(deploymentId, getRelationshipsDetails(client, deploymentId))
              case `outputsOpt` => AgentUtil.printOutputsDetails(deploymentId, getOutputsDetails(client, deploymentId))
              case `executionsOpt` => AgentUtil.printExecutionsDetails(deploymentId, getExecutionsDetails(client, deploymentId))
            }
          } else {
            AgentUtil.printDetails(client, deploymentId)
          }
        case ((`infoNodeCmd`, deploymentId: String), nodeId: String) => AgentUtil.printNodeDetails(client, deploymentId, nodeId)
        case ((`infoNodeInstanceCmd`, deploymentId: String), instanceId: String) => AgentUtil.printInstanceDetails(client, deploymentId, instanceId)
        case ((((`infoRelationshipCmd`, deploymentId: String), source: String), target: String), relationshipType: String) => AgentUtil.printRelationshipDetails(client, deploymentId, source, target, relationshipType)
        case ((((`infoRelationshipInstanceCmd`, deploymentId: String), source: String), target: String), relationshipType: String) => AgentUtil.printRelationshipInstanceDetails(client, deploymentId, source, target, relationshipType)
        case ((`infoExecutionCmd`, deploymentId: String), executionId: String) => AgentUtil.printExecutionDetails(client, deploymentId, executionId)
        case (((`scaleCmd`, deploymentId: String), nodeId: String), newInstancesCount: Int) =>
          println(scaleExecution(client, deploymentId, nodeId, newInstancesCount))
          println(s"Execute 'agent log $deploymentId' to tail the log of deployment agent")
        case ((((`executeNodeCmd`, options: Seq[(String, Object)]), deploymentId: String), nodeId: String), operation: String) =>
          println(executeNodeOperation(client, deploymentId, Some(nodeId), None, getStringOption(options, interfaceOpt), operation, getMapOption(options, inputOpt), getFlagOption(options, transientOpt)))
        case ((((`executeNodeInstanceCmd`, options: Seq[(String, Object)]), deploymentId: String), instanceId: String), operation: String) =>
          println(executeNodeOperation(client, deploymentId, None, Some(instanceId), getStringOption(options, interfaceOpt), operation, getMapOption(options, inputOpt), getFlagOption(options, transientOpt)))
        case ((((((`executeRelationshipCmd`, options: Seq[(String, Object)]), deploymentId: String), source: String), target: String), relationshipType: String), operation: String) =>
          println(executeRelationshipOperation(client, deploymentId, Some(source), None, Some(target), None, relationshipType, getStringOption(options, interfaceOpt), operation, getMapOption(options, inputOpt), getFlagOption(options, transientOpt)))
        case ((((((`executeRelationshipInstanceCmd`, options: Seq[(String, Object)]), deploymentId: String), source: String), target: String), relationshipType: String), operation: String) =>
          println(executeRelationshipOperation(client, deploymentId, None, Some(source), None, Some(target), relationshipType, getStringOption(options, interfaceOpt), operation, getMapOption(options, inputOpt), getFlagOption(options, transientOpt)))
        case (`installCmd`, deploymentId: String) =>
          println(launchInstallWorkflow(client, deploymentId))
          println(s"Execute 'agent log $deploymentId' to tail the log of deployment agent")
        case ((`cancelCmd`, force: Option[String]), deploymentId: String) =>
          println(cancelExecution(client, deploymentId, force.isDefined))
          println(s"Execute 'agent log $deploymentId' to tail the log of deployment agent")
        case (`resumeCmd`, deploymentId: String) =>
          println(resumeExecution(client, deploymentId))
          println(s"Execute 'agent log $deploymentId' to tail the log of deployment agent")
        case ((`pauseCmd`, force: Option[String]), deploymentId: String) =>
          println(stopExecution(client, deploymentId, force.isDefined))
          println(s"Execute 'agent log $deploymentId' to tail the log of deployment agent")
        case (`logCmd`, deploymentId: String) =>
          println("***Press enter to stop following logs***")
          val logCallback = client.tailLog(deploymentId, System.out)
          try {
            StdIn.readLine()
          } finally {
            logCallback.close()
          }
        case ((`uninstallCmd`, force: Option[String]), deploymentId: String) =>
          println(launchUninstallWorkflow(client, deploymentId, force.nonEmpty))
          println(s"Execute 'agent log $deploymentId' to tail the log of deployment agent")
        case (`startCmd`, deploymentId: String) =>
          startAgent(client, deploymentId)
          println(s"Started $deploymentId")
        case (`restartCmd`, deploymentId: String) =>
          restartAgent(client, deploymentId)
          println(s"Restarted $deploymentId")
        case (`updateCmd`, deploymentId: String) =>
          val basedir = state.attributes.get(Attributes.basedirAttribute).get
          println(updateDeploymentRecipe(client, deploymentId, basedir))
          println(s"Changes in scripts are immediately recognized, changes in yaml need agent restart with 'agent restart $deploymentId'")
        case (`stopCmd`, deploymentId: String) =>
          stopAgent(client, deploymentId)
          println(s"Stopped $deploymentId")
        case ((`deleteCmd`, deleteOpt: Option[String]), deploymentId: String) =>
          println(undeploy(client, deploymentId, deleteOpt.contains(ignoreDeploymentOpt), deleteOpt.contains(forceOpt))._1)
      }
    } catch {
      case e: Throwable =>
        e.printStackTrace()
        println(s"Error ${e.getMessage}, see log for stack trace")
        logger.error("Command finished with error", e)
        fail = true
    }
    if (fail) state.fail else state
  }
}
