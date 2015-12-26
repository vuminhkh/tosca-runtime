package com.toscaruntime.compiler

import java.nio.file.Paths

object TestConstant {

  val TEST_DATA_PATH = Paths.get("target").resolve("compiler-test-data")

  val CSAR_REPOSITORY_PATH = TEST_DATA_PATH.resolve("csars")

  val GIT_TEST_DATA_PATH = TEST_DATA_PATH.resolve("gits")

  val ASSEMBLY_PATH = TEST_DATA_PATH.resolve("assemblies")
}
