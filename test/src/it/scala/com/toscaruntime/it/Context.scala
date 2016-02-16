package com.toscaruntime.it

import com.toscaruntime.cli.command.UseCommand
import com.toscaruntime.it.TestConstant._
import com.toscaruntime.rest.client.ToscaRuntimeClient
import com.toscaruntime.util.DockerUtil

object Context {

  val dockerConfig = DockerUtil.getDefaultDockerDaemonConfig

  val client = new ToscaRuntimeClient(dockerConfig.getUrl, dockerConfig.getCertPath)

  val dockerConfigPath = UseCommand.getDockerConfigPath(testDataPath)
}
