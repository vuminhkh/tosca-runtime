package com.mkv.tosca.compiler

import java.nio.file.{Files, Path}

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.command.BuildImageResultCallback
import com.mkv.tosca.constant.DeployerConstant
import com.mkv.util.FileUtil
import com.typesafe.config.impl.ConfigImpl
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}

/**
 * Package a deployment as a docker container.
 *
 * @author Minh Khang VU
 */
object Packager {

  /**
   * Create a deployment agent's docker image. This method suppose that the deployment has been constructed following the tosca-runtime format.
   *
   * @param dockerClient the docker client to use
   * @param deploymentPath the path to the deployment
   * @return id of the created docker image
   */
  def createDockerImage(dockerClient: DockerClient, deploymentPath: Path) = {
    val tempDockerImageBuildDir = Files.createTempDirectory("tosca")
    val deploymentName = ConfigFactory
      .parseFile(deploymentPath.resolve("deployment").resolve("deployment.conf").toFile).resolveWith(ConfigImpl.systemPropertiesAsConfig())
      .getString(DeployerConstant.DEPLOYMENT_NAME_KEY)
    FileUtil.copy(deploymentPath, tempDockerImageBuildDir)
    Files.copy(Thread.currentThread().getContextClassLoader.getResourceAsStream("Dockerfile"), tempDockerImageBuildDir.resolve("Dockerfile"))
    (dockerClient.buildImageCmd(tempDockerImageBuildDir.toFile).withTag(deploymentName).withNoCache.exec(new BuildImageResultCallback).awaitImageId, deploymentName)
  }

  /**
   * Create a deployment agent's docker image with custom setup
   *
   * @param dockerClient the docker client to use
   * @param deploymentName the name of the deployment
   * @param recipePath the path of the recipe
   * @param providerConfigPath the path for the configuration of the provider
   * @param inputsPath inputs for the deployment
   * @return id of the created docker image
   */
  def createDockerImage(dockerClient: DockerClient, deploymentName: String, recipePath: Path, inputsPath: Option[Path], providerConfigPath: Path) = {
    val tempDockerImageBuildDir = Files.createTempDirectory("tosca")
    val tempRecipePath = tempDockerImageBuildDir.resolve("deployment").resolve("recipe")
    FileUtil.copy(recipePath, tempRecipePath)
    Files.copy(Thread.currentThread().getContextClassLoader.getResourceAsStream("Dockerfile"), tempDockerImageBuildDir.resolve("Dockerfile"))
    val deploymentConfig = ConfigFactory.empty()
    deploymentConfig.withValue(DeployerConstant.DEPLOYMENT_NAME_KEY, ConfigValueFactory.fromAnyRef(deploymentName))
    FileUtil.writeTextFile(deploymentConfig.root().render(), tempDockerImageBuildDir.resolve("deployment").resolve("deployment.conf"))
    FileUtil.copy(providerConfigPath, tempDockerImageBuildDir.resolve("provider").resolve("provider.conf"))
    if (inputsPath.isEmpty) {
      FileUtil.touch(tempDockerImageBuildDir.resolve("deployment").resolve("inputs.yaml"))
    } else {
      FileUtil.copy(inputsPath.get, tempDockerImageBuildDir.resolve("deployment").resolve("inputs.yaml"))
    }
    (dockerClient.buildImageCmd(tempDockerImageBuildDir.toFile).withTag(deploymentName).withNoCache.exec(new BuildImageResultCallback).awaitImageId, deploymentName)
  }
}
