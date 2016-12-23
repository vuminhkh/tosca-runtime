package com.toscaruntime.it.steps

import java.nio.file.Path

import com.toscaruntime.cli.command.{BootStrapCommand, DeploymentCommand, TeardownCommand}
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
    DeploymentCommand.createDeploymentImage(
      topologyPath,
      inputOpt,
      repositoryPath,
      assemblyPath,
      deploymentId.getOrElse(name),
      Context.config.getString("deployer.image"),
      Context.client,
      testProvidersConfigPath,
      testPluginsConfigPath,
      Some(config == standalone)
    )
  }

  def bootstrap(provider: String = openstackProvider, target: String = swarmTarget) = {
    BootStrapCommand.createBootstrapAgent(provider,
      Context.config.getString("deployer.image"),
      target,
      Context.client,
      assemblyPath,
      bootstrapPath.resolve(provider).resolve(target).resolve("archive"),
      testProvidersConfigPath.resolve(provider),
      testPluginsConfigPath,
      repositoryPath,
      Context.getInput("bootstrap_" + provider)
    )
    AgentUtil.waitForBootstrapAgent(Context.client, provider, target)
    AgentUtil.bootstrap(Context.client, provider, target).outputs("public_docker_daemon_host").asInstanceOf[String]
  }

  def teardown(provider: String = openstackProvider, target: String = swarmTarget) = {
    TeardownCommand.teardown(Context.client, provider, target)
  }

  def listDeploymentImages() = {
    DeploymentCommand.listDeploymentImages(Context.client)
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
    DeploymentCommand.deleteDeploymentImage(Context.client, deploymentName)
  }
}
