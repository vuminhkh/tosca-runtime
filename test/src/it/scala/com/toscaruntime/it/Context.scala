package com.toscaruntime.it

import com.toscaruntime.cli.command.UseCommand
import com.toscaruntime.it.TestConstant._
import com.toscaruntime.rest.client.ToscaRuntimeClient
import com.toscaruntime.util.{ClassLoaderUtil, DockerUtil}

object Context {

  val dockerConfig = DockerUtil.getDefaultDockerDaemonConfig

  val client = new ToscaRuntimeClient(dockerConfig.getUrl, dockerConfig.getCertPath)

  private val dockerConfigPath = UseCommand.getDockerConfigPath(testDataPath)

  private val openstackConfigPath = ClassLoaderUtil.getPathForResource("conf/providers/openstack/")

  private val openstackInputPath = ClassLoaderUtil.getPathForResource("csars/openstack/inputs.yml")

  val providerConfigPaths = Map(dockerProvider -> dockerConfigPath, openstackProvider -> openstackConfigPath)

  val providerInputPaths = Map(openstackProvider -> openstackInputPath)
}