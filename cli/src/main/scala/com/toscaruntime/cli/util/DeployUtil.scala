package com.toscaruntime.cli.util

import com.toscaruntime.rest.client.ToscaRuntimeClient
import com.toscaruntime.rest.model.DeploymentDetails
import com.toscaruntime.util.RetryUtil
import com.toscaruntime.util.RetryUtil.Action
import com.typesafe.scalalogging.LazyLogging

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

  def printDetails(client: ToscaRuntimeClient, deploymentId: String): Unit = {
    val details = Await.result(client.getDeploymentAgentInfo(deploymentId), waitForEver)
    printDetails("Deployment " + deploymentId, details)
  }

  def printDetails(name: String, details: DeploymentDetails): Unit = {
    println(name + " has " + details.nodes.length + " nodes :")
    details.nodes.foreach { node =>
      println(" - Node " + node.id + " has " + node.instances.length + " instances :")
      node.instances.foreach { instance =>
        println(" \t+ " + instance.id + ": " + instance.state)
      }
    }
    if (details.outputs.nonEmpty) {
      println("Output for " + name + " :")
      details.outputs.foreach { output =>
        println(" - " + output._1 + " = " + output._2)
      }
    } else {
      println(name + " does not have any output")
    }
  }

  def waitForDeploymentAgent(client: ToscaRuntimeClient, deploymentId: String) = {
    RetryUtil.doActionWithRetry(new Action[Any] {
      override def doAction(): Any = Await.result(client.getDeploymentAgentInfo(deploymentId), waitForEver)
    }, "Wait for deployment " + deploymentId, Integer.MAX_VALUE, 2000L, classOf[Throwable])
  }

  def waitForBootstrapAgent(client: ToscaRuntimeClient, provider: String, target: String) = {
    RetryUtil.doActionWithRetry(new Action[Any] {
      override def doAction(): Any = Await.result(client.getBootstrapAgentInfo(provider, target), waitForEver)
    }, "Wait for bootstrap " + provider, Integer.MAX_VALUE, 2000L, classOf[Throwable])
  }
}
