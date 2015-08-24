package com.mkv.tosca.compiler

import java.nio.file._

import com.google.common.io.Closeables
import com.mkv.tosca.compiler.tosca.Csar
import com.mkv.util.FileUtil
import com.typesafe.scalalogging.LazyLogging

import scala.collection.JavaConversions._

/**
 * Entry point to compile csars to its java implementation
 *
 * @author Minh Khang VU
 */
object Compiler extends LazyLogging {

  /**
   * Batch compile a topology with all its dependencies and produce a deployable package
   * @param topology the topology to produce deployment for
   * @param dependencies the topology's dependencies
   * @param output output for the deployable package
   */
  def batchCompile(topology: Path, dependencies: List[Path], output: Path): Unit = {
    var topologyFileSystem = topology
    val topologyIsZipped = Files.isRegularFile(topology)
    if (topologyIsZipped) {
      topologyFileSystem = FileUtil.createZipFileSystem(topology)
    }
    var outputFileSystem = output
    val outputIsZipped = !Files.isDirectory(output)
    if (outputIsZipped) {
      outputFileSystem = FileUtil.createZipFileSystem(output)
    }
    // Load dependencies
    val dependenciesFileSystems = Util.createZipFileSystems(dependencies)
    try {
      val parsedDependenciesWithPaths = dependenciesFileSystems.flatMap { case (dependencyPath: Path, _) =>
        analyzeSyntax(dependencyPath).map((_, dependencyPath))
      }
      if (parsedDependenciesWithPaths.size < dependencies.size) {
        logger.error("Syntax error detected in dependencies, check output log for details")
        return
      }
      val parsedDependencies = parsedDependenciesWithPaths.map(_._1)
      for ((parsedDependencyWithPath, index) <- parsedDependenciesWithPaths.zipWithIndex) {
        val currentDependencies = parsedDependencies.slice(0, index)
        val semanticErrors = analyzeSemantic(parsedDependencyWithPath._1, parsedDependencyWithPath._2, currentDependencies)
        if (semanticErrors.nonEmpty) {
          logger.error("Semantic error with " + parsedDependencyWithPath._1)
          return
        } else {
          CodeGenerator.generate(parsedDependencyWithPath._1, currentDependencies, parsedDependencyWithPath._2, outputFileSystem)
        }
      }
      val parsedTopology = analyzeSyntax(topologyFileSystem)
      if (parsedTopology.isDefined) {
        val semanticErrors = analyzeSemantic(parsedTopology.get, topologyFileSystem, parsedDependencies)
        if (semanticErrors.isEmpty) {
          CodeGenerator.generate(parsedTopology.get, parsedDependencies, topologyFileSystem, outputFileSystem)
          logger.info("Deployment generated to " + output)
        }
      }
    } finally {
      if (topologyIsZipped) {
        Closeables.close(topologyFileSystem.getFileSystem, true)
      }
      if (outputIsZipped) {
        Closeables.close(outputFileSystem.getFileSystem, true)
      }
      dependenciesFileSystems.filter(_._2.isDefined).foreach { case (_, fileSystem: Option[FileSystem]) =>
        Closeables.close(fileSystem.get, true)
      }
    }
  }

  /**
   * Compile a csar and produce generated java code for types and topology
   * @param csar the csar to compile
   * @param dependencies the dependencies, must be compiled csar
   * @param output the output for compilation
   */
  def compile(csar: Path, dependencies: List[Path], output: Path): Unit = {
    // Create the file system based on the csar path
    var csarFileSystem = csar
    val csarIsZipped = Files.isRegularFile(csar)
    if (csarIsZipped) {
      csarFileSystem = FileUtil.createZipFileSystem(csar)
    }
    // Load dependencies
    val dependenciesFileSystems = Util.createZipFileSystems(dependencies).toMap
    try {
      val parsedDependencies = dependenciesFileSystems.keys.flatMap(dependencyPath => analyzeSyntax(dependencyPath.resolve(Constant.ARCHIVE_FOLDER))).toList
      if (parsedDependencies.size < dependencies.size) {
        logger.error("Syntax error detected in dependencies, check output log for details")
        return
      }
      // Parse the csar to construct syntax tree
      val parsedCsar = analyzeSyntax(csarFileSystem)
      if (parsedCsar.isDefined) {
        val semanticErrors = analyzeSemantic(parsedCsar.get, csarFileSystem, parsedDependencies)
        if (semanticErrors.isEmpty) {
          CodeGenerator.generate(parsedCsar.get, parsedDependencies, csarFileSystem, output)
          logger.info("Code generated to " + output)
        }
      }
    } finally {
      if (csarIsZipped) {
        Closeables.close(csarFileSystem.getFileSystem, true)
      }
      dependenciesFileSystems.values.filter(_.isDefined).foreach { fileSystem =>
        Closeables.close(fileSystem.get, true)
      }
    }
  }

  def analyzeSemantic(parsedCsar: Csar, csarFileSystem: Path, parsedDependencies: List[Csar]) = {
    // Analyze semantic to be sure that there are no errors
    val semanticErrors = SemanticAnalyzer.analyze(parsedCsar, csarFileSystem, parsedDependencies)
    if (semanticErrors.isEmpty) {
      logger.info("CSAR's semantic analyzer has finished successfully for " + parsedCsar.csarName)
    } else {
      logger.error("Semantic errors for " + parsedCsar.csarName + " :")
      semanticErrors.foreach {
        case (filePath, errors) =>
          logger.error("For file : " + filePath)
          errors.foreach { error =>
            logger.error("Line " + error.startPosition + " : " + error.error)
          }
          println()
      }
    }
    semanticErrors
  }

  def analyzeSyntax(csarPath: Path) = {
    val allYamlFiles = FileUtil.listFiles(csarPath, ".yml", ".yaml").toList
    val allParseResults = allYamlFiles.map { yamlPath =>
      val toscaDefinitionText = FileUtil.readTextFile(yamlPath)
      (FileUtil.relativizePath(csarPath, yamlPath), SyntaxAnalyzer.parse(SyntaxAnalyzer.definition, toscaDefinitionText))
    }.toMap
    val errorParseResults = allParseResults.filter(!_._2.successful)
    val successFullResults = allParseResults.filter(_._2.successful)
    if (errorParseResults.nonEmpty) {
      logger.error("Syntax errors for " + csarPath + " :")
      errorParseResults.foreach {
        case (filePath, parseResult) =>
          logger.error("For file : " + filePath)
          logger.error(parseResult.toString)
      }
      None
    } else {
      logger.info("CSAR's syntax analyzer has finished successfully for " + csarPath)
      Some(Csar(successFullResults.map { case (path, parseResult) => (path.toString, parseResult.get) }))
    }
  }
}
