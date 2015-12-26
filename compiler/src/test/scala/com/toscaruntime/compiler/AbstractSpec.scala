package com.toscaruntime.compiler

import java.nio.file.{Files, Path, Paths}

import com.toscaruntime.compiler.tosca.CompilationResult
import com.toscaruntime.util.{ClassLoaderUtil, FileUtil}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.BeforeAndAfter
import org.scalatestplus.play.PlaySpec

class AbstractSpec extends PlaySpec with LazyLogging with BeforeAndAfter {

  before {
    FileUtil.delete(TestConstant.TEST_DATA_PATH)
    Files.createDirectories(TestConstant.ASSEMBLY_PATH)
    Files.createDirectories(TestConstant.GIT_TEST_DATA_PATH)
    Files.createDirectories(TestConstant.CSAR_REPOSITORY_PATH)
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

  def assemblyDockerTopologyAndAssertCompilationResult(dockerTopology: String) = {
    val topologyPath = ClassLoaderUtil.getPathForResource(dockerTopology)
    val generatedAssemblyPath = TestConstant.ASSEMBLY_PATH.resolve(topologyPath.getFileName.toString)
    Files.createDirectories(generatedAssemblyPath)
    val topologyCompilationErrors = Compiler.assembly(topologyPath, generatedAssemblyPath, TestConstant.CSAR_REPOSITORY_PATH)
    showCompilationErrors(topologyCompilationErrors)
    topologyCompilationErrors.isSuccessful must be(true)
    val deploymentGenerated = FileUtil.readTextFile(generatedAssemblyPath.resolve("deployment").resolve("Deployment.java"))
    deploymentGenerated must include("com.toscaruntime.docker.nodes.Container")
  }
}
