package com.toscaruntime.it.steps

import com.toscaruntime.cli.command.AgentsCommand
import com.toscaruntime.cli.util.AgentUtil
import com.toscaruntime.constant.ExecutionConstant._
import com.toscaruntime.it.TestConstant._
import com.toscaruntime.it.Context
import org.scalatest.MustMatchers

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Steps to interact with agents
  *
  * @author Minh Khang VU
  */
object AgentsSteps extends MustMatchers {

  val NODE_STARTED = 8

  val NODE_STARTING = 7

  val RELATIONSHIP_ESTABLISHING = 9

  val RELATIONSHIP_ESTABLISHED = 10

  def cancelDeployment(deploymentId: String, force: Boolean = false) = AgentsCommand.cancelExecution(Context.client, deploymentId, force)

  def pauseDeployment(deploymentId: String, force: Boolean = false) = AgentsCommand.stopExecution(Context.client, deploymentId, force)

  def scale(deploymentId: String, nodeName: String, newInstancesCount: Int) = {
    AgentsCommand.scaleExecution(Context.client, deploymentId, nodeName, newInstancesCount)
    Await.result(Context.client.waitForRunningExecutionToEnd(deploymentId), 15 minutes)
  }

  def executeNodeOperation(deploymentId: String, nodeName: Option[String], operationName: String, instanceId: Option[String] = None, interfaceName: Option[String] = None, inputs: Map[String, String] = Map.empty, transient: Boolean = false) = {
    AgentsCommand.executeNodeOperation(Context.client, deploymentId, nodeName, instanceId, interfaceName, operationName, inputs, transient)
    Await.result(Context.client.waitForRunningExecutionToEnd(deploymentId), 15 minutes)
  }

  def executeRelationshipOperation(deploymentId: String, sourceName: Option[String], targetName: Option[String], relationshipType: String, operationName: String, sourceId: Option[String] = None, targetId: Option[String] = None, interfaceName: Option[String] = None, inputs: Map[String, String] = Map.empty, transient: Boolean = false) = {
    AgentsCommand.executeRelationshipOperation(Context.client, deploymentId, sourceName, sourceId, targetName, targetId, relationshipType, interfaceName, operationName, inputs, transient)
    Await.result(Context.client.waitForRunningExecutionToEnd(deploymentId), 15 minutes)
  }

  def assertDeploymentHasBeenStopped(deploymentId: String) = Await.result(Context.client.waitForRunningExecutionToReachStatus(deploymentId, RUNNING, STOPPED), 15 minutes)

  def assertDeploymentHasBeenStoppedWithError(deploymentId: String) = assertDeploymentHasBeenStopped(deploymentId).executions.head.error must not be empty

  def assertDeploymentHasBeenStoppedWithoutError(deploymentId: String) = assertDeploymentHasBeenStopped(deploymentId).executions.head.error must be(empty)

  def assertDeploymentFinished(deploymentId: String, timeout: Duration = 20 minutes) = Await.result(Context.client.waitForRunningExecutionToEnd(deploymentId), timeout)

  def updateRecipe(deploymentId: String) = {
    AgentUtil.updateDeploymentRecipe(Context.client, deploymentId, assemblyPath)
  }

  def launchDeployment(deploymentId: String) = {
    Await.result(AgentsCommand.deploy(Context.client, deploymentId)._2, 20 minutes)
  }

  def resumeDeployment(deploymentId: String) = {
    AgentsCommand.resumeExecution(Context.client, deploymentId)
  }

  def launchUndeployment(deploymentId: String) = {
    Await.result(AgentsCommand.undeploy(Context.client, deploymentId, force = false, teardown = false)._2, 20 minutes)
  }

  def launchInstallWorkflow(deploymentId: String) = {
    AgentsCommand.launchInstallWorkflow(Context.client, deploymentId)
    Await.result(Context.client.waitForRunningExecutionToEnd(deploymentId), 20 minutes)
  }

  def launchUninstallWorkflow(deploymentId: String) = {
    AgentsCommand.launchUninstallWorkflow(Context.client, deploymentId, force = false)
    Await.result(Context.client.waitForRunningExecutionToEnd(deploymentId), 20 minutes)
  }

  def createAgent(deploymentId: String) = {
    AgentsCommand.createAgent(Context.client, deploymentId)
  }

  def listAgents() = {
    AgentsCommand.listAgents(Context.client)
  }

  def startAgent(deploymentId: String) = {
    AgentsCommand.startAgent(Context.client, deploymentId)
  }

  def restartAgent(deploymentId: String) = {
    AgentsCommand.restartAgent(Context.client, deploymentId)
  }

  def assertDeploymentHasNode(deploymentId: String, nodeName: String, instanceCount: Int, state: Int = NODE_STARTED) = {
    val nodesFound = AgentsCommand.getNodesDetails(Context.client, deploymentId).filter(nodeDetails => nodeDetails.head == nodeName)
    nodesFound must have size 1
    // Here it means total instances == started instances
    nodesFound.head(1) must be(instanceCount.toString)
    nodesFound.head(state) must be(instanceCount.toString)
  }

  def assertDeploymentHasRelationship(deploymentId: String, sourceName: String, targetName: String, instanceCount: Int, state: Int = RELATIONSHIP_ESTABLISHED) = {
    val relationsFound = AgentsCommand.getRelationshipsDetails(Context.client, deploymentId).filter { relationshipDetails =>
      relationshipDetails.head == sourceName && relationshipDetails(1) == targetName
    }
    relationsFound must have size 1
    relationsFound.head(3) must be(instanceCount.toString)
    relationsFound.head(state) must be(instanceCount.toString)
  }

  def assertDeploymentHasOutput(deploymentId: String, key: String) = {
    val found = AgentsCommand.getOutputsDetails(Context.client, deploymentId).filter(outputEntry => outputEntry.head == key && outputEntry.last.nonEmpty)
    found must have size 1
    // Return the value for the output
    found.head.last
  }

  def stopAgent(deploymentId: String) = {
    AgentsCommand.stopAgent(Context.client, deploymentId)
  }

  def deleteAgent(deploymentId: String) = {
    AgentsCommand.deleteAgent(Context.client, deploymentId)
  }

  def assertAgentInState(deploymentId: String, state: String) = {
    AgentsCommand.listAgents(Context.client)
  }

  def assertAgentsListContain(agentList: List[List[String]], deploymentId: String, status: String) = {
    agentList.filter(info => info.head == deploymentId && info(1).startsWith(status)) must have size 1
  }

  def assertAgentsListNotContain(agentList: List[List[String]], deploymentId: String) = {
    agentList.find(info => info.head == deploymentId) must be(empty)
  }
}
