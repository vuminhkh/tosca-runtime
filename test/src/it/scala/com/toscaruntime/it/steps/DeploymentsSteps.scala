package com.toscaruntime.it.steps

import java.nio.file.Path

import com.toscaruntime.cli.command.{BootStrapCommand, DeploymentsCommand}
import com.toscaruntime.cli.util.AgentUtil
import com.toscaruntime.it.Context
import com.toscaruntime.it.TestConstant._
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.MustMatchers

object DeploymentsSteps extends MustMatchers with LazyLogging {

  private def getTopologyPath(name: String, provider: String, config: String) = {
    csarsPath.resolve(provider).resolve(config).resolve(name)
  }

  def createDeploymentImage(name: String, provider: String = dockerProvider, config: String = standalone, input: Option[Path] = None, deploymentId: Option[String] = None) = {
    val topologyPath = getTopologyPath(name, provider, config)
    val inputOpt = input.orElse(Context.getInput(provider))
    DeploymentsCommand.createDeploymentImage(
      topologyPath,
      inputOpt,
      repositoryPath,
      assemblyPath,
      deploymentId.getOrElse(name),
      Context.client,
      testProvidersConfigPath,
      testPluginsConfigPath,
      Some(config == standalone)
    )
  }

  def bootstrap(provider: String = openstackProvider, target: String = swarmTarget) = {
    BootStrapCommand.createBootstrapAgent(provider,
      target,
      Context.client,
      assemblyPath,
      bootstrapPath.resolve(provider).resolve(target).resolve("archive"),
      testProvidersConfigPath.resolve(provider),
      repositoryPath,
      Context.getInput(provider)
    )
    AgentUtil.waitForBootstrapAgent(Context.client, provider, target)
    AgentUtil.bootstrap(Context.client, provider, target).outputs("public_daemon_url").asInstanceOf[String]
  }

  def listDeploymentImages() = {
    DeploymentsCommand.listDeploymentImages(Context.client)
  }

  def assertDeploymentImageListContain(list: List[List[String]], deploymentName: String) = {
    val found = list.filter { deploymentImageInfo =>
      deploymentImageInfo.head == deploymentName
    }
    found must have size 1
  }

  def assertDeploymentImageListNotContain(list: List[List[String]], deploymentName: String) = {
    list.find { deploymentImageInfo =>
      deploymentImageInfo.head == deploymentName
    } must be(empty)
  }

  def deleteDeploymentImage(deploymentName: String) = {
    DeploymentsCommand.deleteDeploymentImage(Context.client, deploymentName)
  }
}
