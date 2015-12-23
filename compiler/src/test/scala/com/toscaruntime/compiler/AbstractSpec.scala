package com.toscaruntime.compiler

import java.nio.file.{Path, Paths}

import com.toscaruntime.compiler.tosca.CompilationResult
import com.toscaruntime.util.FileUtil
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.BeforeAndAfter
import org.scalatestplus.play.PlaySpec

class AbstractSpec extends PlaySpec with LazyLogging with BeforeAndAfter {

  before {
    FileUtil.delete(TestConstant.TEST_DATA_PATH)
  }

  def showCompilationErrors(compilationResult: CompilationResult) = {
    compilationResult.errors.foreach {
      case (path, errors) => errors.foreach { error =>
        logger.error("At [{}][{}.{}] : {}", Paths.get(path).getFileName, error.startPosition.line.toString, error.startPosition.column.toString, error.error)
      }
    }
  }

  def installAndAssertCompilationResult(csarPath: Path) = {
    val compilationResult = Compiler.install(csarPath, TestConstant.CSAR_REPOSITORY_PATH)
    showCompilationErrors(compilationResult)
    compilationResult.isSuccessful must be(true)
    compilationResult
  }
}
