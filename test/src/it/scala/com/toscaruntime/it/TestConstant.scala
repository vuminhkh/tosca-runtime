package com.toscaruntime.it

import java.nio.file.Paths

import com.toscaruntime.util.ClassLoaderUtil

object TestConstant {

  val prepareTestDataPath = Paths.get("test").resolve("target").resolve("prepare-test")

  val testDataPath = Paths.get("target").resolve("test-data")

  val repositoryPath = testDataPath.resolve("repository")

  val tempPath = testDataPath.resolve("temp")

  val csarsPath = ClassLoaderUtil.getPathForResource("csars")

  val assemblyPath = testDataPath.resolve("assembly")

  val dockerProvider = "docker"

  val openstackProvider = "openstack"

  val standalone = "standalone"

  val bootstrap = "bootstrap"
}
