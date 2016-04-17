package com.toscaruntime.it

import java.nio.file.{Files, Path, Paths, StandardCopyOption}

import com.toscaruntime.cli.command.UseCommand
import com.toscaruntime.it.TestConstant._
import com.toscaruntime.rest.client.ToscaRuntimeClient
import com.toscaruntime.util.DockerUtil
import org.apache.commons.lang3.StringUtils

object Context {

  val dockerConfig = DockerUtil.getDefaultDockerDaemonConfig

  val client = new ToscaRuntimeClient(dockerConfig.getUrl, dockerConfig.getCertPath)

  private val dockerConfigPath = UseCommand.getDaemonConfigPath(testDataPath)

  private lazy val openstackGlobalConfPath = {
    val providerConfDir = System.getenv("TOSCA_RUNTIME_OS_CONF_DIR")
    if (StringUtils.isBlank(providerConfDir)) {
      throw new AssertionError("Environment variable TOSCA_RUNTIME_OS_CONF_DIR needs to be set to launch openstack test")
    }
    val providerConfDirPath = Paths.get(providerConfDir)
    if (!Files.isDirectory(providerConfDirPath)) {
      throw new AssertionError(providerConfDir + " is not a directory or do not exist")
    }
    providerConfDirPath
  }

  private lazy val openstackConfigPath = {
    val providerConfFile = openstackGlobalConfPath.resolve("provider").resolve("provider.conf")
    if (!Files.isRegularFile(providerConfFile)) {
      throw new AssertionError(providerConfFile + " is not a file or do not exist, openstack tests need tenant information")
    }
    providerConfFile.getParent
  }

  private lazy val openstackInputPath = {
    val inputsFile = openstackGlobalConfPath.resolve("deployment").resolve("inputs.yaml")
    if (!Files.isRegularFile(inputsFile)) {
      throw new AssertionError(inputsFile + " is not a file or do not exist, openstack tests need input to know which image, flavor to use ...")
    }
    inputsFile
  }

  private lazy val openstackKeyPath = {
    val keyFile = openstackGlobalConfPath.resolve("deployment").resolve("toscaruntime.pem")
    if (!Files.isRegularFile(keyFile)) {
      throw new AssertionError(keyFile + " is not a file or do not exist, openstack tests need a key to create VM")
    }
    keyFile
  }

  def getProviderConfig(provider: String) = {
    provider match {
      case `openstackProvider` => openstackConfigPath
      case `dockerProvider` => dockerConfigPath
    }
  }

  def getInput(provider: String) = {
    provider match {
      case `openstackProvider` => Some(openstackInputPath)
      case _ => None
    }
  }

  def postProcessTopology(provider: String, topologyPath: Path) = {
    provider match {
      case `openstackProvider` => Files.copy(openstackKeyPath, topologyPath.resolve("toscaruntime.pem"), StandardCopyOption.REPLACE_EXISTING)
      case _ =>
    }
  }
}