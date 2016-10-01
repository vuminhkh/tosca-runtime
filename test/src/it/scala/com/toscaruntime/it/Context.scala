package com.toscaruntime.it

import java.io.FileNotFoundException
import java.nio.file.Files

import com.toscaruntime.it.TestConstant._
import com.toscaruntime.rest.client.ToscaRuntimeClient
import com.toscaruntime.util.DockerUtil

object Context {

  val dockerConfig = DockerUtil.getDefaultDockerDaemonConfig

  val client = new ToscaRuntimeClient(dockerConfig.getUrl, dockerConfig.getCertPath)

  def getInput(testName: String) = {
    if (testName == dockerProvider) None
    else {
      val testInputPath = testConfigPath.resolve("inputs").resolve(testName + ".yaml")
      if (Files.isRegularFile(testInputPath)) Some(testInputPath) else throw new FileNotFoundException(s"Input is mandatory for $testName, please configure $testInputPath")
    }
  }
}