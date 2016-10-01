package com.toscaruntime.it

import java.nio.file.Paths

import com.toscaruntime.util.ClassLoaderUtil

object TestConstant {

  val customTestComponentsPath = Paths.get("test").resolve("src").resolve("it").resolve("resources").resolve("csars")

  val prepareTestDataPath = Paths.get("test").resolve("target").resolve("prepare-test")

  val testDataPath = Paths.get("target").resolve("test-data")

  val repositoryPath = testDataPath.resolve("repository")

  val tempPath = testDataPath.resolve("temp")

  val inputsPath = ClassLoaderUtil.getPathForResource("inputs")

  val csarsPath = ClassLoaderUtil.getPathForResource("csars")

  val assemblyPath = testDataPath.resolve("assembly")

  val bootstrapPath = testDataPath.resolve("bootstrap")

  val testConfigPath = Paths.get("test-config")

  val testProvidersConfigPath = testConfigPath.resolve("conf").resolve("providers")

  val testPluginsConfigPath = testConfigPath.resolve("conf").resolve("plugins")

  val dockerProvider = "docker"

  val openstackProvider = "openstack"

  val awsProvider = "aws"

  val standalone = "standalone"

  val bootstrap = "bootstrap"

  val defaultTarget = "default"

  val swarmTarget = "swarm"
}
