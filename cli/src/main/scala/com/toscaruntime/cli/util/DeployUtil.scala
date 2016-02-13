package com.toscaruntime.cli.util

import com.toscaruntime.rest.client.ToscaRuntimeClient
import com.toscaruntime.rest.model.{AbstractInstance, DeploymentDetails}
import com.toscaruntime.util.FailSafeUtil
import FailSafeUtil.Action
import com.typesafe.scalalogging.LazyLogging
import tosca.constants.{InstanceState, RelationshipInstanceState}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Deployment utilities
  *
  * @author Minh Khang VU
  */
object DeployUtil extends LazyLogging {

  private val waitForEver = 365 day

  def listDeploymentAgents(client: ToscaRuntimeClient) = {
    val deploymentAgents = Await.result(client.listDeploymentAgents(), waitForEver)
    if (deploymentAgents.nonEmpty) {
      println("Daemon has " + deploymentAgents.length + " deployment agent(s) : ")
      val headers = List("Deployment Id", "Status", "Created", "IP", "Container Id")
      val deploymentsData = deploymentAgents.map { deployment =>
        List(deployment.name, deployment.agentStatus, deployment.agentCreated, deployment.agentIPs.values.mkString(", "), deployment.agentId)
      }
      println(TabulatorUtil.format(headers :: deploymentsData))
    } else {
      println("No deployment agent found")
    }
  }

  def deploy(client: ToscaRuntimeClient, deploymentId: String) = {
    Await.result(client.deploy(deploymentId), waitForEver)
  }

  def bootstrap(client: ToscaRuntimeClient, provider: String, target: String) = {
    Await.result(client.bootstrap(provider, target), waitForEver)
  }

  def teardown(client: ToscaRuntimeClient, provider: String, target: String) = {
    Await.result(client.teardown(provider, target), waitForEver)
  }

  def getDeploymentDetails(client: ToscaRuntimeClient, deploymentId: String) = {
    Await.result(client.getDeploymentAgentInfo(deploymentId), waitForEver)
  }

  def printDetails(client: ToscaRuntimeClient, deploymentId: String): Unit = {
    val details = getDeploymentDetails(client, deploymentId)
    printDetails("Deployment " + deploymentId, details)
  }

  private def countInstances(instances: List[AbstractInstance], state: String) = {
    instances.foldLeft(0)((instanceCount, instance) => if (instance.state == state) instanceCount + 1 else instanceCount).toString
  }

  def printDetails(name: String, details: DeploymentDetails): Unit = {
    printNodesDetails(name, details)
    printRelationshipsDetails(name, details)
    printOutputsDetails(name, details)
  }

  def printRelationshipsDetails(client: ToscaRuntimeClient, deploymentId: String): Unit = {
    printRelationshipsDetails("Deployment " + deploymentId, getDeploymentDetails(client, deploymentId))
  }

  def printRelationshipsDetails(name: String, details: DeploymentDetails): Unit = {
    println(name + " has " + details.relationships.length + " relationships :")
    val relationshipHeaders = List("Source", "Target", "Total Instance", "Pre-Configured", "Post-Configured", "Established")
    val relationshipsData = details.relationships.map { relationship =>
      val preConfiguredInstancesCount = countInstances(relationship.relationshipInstances, RelationshipInstanceState.PRE_CONFIGURED)
      val postConfiguredInstancesCount = countInstances(relationship.relationshipInstances, RelationshipInstanceState.POST_CONFIGURED)
      val establishedConfiguredInstancesCount = countInstances(relationship.relationshipInstances, RelationshipInstanceState.ESTABLISHED)
      List(relationship.sourceNodeId, relationship.targetNodeId, relationship.relationshipInstances.length.toString, preConfiguredInstancesCount, postConfiguredInstancesCount, establishedConfiguredInstancesCount)
    }
    println(TabulatorUtil.format(relationshipHeaders :: relationshipsData))
  }

  def printNodesDetails(client: ToscaRuntimeClient, deploymentId: String): Unit = {
    printNodesDetails("Deployment " + deploymentId, getDeploymentDetails(client, deploymentId))
  }

  def printNodesDetails(name: String, details: DeploymentDetails): Unit = {
    println(name + " has " + details.nodes.length + " nodes :")
    val nodeHeaders = List("Node", "Total Instances", "Created", "Configured", "Started")
    val nodesData = details.nodes.map { node =>
      val startedInstancesCount = countInstances(node.instances, InstanceState.STARTED)
      val configuredInstancesCount = countInstances(node.instances, InstanceState.CONFIGURED)
      val createdInstancesCount = countInstances(node.instances, InstanceState.CREATED)
      List(node.id, node.instances.length.toString, createdInstancesCount, configuredInstancesCount, startedInstancesCount)
    }
    println(TabulatorUtil.format(nodeHeaders :: nodesData))
  }

  private def printProperties(properties: Map[String, Any]): Unit = {
    val propertiesData = properties.map {
      case (key, value) => List(key, value.toString)
    }.toList
    println(TabulatorUtil.format(List("Key", "Value") :: propertiesData))
  }

  def printNodeDetails(client: ToscaRuntimeClient, deploymentId: String, nodeId: String): Unit = {
    val deploymentDetails = getDeploymentDetails(client, deploymentId)
    deploymentDetails.nodes.find(_.id == nodeId) match {
      case Some(node) =>
        if (node.properties.nonEmpty) {
          println(s"The node $nodeId has following properties:")
          printProperties(node.properties)
        }
        println(s"The node $nodeId has ${node.instances.length} instances")
        val instancesData = node.instances.map { instance =>
          List(instance.id, instance.state, instance.attributes.size.toString)
        }
        println(TabulatorUtil.format(List("Id", "State", "Attributes") :: instancesData))
      case None =>
        println(s"Node $nodeId not found in the deployment [$deploymentId] ")
    }
  }

  def printInstanceDetails(client: ToscaRuntimeClient, deploymentId: String, instanceId: String): Unit = {
    val deploymentDetails = getDeploymentDetails(client, deploymentId)
    deploymentDetails.nodes.flatMap { node =>
      node.instances.find(_.id == instanceId)
    }.headOption match {
      case Some(instance) =>
        println(s"The instance [$instanceId] is in state ${instance.state}")
        if (instance.attributes.nonEmpty) {
          println(s"The instance [$instanceId] has following attributes:")
          printProperties(instance.attributes)
        }
      case None =>
        println(s"The instance [$instanceId] is not found in the deployment [$deploymentId] ")
    }
  }

  def printRelationshipInstanceDetails(client: ToscaRuntimeClient, deploymentId: String, source: String, target: String): Unit = {
    val deploymentDetails = getDeploymentDetails(client, deploymentId)
    deploymentDetails.relationships.flatMap { relationship =>
      relationship.relationshipInstances.find(relationshipInstance =>
        relationshipInstance.sourceInstanceId == source && relationshipInstance.targetInstanceId == target
      )
    }.headOption match {
      case Some(relationshipInstance) =>
        println(s"The relationship instance from [$source] to [$target] is in state ${relationshipInstance.state}")
        if (relationshipInstance.attributes.nonEmpty) {
          println(s"The relationship instance from [$source] to [$target] has following attributes:")
          printProperties(relationshipInstance.attributes)
        }
      case None =>
        println(s"The relationship instance from [$source] to [$target] is not found in the deployment [$deploymentId] ")
    }
  }

  def printRelationshipDetails(client: ToscaRuntimeClient, deploymentId: String, source: String, target: String): Unit = {
    val deploymentDetails = getDeploymentDetails(client, deploymentId)
    deploymentDetails.relationships.find(relationship => relationship.sourceNodeId == source && relationship.targetNodeId == target) match {
      case Some(relationship) =>
        if (relationship.properties.nonEmpty) {
          println(s"The relationship from [$source] to [$target] has following properties:")
          printProperties(relationship.properties)
        }
        println(s"The relationship from [$source] to [$target] has following instances:")
        val instancesData = relationship.relationshipInstances.map { relationshipInstance =>
          List(relationshipInstance.sourceInstanceId, relationshipInstance.targetInstanceId, relationshipInstance.state, relationshipInstance.attributes.size.toString)
        }
        println(TabulatorUtil.format(List("Source Id", "Target Id", "State", "Attributes") :: instancesData))
      case None =>
        println(s"The relationship from [$source] to [$target] is not found in the deployment [$deploymentId] ")
    }
  }

  def printOutputsDetails(client: ToscaRuntimeClient, deploymentId: String): Unit = {
    printOutputsDetails("Deployment " + deploymentId, getDeploymentDetails(client, deploymentId))
  }

  def printOutputsDetails(name: String, details: DeploymentDetails): Unit = {
    if (details.outputs.nonEmpty) {
      println("Outputs for " + name + " :")
      val outputHeaders = List("Name", "Value")
      val outputsData = details.outputs.map { output =>
        List(output._1, output._2.toString)
      }.toList
      println(TabulatorUtil.format(outputHeaders :: outputsData))
    } else {
      println(name + " does not have any output")
    }
  }

  def waitForDeploymentAgent(client: ToscaRuntimeClient, deploymentId: String) = {
    FailSafeUtil.doActionWithRetry(new Action[Any] {
      override def doAction(): Any = Await.result(client.getDeploymentAgentInfo(deploymentId), waitForEver)
    }, "Wait for deployment " + deploymentId, Integer.MAX_VALUE, 2000L, classOf[Throwable])
  }

  def waitForBootstrapAgent(client: ToscaRuntimeClient, provider: String, target: String) = {
    FailSafeUtil.doActionWithRetry(new Action[Any] {
      override def doAction(): Any = Await.result(client.getBootstrapAgentInfo(provider, target), waitForEver)
    }, "Wait for bootstrap " + provider, Integer.MAX_VALUE, 2000L, classOf[Throwable])
  }
}
