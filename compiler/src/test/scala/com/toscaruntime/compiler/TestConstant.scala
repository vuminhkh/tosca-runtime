package com.toscaruntime.compiler

import java.nio.file.Paths

object TestConstant {

  val TEST_DATA_PATH = Paths.get("target").resolve("compiler-test-data")

  val GIT_TEST_DATA_PATH = TEST_DATA_PATH.resolve("gits")
}
