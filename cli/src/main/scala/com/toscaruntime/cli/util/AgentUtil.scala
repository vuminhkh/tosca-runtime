package com.toscaruntime.cli.util

import java.util.concurrent.TimeUnit

import com.toscaruntime.rest.client.ToscaRuntimeClient
import com.toscaruntime.rest.model.{AbstractInstance, DeploymentDetails}
import com.toscaruntime.util.FailSafeUtil
import com.toscaruntime.util.FailSafeUtil.Action
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
object AgentUtil extends LazyLogging {

  private val waitForEver = 365 day

  def printDeploymentAgentsList(agentsList: List[List[String]]) = {
    if (agentsList.nonEmpty) {
      println("Daemon has " + agentsList.length + " deployment agent(s) : ")
      val headers = List("Deployment Id", "Status", "Created", "IP", "Container Id")
      println(TabulatorUtil.format(headers :: agentsList))
    } else {
      println("No deployment agent found")
    }
  }

  def listDeploymentAgents(client: ToscaRuntimeClient) = {
    val deploymentAgents = Await.result(client.listDeploymentAgents(), waitForEver)
    deploymentAgents.map { deployment =>
      List(deployment.name, deployment.agentStatus, deployment.agentCreated, deployment.agentIPs.values.mkString(", "), deployment.agentId)
    }
  }

  def deploy(client: ToscaRuntimeClient, deploymentId: String) = {
    Await.result(client.deploy(deploymentId), waitForEver)
  }

  def undeploy(client: ToscaRuntimeClient, deploymentId: String) = {
    Await.result(client.undeploy(deploymentId), waitForEver)
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

  def printDetails(deploymentId: String, details: DeploymentDetails): Unit = {
    printNodesDetails(deploymentId, getNodesDetails(details))
    printRelationshipsDetails(deploymentId, getRelationshipsDetails(details))
    printOutputsDetails(deploymentId, getOutputsDetails(details))
  }

  def getRelationshipsDetails(details: DeploymentDetails) = {
    details.relationships.map { relationship =>
      val initialInstancesCount = countInstances(relationship.relationshipInstances, InstanceState.INITIAL)
      val preConfiguringInstancesCount = countInstances(relationship.relationshipInstances, RelationshipInstanceState.PRE_CONFIGURING)
      val preConfiguredInstancesCount = countInstances(relationship.relationshipInstances, RelationshipInstanceState.PRE_CONFIGURED)
      val postConfiguringInstancesCount = countInstances(relationship.relationshipInstances, RelationshipInstanceState.POST_CONFIGURING)
      val postConfiguredInstancesCount = countInstances(relationship.relationshipInstances, RelationshipInstanceState.POST_CONFIGURED)
      val establishingInstancesCount = countInstances(relationship.relationshipInstances, RelationshipInstanceState.ESTABLISHING)
      val establishedInstancesCount = countInstances(relationship.relationshipInstances, RelationshipInstanceState.ESTABLISHED)
      val unlinkingInstancesCount = countInstances(relationship.relationshipInstances, RelationshipInstanceState.UNLINKING)
      List(
        relationship.sourceNodeId,
        relationship.targetNodeId,
        relationship.relationshipInstances.length.toString,
        initialInstancesCount,
        preConfiguringInstancesCount,
        preConfiguredInstancesCount,
        postConfiguringInstancesCount,
        postConfiguredInstancesCount,
        establishingInstancesCount,
        establishedInstancesCount,
        unlinkingInstancesCount
      )
    }
  }

  def printRelationshipsDetails(name: String, relationshipsData: List[List[String]]): Unit = {
    println(name + " has " + relationshipsData.length + " relationships :")
    val relationshipHeaders = List("Source", "Target", "Total Instance", "Initial", "Pre-Configuring", "Pre-Configured", "Post-Configured", "Post-Configuring", "Establishing", "Established", "Unlinking")
    println(TabulatorUtil.format(relationshipHeaders :: relationshipsData))
  }

  def hasLivingNodes(details: DeploymentDetails): Boolean = {
    details.nodes.foreach { node =>
      if (node.instances.nonEmpty) return true
    }
    details.relationships.foreach(relationship =>
      if (relationship.relationshipInstances.nonEmpty) return true
    )
    false
  }

  def getNodesDetails(details: DeploymentDetails) = {
    details.nodes.map { node =>
      val initialInstancesCount = countInstances(node.instances, InstanceState.INITIAL)
      val startingInstancesCount = countInstances(node.instances, InstanceState.STARTING)
      val startedInstancesCount = countInstances(node.instances, InstanceState.STARTED)
      val configuringInstancesCount = countInstances(node.instances, InstanceState.CONFIGURING)
      val configuredInstancesCount = countInstances(node.instances, InstanceState.CONFIGURED)
      val creatingInstancesCount = countInstances(node.instances, InstanceState.CREATING)
      val createdInstancesCount = countInstances(node.instances, InstanceState.CREATED)
      val deletingInstancesCount = countInstances(node.instances, InstanceState.DELETING)
      val stoppingInstancesCount = countInstances(node.instances, InstanceState.STOPPING)
      List(node.id, node.instances.length.toString, initialInstancesCount, creatingInstancesCount, createdInstancesCount, configuringInstancesCount, configuredInstancesCount, startingInstancesCount, startedInstancesCount, stoppingInstancesCount, deletingInstancesCount)
    }
  }

  def printNodesDetails(deploymentId: String, nodesData: List[List[String]]): Unit = {
    println(deploymentId + " has " + nodesData.length + " nodes :")
    val nodeHeaders = List("Node", "Total Instances", "Initial", "Creating", "Created", "Configuring", "Configured", "Starting", "Started", "Stopping", "Deleting")
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

  def getOutputsDetails(details: DeploymentDetails) = {
    details.outputs.map { output =>
      List(output._1, output._2.toString)
    }.toList
  }

  def printOutputsDetails(deploymentId: String, outputsData: List[List[String]]): Unit = {
    if (outputsData.nonEmpty) {
      println("Outputs for " + deploymentId + " :")
      val outputHeaders = List("Name", "Value")
      println(TabulatorUtil.format(outputHeaders :: outputsData))
    } else {
      println(deploymentId + " does not have any output")
    }
  }

  def waitForDeploymentAgent(client: ToscaRuntimeClient, deploymentId: String) = {
    FailSafeUtil.doActionWithRetry(new Action[Any] {
      override def doAction(): Any = Await.result(client.getDeploymentAgentInfo(deploymentId), waitForEver)
    }, "Wait for deployment " + deploymentId, Integer.MAX_VALUE, 2, TimeUnit.SECONDS, classOf[Throwable])
  }

  def waitForBootstrapAgent(client: ToscaRuntimeClient, provider: String, target: String) = {
    FailSafeUtil.doActionWithRetry(new Action[Any] {
      override def doAction(): Any = Await.result(client.getBootstrapAgentInfo(provider, target), waitForEver)
    }, "Wait for bootstrap " + provider, Integer.MAX_VALUE, 2, TimeUnit.SECONDS, classOf[Throwable])
  }
}
