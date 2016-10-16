package com.toscaruntime.rest.client

import java.io.{FileWriter, PrintStream}
import java.nio.file.{Files, Path, StandardOpenOption}

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.InspectContainerResponse
import com.github.dockerjava.api.model._
import com.github.dockerjava.core.command.{BuildImageResultCallback, LogContainerResultCallback}
import com.google.common.collect.Maps
import com.toscaruntime.constant.{DeployerConstant, RuntimeConstant}
import com.toscaruntime.exception.client.{AgentNotRunningException, DaemonResourcesNotFoundException, InvalidArgumentException}
import com.toscaruntime.rest.model.DeploymentInfoDTO
import com.toscaruntime.util._
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import com.typesafe.scalalogging.LazyLogging
import org.yaml.snakeyaml.Yaml

import scala.collection.JavaConverters._

/**
  * A client to interact with docker daemon
  *
  * @author Minh Khang VU
  */
class DockerDaemonClient(var config: DockerDaemonConfig) extends LazyLogging {

  var dockerClient: DockerClient = DockerUtil.buildDockerClient(config)

  val yaml = new Yaml()

  def setDockerClient(newConfig: DockerDaemonConfig) = {
    dockerClient.close()
    dockerClient = DockerUtil.buildDockerClient(newConfig)
    config = newConfig
  }

  private def findMappedPort(container: InspectContainerResponse, localPort: Int) = {
    container.getNetworkSettings.getPorts.getBindings.asScala.filterKeys(exposedPort => exposedPort.getProtocol == InternetProtocol.TCP && exposedPort.getPort == localPort).values.head.head.getHostPortSpec
  }

  def getProxyURL: Option[String] = {
    val proxyFound = dockerClient.listContainersCmd().withLabelFilter(Map(RuntimeConstant.COMPONENT_TYPE_LABEL -> RuntimeConstant.PROXY_TYPE_VALUE).asJava).withLimit(1).exec()
    if (proxyFound.isEmpty) {
      None
    } else {
      val proxy = proxyFound.iterator().next()
      val proxyPort = findMappedPort(dockerClient.inspectContainerCmd(proxy.getId).exec(), 9000)
      // TODO the public ip to join the container should be discovered more dynamically ?
      Some("http://" + DockerUtil.getDockerHostName(config.getHost) + ":" + proxyPort)
    }
  }

  def getAgent(deploymentId: String) = {
    // TODO container that has been stopped, must be filtered out or status should be sent back
    val containers = dockerClient.listContainersCmd().withLabelFilter(Map(RuntimeConstant.DEPLOYMENT_ID_LABEL -> deploymentId).asJava).withShowAll(true).exec().asScala
    containers.headOption
  }

  def getAgentInfo(deploymentId: String) = {
    getAgent(deploymentId).map { container =>
      dockerClient.inspectContainerCmd(container.getId).exec()
    }
  }

  def getBootstrapAgentURL(deploymentId: String) = {
    getAgentInfo(deploymentId).filter(_.getState.getRunning).map { container =>
      val daemonHost = DockerUtil.getDockerHostName(config.getHost)
      val port = container.getNetworkSettings.getPorts.getBindings.asScala.filterKeys(exposedPort => exposedPort.getProtocol == InternetProtocol.TCP && exposedPort.getPort == 9000).values.head.head.getHostPortSpec
      s"http://$daemonHost:$port/deployment"
    }
  }

  def listDeploymentAgents() = {
    // TODO container that has been stopped, must be filtered out or status should be sent back
    val containers = dockerClient.listContainersCmd().withShowAll(true).withLabelFilter(Map(RuntimeConstant.ORGANIZATION_LABEL -> RuntimeConstant.ORGANIZATION_VALUE).asJava).exec().asScala
    containers.map { container =>
      val containerInfo = dockerClient.inspectContainerCmd(container.getId).exec
      val deploymentId = container.getLabels.get(RuntimeConstant.DEPLOYMENT_ID_LABEL)
      val ipAddresses = containerInfo.getNetworkSettings.getNetworks.asScala.map {
        case (networkName: String, network: NetworkSettings.Network) => (networkName, network.getIpAddress)
      }.toMap
      val deploymentInfo = DeploymentInfoDTO(deploymentId, container.getNames.head, containerInfo.getId, containerInfo.getCreated, container.getStatus, ipAddresses)
      (deploymentId, deploymentInfo)
    }.toMap
  }

  def copyDockerFile(outputPath: Path, deploymentId: String) = {
    Files.copy(Thread.currentThread().getContextClassLoader.getResourceAsStream("Dockerfile"), outputPath)
    Files.write(outputPath, s"LABEL ${RuntimeConstant.DEPLOYMENT_ID_LABEL}=$deploymentId\n".getBytes("UTF-8"), StandardOpenOption.APPEND)
  }

  /**
    * Create a deployment agent's docker image with custom setup
    *
    * @param deploymentId        the name of the deployment
    * @param recipePath          the path of the recipe
    * @param providerConfigPaths the path for the configuration of the provider
    * @return id of the created docker image
    */
  def createAgentImage(deploymentId: String, bootstrap: Boolean, recipePath: Path, providerConfigPaths: List[Path], pluginConfigPaths: List[Path], bootstrapContext: Map[String, Any]) = {
    val tempDockerImageBuildDir = Files.createTempDirectory("tosca")
    val tempRecipePath = tempDockerImageBuildDir.resolve("deployment")
    PathUtil.openAsDirectory(recipePath, FileUtil.copy(_, tempRecipePath))
    copyDockerFile(tempDockerImageBuildDir.resolve("Dockerfile"), deploymentId)
    val deploymentConfig = ConfigFactory.empty()
      .withValue(DeployerConstant.DEPLOYMENT_NAME_KEY, ConfigValueFactory.fromAnyRef(deploymentId))
      .withValue(DeployerConstant.BOOTSTRAP_KEY, ConfigValueFactory.fromAnyRef(bootstrap))
    FileUtil.writeTextFile(deploymentConfig.root().render(), tempDockerImageBuildDir.resolve("deployment").resolve("deployment.conf"))
    // Copy provider configs
    val copiedProviderConfigPaths = tempDockerImageBuildDir.resolve("providers")
    Files.createDirectories(copiedProviderConfigPaths)
    providerConfigPaths.foreach(providerConfigPath => FileUtil.copy(providerConfigPath, copiedProviderConfigPaths.resolve(providerConfigPath.getFileName)))
    // Change auto generated conf to regular conf
    ScalaFileUtil.listRecursive(copiedProviderConfigPaths, file => Files.isRegularFile(file) && file.getFileName.toString == "auto_generated_provider.conf").foreach(path => Files.move(path, path.resolveSibling("provider.conf")))
    // Copy plugin configs
    val copiedPluginConfigPaths = tempDockerImageBuildDir.resolve("plugins")
    Files.createDirectories(copiedPluginConfigPaths)
    pluginConfigPaths.foreach(pluginConfigPath => FileUtil.copy(pluginConfigPath, copiedPluginConfigPaths.resolve(pluginConfigPath.getFileName)))
    if (bootstrapContext.nonEmpty) {
      yaml.dump(JavaScalaConversionUtil.toJavaMap(bootstrapContext), new FileWriter(tempDockerImageBuildDir.resolve("bootstrapContext.yaml").toFile))
    }
    dockerClient.buildImageCmd(tempDockerImageBuildDir.toFile).withTag(s"toscaruntime/deployment_$deploymentId").withNoCache(true).exec(new BuildImageResultCallback)
  }

  def updateAgentRecipe(deploymentId: String, recipePath: Path) = {
    val containerId = getAgent(deploymentId).getOrElse(throw new AgentNotRunningException(s"Deployment agent $deploymentId is not running")).getId
    if (!Files.isDirectory(recipePath)) throw new InvalidArgumentException("Can only handle directory as input to update agent's recipe")
    dockerClient.copyArchiveToContainerCmd(containerId)
      .withDirChildrenOnly(true)
      .withHostResource(recipePath.toString)
      .withRemotePath("/var/lib/toscaruntime/deployment")
      .exec()
  }

  def getAgentImage(deploymentId: String) = {
    val images = dockerClient.listImagesCmd().withLabelFilter(Map(RuntimeConstant.DEPLOYMENT_ID_LABEL -> deploymentId).asJava).exec().asScala
      .filter(image => image.getRepoTags != null && image.getRepoTags.nonEmpty && !image.getRepoTags()(0).equals("<none>:<none>"))
    images.headOption
  }

  def listDeploymentImages() = {
    val images = dockerClient.listImagesCmd().withLabelFilter(Map(RuntimeConstant.ORGANIZATION_LABEL -> RuntimeConstant.ORGANIZATION_VALUE).asJava).exec().asScala
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
    val images = dockerClient.listImagesCmd.withDanglingFilter(true).withShowAll(true).exec.asScala
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
    portBindings.bind(portHttp, Ports.Binding.empty())
    val createdContainer = dockerClient
      .createContainerCmd(deploymentImageId)
      .withExposedPorts(portHttp)
      .withPortBindings(portBindings)
      .withName(s"toscaruntime_${DockerUtil.normalizeResourceName(deploymentId)}_agent")
      .withLabels(labels).exec
    dockerClient.startContainerCmd(createdContainer.getId).exec()
    bootstrapContext.get("docker_network_id").map {
      case networkId: String =>
        // If it's a swarm cluster with a swarm network overlay then connect to it
        dockerClient.connectToNetworkCmd().withContainerId(createdContainer.getId).withNetworkId(networkId).exec()
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
        RuntimeConstant.PROVIDER_TYPE_LABEL -> provider,
        RuntimeConstant.PROVIDER_TARGET_LABEL -> target
      ), Map.empty
    )
  }

  def start(deploymentId: String) = {
    dockerClient.startContainerCmd(getAgent(deploymentId).getOrElse(throw new AgentNotRunningException(s"Deployment agent $deploymentId is not running")).getId).exec()
  }

  def stop(deploymentId: String) = {
    dockerClient.stopContainerCmd(getAgent(deploymentId).getOrElse(throw new AgentNotRunningException(s"Deployment agent $deploymentId is not running")).getId).exec()
  }

  def restart(deploymentId: String) = {
    dockerClient.restartContainerCmd(getAgent(deploymentId).getOrElse(throw new AgentNotRunningException(s"Deployment agent $deploymentId is not running")).getId).exec()
  }

  def delete(deploymentId: String) = {
    dockerClient.removeContainerCmd(getAgent(deploymentId).getOrElse(throw new AgentNotRunningException(s"Deployment agent $deploymentId is not running")).getId).withForce(true).exec()
  }

  def tailContainerLog(containerId: String, output: PrintStream) = {
    val logCallBack = new LogContainerResultCallback() {
      override def onNext(item: Frame): Unit = {
        output.print(new String(item.getPayload, "UTF-8"))
      }
    }
    DockerUtil.showLog(dockerClient, containerId, true, 1000, logCallBack)
    logCallBack
  }

  def tailLog(deploymentId: String, output: PrintStream) = {
    val containerId = getAgent(deploymentId).getOrElse(throw new AgentNotRunningException(s"Deployment agent $deploymentId is not running")).getId
    tailContainerLog(containerId, output)
  }

  def version = {
    dockerClient.versionCmd().exec().getApiVersion
  }
}
