package com.toscaruntime.util

import java.nio.file.{Files, Paths}

import org.scalatest.{MustMatchers, WordSpec}

class ScalaFileUtilSpec extends WordSpec with MustMatchers {

  "Scala file util" must {
    "be able to copy directory recursively" in {
      val tempDir = Files.createTempDirectory("test")
      ScalaFileUtil.copyRecursive(Paths.get("common/file-util/src/test/resources/"), tempDir)
      Files.isDirectory(tempDir.resolve("testDir")) must be(true)
      Files.isRegularFile(tempDir.resolve("testDir").resolve("testFile.txt")) must be(true)
    }
  }
}
