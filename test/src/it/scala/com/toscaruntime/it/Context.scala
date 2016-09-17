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

  def resolveGlobalConfPath(envVar: String) = {
    val providerConfDir = System.getenv(envVar)
    if (StringUtils.isBlank(providerConfDir)) {
      throw new AssertionError(s"Environment variable $envVar needs to be set to launch test")
    }
    val providerConfDirPath = Paths.get(providerConfDir)
    if (!Files.isDirectory(providerConfDirPath)) {
      throw new AssertionError(providerConfDir + " is not a directory or do not exist")
    }
    providerConfDirPath
  }

  private lazy val openstackGlobalConfPath = resolveGlobalConfPath("TOSCA_RUNTIME_OS_CONF_DIR")
  private lazy val awsGlobalConfPath = resolveGlobalConfPath("TOSCA_RUNTIME_AWS_CONF_DIR")

  def resolveProviderConf(globalConfPath: Path) = {
    val providerConfFile = globalConfPath.resolve("provider").resolve("default").resolve("provider.conf")
    if (!Files.isRegularFile(providerConfFile)) {
      throw new AssertionError(providerConfFile + " is not a file or do not exist, openstack tests need tenant information")
    }
    providerConfFile.getParent.getParent
  }

  private lazy val openstackConfigPath = resolveProviderConf(openstackGlobalConfPath)
  private lazy val awsConfigPath = resolveProviderConf(awsGlobalConfPath)

  def resolveInputPath(globalConfPath: Path) = {
    val inputsFile = globalConfPath.resolve("deployment").resolve("inputs.yaml")
    if (!Files.isRegularFile(inputsFile)) {
      throw new AssertionError(inputsFile + " is not a file or do not exist, openstack tests need input to know which image, flavor to use ...")
    }
    inputsFile
  }

  private lazy val openstackInputPath = resolveInputPath(openstackGlobalConfPath)
  private lazy val awsInputPath = resolveInputPath(awsGlobalConfPath)

  def resolveKeyPath(globalConfPath: Path) = {
    val keyFile = globalConfPath.resolve("deployment").resolve("toscaruntime.pem")
    if (!Files.isRegularFile(keyFile)) {
      throw new AssertionError(keyFile + " is not a file or do not exist, openstack tests need a key to create VM")
    }
    keyFile
  }

  private lazy val openstackKeyPath = resolveKeyPath(openstackGlobalConfPath)
  private lazy val awsKeyPath = resolveKeyPath(awsGlobalConfPath)

  def getProviderConfig(provider: String) = {
    provider match {
      case `awsProvider` => awsConfigPath
      case `openstackProvider` => openstackConfigPath
      case `dockerProvider` => dockerConfigPath
    }
  }

  def getInput(provider: String) = {
    provider match {
      case `awsProvider` => Some(awsInputPath)
      case `openstackProvider` => Some(openstackInputPath)
      case _ => None
    }
  }

  def postProcessTopology(provider: String, topologyPath: Path) = {
    provider match {
      case `awsProvider` => Files.copy(awsKeyPath, topologyPath.resolve("toscaruntime.pem"), StandardCopyOption.REPLACE_EXISTING)
      case `openstackProvider` => Files.copy(openstackKeyPath, topologyPath.resolve("toscaruntime.pem"), StandardCopyOption.REPLACE_EXISTING)
      case _ =>
    }
  }
}