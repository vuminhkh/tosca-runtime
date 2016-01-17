package com.toscaruntime.rest.client

import java.io.{FileWriter, PrintStream}
import java.net.URL
import java.nio.file.{Files, Path, StandardOpenOption}

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.InspectContainerResponse
import com.github.dockerjava.api.model._
import com.github.dockerjava.core.command.{BuildImageResultCallback, LogContainerResultCallback}
import com.google.common.collect.Maps
import com.toscaruntime.constant.{DeployerConstant, RuntimeConstant}
import com.toscaruntime.exception.DaemonResourcesNotFoundException
import com.toscaruntime.rest.model.DeploymentInfo
import com.toscaruntime.util.{DockerUtil, FileUtil}
import com.typesafe.config.impl.ConfigImpl
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import com.typesafe.scalalogging.LazyLogging
import org.yaml.snakeyaml.Yaml

import scala.collection.JavaConverters._

/**
  * A client to interact with docker daemon
  *
  * @author Minh Khang VU
  */
class DockerDaemonClient(var url: String, var certPath: String) extends LazyLogging {

  var dockerClient: DockerClient = DockerUtil.buildDockerClient(url, certPath)

  def setDockerClient(newUrl: String, newCertPath: String) = {
    dockerClient.close()
    dockerClient = DockerUtil.buildDockerClient(newUrl, newCertPath)
    url = newUrl
    certPath = newCertPath
  }

  private def findMappedPort(container: InspectContainerResponse, localPort: Int) = {
    container.getNetworkSettings.getPorts.getBindings.asScala.filterKeys(exposedPort => exposedPort.getProtocol == InternetProtocol.TCP && exposedPort.getPort == localPort).values.head.head.getHostPort
  }

  def getProxyURL: Option[String] = {
    val filters = new Filters().withLabels(RuntimeConstant.COMPONENT_TYPE_LABEL + "=" + RuntimeConstant.PROXY_TYPE_VALUE)
    val proxyFound = dockerClient.listContainersCmd().withFilters(filters).withLimit(1).exec()
    if (proxyFound.isEmpty) {
      None
    } else {
      val proxy = proxyFound.iterator().next()
      val proxyPort = findMappedPort(dockerClient.inspectContainerCmd(proxy.getId).exec(), 9000)
      // TODO the public ip to join the container should be discovered more dynamically ?
      Some("http://" + new URL(url).getHost + ":" + proxyPort)
    }
  }

  def getAgent(deploymentId: String) = {
    val filters = new Filters().withLabels(RuntimeConstant.DEPLOYMENT_ID_LABEL + "=" + deploymentId)
    // TODO container that has been stopped, must be filtered out or status should be sent back
    val containers = dockerClient.listContainersCmd().withShowAll(true).withFilters(filters).exec().asScala
    containers.headOption
  }

  def getAgentInfo(deploymentId: String) = {
    getAgent(deploymentId).map { container =>
      dockerClient.inspectContainerCmd(container.getId).exec()
    }
  }

  def getBootstrapAgentURL(deploymentId: String) = {
    getAgentInfo(deploymentId).filter(_.getState.getRunning).map { container =>
      val daemonHost = DockerUtil.getDockerHostIP(url)
      val port = container.getNetworkSettings.getPorts.getBindings.asScala.filterKeys(exposedPort => exposedPort.getProtocol == InternetProtocol.TCP && exposedPort.getPort == 9000).values.head.head.getHostPort
      s"http://$daemonHost:$port/deployment"
    }
  }

  def listDeploymentAgents() = {
    val filters = new Filters().withLabels(RuntimeConstant.ORGANIZATION_LABEL + "=" + RuntimeConstant.ORGANIZATION_VALUE)
    // TODO container that has been stopped, must be filtered out or status should be sent back
    val containers = dockerClient.listContainersCmd().withShowAll(true).withFilters(filters).exec().asScala
    containers.map { container =>
      val containerInfo = dockerClient.inspectContainerCmd(container.getId).exec
      val deploymentId = container.getLabels.get(RuntimeConstant.DEPLOYMENT_ID_LABEL)
      val ipAddresses = containerInfo.getNetworkSettings.getNetworks.asScala.map {
        case (networkName: String, network: InspectContainerResponse.Network) => (networkName, network.getIpAddress)
      }.toMap
      val deploymentInfo = DeploymentInfo(deploymentId, container.getNames.head, containerInfo.getId, containerInfo.getCreated, container.getStatus, ipAddresses)
      (deploymentId, deploymentInfo)
    }.toMap
  }

  def copyDockerFile(outputPath: Path, deploymentId: String) = {
    Files.copy(Thread.currentThread().getContextClassLoader.getResourceAsStream("Dockerfile"), outputPath)
    Files.write(outputPath, ("LABEL " + RuntimeConstant.DEPLOYMENT_ID_LABEL + "=" + deploymentId + "\n").getBytes("UTF-8"), StandardOpenOption.APPEND)
  }

  val yaml = new Yaml()

  /**
    * Create a deployment agent's docker image. This method suppose that the deployment has been constructed following the tosca-runtime format.
    *
    * @param deploymentPath the path to the deployment
    * @return id of the created docker image
    */
  def createAgentImage(deploymentPath: Path, bootstrapContext: Map[String, Any]) = {
    var realDeploymentPath = deploymentPath
    val deploymentIsZipped = Files.isRegularFile(deploymentPath)
    if (deploymentIsZipped) {
      realDeploymentPath = Files.createTempDirectory("toscaruntime")
      FileUtil.unzip(deploymentPath, realDeploymentPath)
    }
    val tempDockerImageBuildDir = Files.createTempDirectory("tosca")
    val deploymentId = ConfigFactory
      .parseFile(realDeploymentPath.resolve("deployment").resolve("deployment.conf").toFile).resolveWith(ConfigImpl.systemPropertiesAsConfig())
      .getString(DeployerConstant.DEPLOYMENT_NAME_KEY)
    FileUtil.copy(realDeploymentPath, tempDockerImageBuildDir)
    copyDockerFile(tempDockerImageBuildDir.resolve("Dockerfile"), deploymentId)
    if (bootstrapContext.nonEmpty) {
      yaml.dump(bootstrapContext.asJava, new FileWriter(tempDockerImageBuildDir.resolve("bootstrapContext.yaml").toFile))
    }
    (dockerClient.buildImageCmd(tempDockerImageBuildDir.toFile).withTag("toscaruntime/deployment_" + deploymentId).withNoCache(true).exec(new BuildImageResultCallback).awaitImageId, deploymentId)
  }

  /**
    * Create a deployment agent's docker image with custom setup
    *
    * @param deploymentId the name of the deployment
    * @param recipePath the path of the recipe
    * @param providerConfigPath the path for the configuration of the provider
    * @param inputsPath inputs for the deployment
    * @return id of the created docker image
    */
  def createAgentImage(deploymentId: String, bootstrap: Boolean, recipePath: Path, inputsPath: Option[Path], providerConfigPath: Path, bootstrapContext: Map[String, Any]) = {
    val tempDockerImageBuildDir = Files.createTempDirectory("tosca")
    val tempRecipePath = tempDockerImageBuildDir.resolve("deployment").resolve("recipe")
    val recipeIsZipped = Files.isRegularFile(recipePath)
    var realRecipePath = recipePath
    if (recipeIsZipped) {
      realRecipePath = FileUtil.createZipFileSystem(recipePath)
    }
    try {
      FileUtil.copy(realRecipePath, tempRecipePath)
    } finally {
      if (recipeIsZipped) {
        realRecipePath.getFileSystem.close()
      }
    }
    copyDockerFile(tempDockerImageBuildDir.resolve("Dockerfile"), deploymentId)
    val deploymentConfig = ConfigFactory.empty()
      .withValue(DeployerConstant.DEPLOYMENT_NAME_KEY, ConfigValueFactory.fromAnyRef(deploymentId))
      .withValue(DeployerConstant.BOOTSTRAP_KEY, ConfigValueFactory.fromAnyRef(bootstrap))
    FileUtil.writeTextFile(deploymentConfig.root().render(), tempDockerImageBuildDir.resolve("deployment").resolve("deployment.conf"))
    FileUtil.copy(providerConfigPath, tempDockerImageBuildDir.resolve("provider"))
    if (inputsPath.nonEmpty) {
      FileUtil.copy(inputsPath.get, tempDockerImageBuildDir.resolve("deployment").resolve("inputs.yaml"))
    }
    if (bootstrapContext.nonEmpty) {
      yaml.dump(bootstrapContext.asJava, new FileWriter(tempDockerImageBuildDir.resolve("bootstrapContext.yaml").toFile))
    }
    dockerClient.buildImageCmd(tempDockerImageBuildDir.toFile).withTag("toscaruntime/deployment_" + deploymentId).withNoCache(true).exec(new BuildImageResultCallback)
  }

  def getAgentImage(deploymentId: String) = {
    val images = dockerClient.listImagesCmd().withFilters("{\"label\":[\"" + RuntimeConstant.DEPLOYMENT_ID_LABEL + "=" + deploymentId + "\"]}").exec().asScala
      .filter(image => image.getRepoTags != null && image.getRepoTags.nonEmpty && !image.getRepoTags()(0).equals("<none>:<none>"))
    images.headOption
  }

  def listDeploymentImages() = {
    val images = dockerClient.listImagesCmd()
      .withFilters("{\"label\":[\"" + RuntimeConstant.ORGANIZATION_LABEL + "=" + RuntimeConstant.ORGANIZATION_VALUE + "\"]}").exec().asScala
      .filter(image => image.getRepoTags != null && image.getRepoTags.nonEmpty && !image.getRepoTags()(0).equals("<none>:<none>"))
    images.toList.map { image =>
      dockerClient.inspectImageCmd(image.getId).exec()
    }
  }

  def deleteDeploymentImage(deploymentId: String) = {
    getAgentImage(deploymentId).map { agentImage =>
      dockerClient.removeImageCmd(agentImage.getId).exec()
    }.orElse(throw new DaemonResourcesNotFoundException(s"Deployment image $deploymentId not found"))
  }

  def cleanDanglingImages() = {
    val images = dockerClient.listImagesCmd.withFilters("{\"dangling\":[\"true\"]}").withShowAll(true).exec.asScala
    images.foreach { image =>
      dockerClient.removeImageCmd(image.getId).exec()
    }
  }

  private def createAgent(deploymentId: String, labels: Map[String, String], bootstrapContext: Map[String, Any]) = {
    val deploymentImageId = getAgentImage(deploymentId).getOrElse(throw new DaemonResourcesNotFoundException(s"Deployment image $deploymentId not found")).getId
    val labels = Maps.newHashMap[String, String]()
    labels.put(RuntimeConstant.ORGANIZATION_LABEL, RuntimeConstant.ORGANIZATION_VALUE)
    labels.put(RuntimeConstant.DEPLOYMENT_ID_LABEL, deploymentId)
    labels.putAll(labels)
    val portHttp: ExposedPort = ExposedPort.tcp(9000)
    val portBindings: Ports = new Ports
    portBindings.bind(portHttp, Ports.Binding(null))
    val createdContainer = dockerClient
      .createContainerCmd(deploymentImageId)
      .withExposedPorts(portHttp)
      .withPortBindings(portBindings)
      .withName("toscaruntime_" + deploymentId + "_agent")
      .withLabels(labels).exec
    dockerClient.startContainerCmd(createdContainer.getId).exec()
    bootstrapContext.get("docker_network_id").map {
      case networkId: String =>
        // If it's a swarm cluster with a swarm network overly then connect to it
        dockerClient.connectContainerToNetworkCmd(createdContainer.getId, networkId).exec()
    }
    createdContainer
  }

  def createDeploymentAgent(deploymentId: String, bootstrapContext: Map[String, Any]) = {
    createAgent(deploymentId, Map(RuntimeConstant.AGENT_TYPE_LABEL -> RuntimeConstant.AGENT_TYPE_DEPLOYMENT_VALUE), bootstrapContext)
  }

  def generateDeploymentIdForBootstrap(provider: String, target: String) = {
    "bootstrap_" + provider + "_" + target
  }

  def createBootstrapAgent(provider: String, target: String) = {
    val generatedId = generateDeploymentIdForBootstrap(provider, target)
    createAgent(generatedId,
      Map(
        RuntimeConstant.AGENT_TYPE_LABEL -> RuntimeConstant.AGENT_TYPE_BOOTSTRAP_VALUE,
        RuntimeConstant.PROVIDER_TARGET_LABEL -> target
      ), Map.empty
    )
  }

  def start(deploymentId: String) = {
    dockerClient.startContainerCmd(getAgent(deploymentId).get.getId).exec()
  }

  def stop(deploymentId: String) = {
    dockerClient.stopContainerCmd(getAgent(deploymentId).get.getId).exec()
  }

  def delete(deploymentId: String) = {
    dockerClient.removeContainerCmd(getAgent(deploymentId).get.getId).withForce(true).exec()
  }

  def tailContainerLog(containerId: String, output: PrintStream) = {
    val logCallBack = new LogContainerResultCallback() {
      override def onNext(item: Frame): Unit = {
        output.print(new String(item.getPayload, "UTF-8"))
        super.onNext(item)
      }
    }
    DockerUtil.showLog(dockerClient, containerId, true, 1000, logCallBack)
    logCallBack
  }

  def tailLog(deploymentId: String, output: PrintStream) = {
    val containerId = getAgent(deploymentId).getOrElse(throw new DaemonResourcesNotFoundException("Deployment " + deploymentId + " not found")).getId
    tailContainerLog(containerId, output)
  }

  def version = {
    dockerClient.versionCmd().exec().getApiVersion
  }
}
