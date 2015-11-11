package com.toscaruntime.cli.util

import com.github.dockerjava.api.DockerClient
import com.google.common.collect.Lists
import com.toscaruntime.util.DockerUtil

/**
 * @author Minh Khang VU
 */
object DeployUtil {

  val deploymentURL = "http://0.0.0.0:9000/deployment"

  val getDeploymentCommand = Lists.newArrayList("curl", "-X", "GET", "http://0.0.0.0:9000/deployment")

  val launchDeploymentCommand = Lists.newArrayList("curl", "-X", "POST", "http://0.0.0.0:9000/deployment")

  val deleteDeploymentCommand = Lists.newArrayList("curl", "-X", "DELETE", "http://0.0.0.0:9000/deployment")

  // TODO Use curl to launch deployment, may we have more elegant ways
  def deploy(dockerClient: DockerClient, containerId: String) = {
    DockerUtil.runCommand(dockerClient, containerId, launchDeploymentCommand)
  }

  def undeploy(dockerClient: DockerClient, containerId: String) = {
    DockerUtil.runCommand(dockerClient, containerId, deleteDeploymentCommand)
  }

  def getDetails(dockerClient: DockerClient, containerId: String) = {
    DockerUtil.runCommand(dockerClient, containerId, getDeploymentCommand, null)
  }

  def waitForDeploymentAgent(dockerClient: DockerClient, containerId: String) = {
    val maxRetryCount = 60
    var retryCount = 0
    var webAppUp = false
    while (!webAppUp) {
      try {
        // Try to get the deployment information just to be sure that the web app is up
        DockerUtil.runCommand(dockerClient, containerId, getDeploymentCommand, null)
        webAppUp = true
      } catch {
        case t: Throwable =>
          retryCount += 1
          if (retryCount < maxRetryCount) {
            println("Attempt " + retryCount + " to launch bootstrap, web app may not be up yet " + t.getMessage)
            Thread.sleep(1000L)
          } else {
            throw t
          }
      }
    }
  }
}
