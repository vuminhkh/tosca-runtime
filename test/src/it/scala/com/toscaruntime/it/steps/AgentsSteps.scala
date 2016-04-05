package com.toscaruntime.it.steps

import com.toscaruntime.cli.command.AgentsCommand
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

  def scale(deploymentId: String, nodeName: String, newInstancesCount: Int) = {
    AgentsCommand.scaleExecution(Context.client, deploymentId, nodeName, newInstancesCount)
    Await.result(Context.client.waitForRunningExecutionToEnd(deploymentId), 15 minutes)
  }

  def executeNodeOperation(deploymentId: String, nodeName: Option[String], operationName: String, instanceId: Option[String] = None, interfaceName: Option[String] = None, inputs: Option[Map[String, String]] = None) = {
    AgentsCommand.executeNodeOperation(Context.client, deploymentId, nodeName, instanceId, interfaceName, operationName, inputs)
    Await.result(Context.client.waitForRunningExecutionToEnd(deploymentId), 15 minutes)
  }

  def launchDeployment(deploymentId: String) = {
    Await.result(AgentsCommand.deploy(Context.client, deploymentId)._2, 15 minutes)
  }

  def launchUndeployment(deploymentId: String) = {
    Await.result(AgentsCommand.undeploy(Context.client, deploymentId, force = false)._2, 15 minutes)
  }

  def launchInstallWorkflow(deploymentId: String) = {
    AgentsCommand.launchInstallWorkflow(Context.client, deploymentId)
    Await.result(Context.client.waitForRunningExecutionToEnd(deploymentId), 10 minutes)
  }

  def launchUninstallWorkflow(deploymentId: String) = {
    AgentsCommand.launchUninstallWorkflow(Context.client, deploymentId, force = false)
    Await.result(Context.client.waitForRunningExecutionToEnd(deploymentId), 10 minutes)
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

  def assertDeploymentHasNode(deploymentId: String, nodeName: String, instanceCount: Int) = {
    val nodesFound = AgentsCommand.getNodesDetails(Context.client, deploymentId).filter(nodeDetails => nodeDetails.head == nodeName)
    nodesFound must have size 1
    // Here it means total instances == started instances
    nodesFound.head(1) must be(instanceCount.toString)
    nodesFound.head(8) must be(instanceCount.toString)
  }

  def assertDeploymentHasRelationship(deploymentId: String, sourceName: String, targetName: String, instanceCount: Int) = {
    val relationsFound = AgentsCommand.getRelationshipsDetails(Context.client, deploymentId).filter { relationshipDetails =>
      relationshipDetails.head == sourceName && relationshipDetails(1) == targetName
    }
    relationsFound must have size 1
    relationsFound.head(2) must be(instanceCount.toString)
    relationsFound.head(9) must be(instanceCount.toString)
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
