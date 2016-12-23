package com.toscaruntime.compiler

import java.nio.file.{Files, Path, Paths}

import com.toscaruntime.compiler.tosca.CompilationResult
import com.toscaruntime.constant.CompilerConstant
import com.toscaruntime.util.{ClassLoaderUtil, FileUtil, ScalaFileUtil}
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{BeforeAndAfter, MustMatchers, WordSpec}

class AbstractSpec extends WordSpec with MustMatchers with LazyLogging with BeforeAndAfter {

  lazy val testPath = Paths.get("target").resolve("compiler-test-data")
  lazy val assemblyPath = testPath.resolve("assemblies")
  lazy val gitPath = testPath.resolve("gits")
  lazy val csarsPath = testPath.resolve("csars")

  before {
    try {
      logger.info(s"Cleaning test data at ${testPath.toAbsolutePath}")
      FileUtil.delete(testPath)
      Files.createDirectories(assemblyPath)
      Files.createDirectories(gitPath)
      Files.createDirectories(csarsPath)
      logger.info(s"Cleaned test data ${testPath.toAbsolutePath}")
    } catch {
      case _: Throwable => logger.warn(s"Could not properly clean test data at ${testPath.toAbsolutePath}")
    }
  }

  def showCompilationErrors(compilationResult: CompilationResult) = {
    compilationResult.errors.foreach {
      case (path, errors) => errors.foreach { error =>
        logger.error("At [{}][{}.{}] : {}", Paths.get(path).getFileName, error.startPosition.line.toString, error.startPosition.column.toString, error.error)
      }
    }
  }

  def installAndAssertCompilationResult(csarPath: Path) = {
    val compilationResult = Compiler.install(csarPath, csarsPath)
    showCompilationErrors(compilationResult)
    compilationResult.isSuccessful must be(true)
    compilationResult
  }

  def installProvider(providerName: String) = {
    val commonProviderPath = csarsPath.resolve("toscaruntime-common-provider-types").resolve("${version}").resolve("src").resolve("main").resolve("resources")
    if (!Files.isDirectory(commonProviderPath)) {
      ScalaFileUtil.copyRecursive(Paths.get("providers").resolve("common").resolve("src").resolve("main").resolve("resources"), commonProviderPath)
    }
    ScalaFileUtil.copyRecursive(Paths.get("providers").resolve(providerName), csarsPath.resolve("toscaruntime-" + providerName + "-provider-types").resolve("${version}"))
  }

  def assemblyDockerTopologyAndAssertCompilationResult(dockerTopology: String) = {
    assemblyTopologyAndAssertCompilationResult(dockerTopology, "com.toscaruntime.docker.nodes.Container")
  }

  def assemblyTopologyAndAssertCompilationResult(dockerTopology: String, expectedContain: String) = {
    val topologyPath = ClassLoaderUtil.getPathForResource(dockerTopology)
    val topologyName = topologyPath.getFileName.toString
    val inputsPath = topologyPath.getParent.getParent.resolve("inputs").resolve(topologyName).resolve("inputs.yaml")
    val generatedAssemblyPath = assemblyPath.resolve(topologyPath.getFileName.toString)
    Files.createDirectories(generatedAssemblyPath)
    val topologyCompilationResult = Compiler.assembly(topologyPath, generatedAssemblyPath, csarsPath, if (Files.exists(inputsPath)) Some(inputsPath) else None)
    showCompilationErrors(topologyCompilationResult)
    topologyCompilationResult.isSuccessful must be(true)
    val deploymentGenerated = FileUtil.readTextFile(generatedAssemblyPath.resolve(CompilerConstant.ASSEMBLY_RECIPE_FOLDER).resolve(CompilerConstant.SOURCE_FOLDER).resolve(CompilerConstant.DEPLOYMENT_FILE))
    deploymentGenerated must include(expectedContain)
    generatedAssemblyPath
  }
}
