package com.mkv.tosca.compiler

import java.nio.file.{Files, Path}

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.command.BuildImageResultCallback
import com.mkv.tosca.constant.DeployerConstant
import com.mkv.util.FileUtil
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}

/**
 * Package a deployment as a docker container.
 *
 * @author Minh Khang VU
 */
object Packager {

  /**
   * Create a deployment agent's docker image
   *
   * @param dockerClient the docker client to use
   * @param deploymentName the name of the deployment
   * @param recipePath the path of the recipe
   * @param providerConfig configuration of the provider
   * @param inputsPath inputs for the deployment
   * @return id of the created docker image
   */
  def createDockerImage(dockerClient: DockerClient, deploymentName: String, recipePath: Path, inputsPath: Option[Path], providerConfig: Config) = {
    val tempDockerImageBuildDir = Files.createTempDirectory("tosca")
    FileUtil.copy(recipePath, tempDockerImageBuildDir.resolve("deployment").resolve("recipe"))
    val deploymentConfig = ConfigFactory.empty()
    deploymentConfig.withValue(DeployerConstant.DEPLOYMENT_NAME_KEY, ConfigValueFactory.fromAnyRef(deploymentName))
    FileUtil.writeTextFile(deploymentConfig.root().render(), tempDockerImageBuildDir.resolve("deployment").resolve("deployment.conf"))
    FileUtil.writeTextFile(providerConfig.root().render(), tempDockerImageBuildDir.resolve("provider").resolve("provider.conf"))
    if (inputsPath.isEmpty) {
      FileUtil.touch(tempDockerImageBuildDir.resolve("deployment").resolve("inputs.yaml"))
    } else {
      FileUtil.copy(inputsPath.get, tempDockerImageBuildDir.resolve("deployment").resolve("inputs.yaml"))
    }
    dockerClient.buildImageCmd(recipePath.toFile).withTag(deploymentName).withNoCache.exec(new BuildImageResultCallback).awaitImageId
  }
}
