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

  def createAgent(deploymentId: String) = {
    AgentsCommand.createAgent(Context.client, deploymentId)
  }

  def listAgents() = {
    AgentsCommand.listAgents(Context.client)
  }

  def startAgent(deploymentId: String) = {
    AgentsCommand.start(Context.client, deploymentId)
  }

  def launchInstallWorkflow(deploymentId: String) = {
    Await.result(AgentsCommand.launchInstallWorkflow(Context.client, deploymentId), 5 minutes)
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
    relationsFound.head.last must be(instanceCount.toString)
  }

  def assertDeploymentHasOutput(deploymentId: String, key: String) = {
    AgentsCommand.getOutputsDetails(Context.client, deploymentId).filter(outputEntry => outputEntry.head == key && outputEntry.last.nonEmpty) must have size 1
  }

  def launchUninstallWorkflow(deploymentId: String) = {
    Await.result(AgentsCommand.launchUninstallWorkflow(Context.client, deploymentId), 5 minutes)
  }

  def stopAgent(deploymentId: String) = {
    AgentsCommand.stop(Context.client, deploymentId)
  }

  def deleteAgent(deploymentId: String) = {
    AgentsCommand.delete(Context.client, deploymentId)
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
