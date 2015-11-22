package com.toscaruntime.cli.util

import com.toscaruntime.rest.client.ToscaRuntimeClient
import com.toscaruntime.util.RetryUtil
import com.toscaruntime.util.RetryUtil.Action
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Deployment utilities
  *
  * @author Minh Khang VU
  */
object DeployUtil extends LazyLogging {

  private val waitForEver = 365 day

  def list(client: ToscaRuntimeClient) = {
    val deployments = Await.result(client.listDeployments(), waitForEver)
    println("Daemon has " + deployments.length + " deployment agents : ")
    deployments.foreach { deployment =>
      println(deployment.name + "\t\t" + deployment.agentStatus + "\t\t" + deployment.agentCreated + "\t\t" + deployment.agentIP + "\t\t" + deployment.agentId)
    }
  }

  def deploy(client: ToscaRuntimeClient, deploymentId: String) = {
    Await.result(client.deploy(deploymentId), waitForEver)
  }

  def undeploy(client: ToscaRuntimeClient, deploymentId: String) = {
    Await.result(client.undeploy(deploymentId), waitForEver)
  }

  def printDetails(client: ToscaRuntimeClient, deploymentId: String) = {
    val details = Await.result(client.getDeploymentInformation(deploymentId), waitForEver).data.get
    println("Deployment " + deploymentId + " has " + details.nodes.length + " nodes : ")
    details.nodes.foreach { node =>
      println(" - Node " + node.id + " has " + node.instances.length)
      node.instances.foreach { instance =>
        println(" \t+ " + instance.id + ": " + instance.state)
      }
    }
    println("Output for " + deploymentId + " : ")
    details.outputs.foreach { output =>
      println(output._1 + " = " + output._2)
    }
  }

  def waitForDeploymentAgent(client: ToscaRuntimeClient, deploymentId: String) = {
    RetryUtil.doActionWithRetry(new Action[Any] {
      override def getName: String = "Wait for deployment " + deploymentId

      override def doAction(): Any = Await.result(client.getDeploymentInformation(deploymentId), waitForEver)
    }, Integer.MAX_VALUE, 2000L, classOf[Throwable])
  }
}
