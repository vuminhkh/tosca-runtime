package com.mkv.tosca.compiler

import java.nio.file._

import com.google.common.io.Closeables
import com.mkv.tosca.compiler.tosca.Csar
import com.mkv.util.FileUtil
import com.typesafe.scalalogging.LazyLogging

import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer

/**
 * Entry point to compile csars to its java implementation
 *
 * @author Minh Khang VU
 */
object Compiler extends LazyLogging {

  def compile(csar: Path, csarPath: List[Path], output: Path): Unit = {
    // Create the file system based on the csar path
    val csarName = csar.getFileName.toString
    var recipePath = csar
    val fileSystemsToClose = ListBuffer[FileSystem]()
    if (Files.isRegularFile(csar)) {
      recipePath = FileUtil.createZipFileSystem(csar)
      fileSystemsToClose += recipePath.getFileSystem
    }
    // Load dependencies
    val dependenciesPaths = csarPath.map { dependencyPath =>
      if (!Files.isDirectory(dependencyPath)) {
        val zipPath = FileUtil.createZipFileSystem(dependencyPath)
        fileSystemsToClose += zipPath.getFileSystem
        zipPath
      } else {
        dependencyPath
      }
    }
    try {
      val dependencies = dependenciesPaths.flatMap(dependencyPath => analyzeSyntax(dependencyPath.resolve(Constant.ARCHIVE_FOLDER)))
      if (dependencies.size < csarPath.size) {
        logger.error("Syntax error detected in dependencies, check output log for details")
        return
      }
      // Parse the csar to construct syntax tree
      val parsedCsar = analyzeSyntax(recipePath)
      if (parsedCsar.isDefined) {
        // Analyze semantic to be sure that there are no errors
        val semanticErrors = SemanticAnalyzer.analyze(parsedCsar.get, recipePath, dependencies)
        if (semanticErrors.isEmpty) {
          logger.info("CSAR's semantic analyzer has finished successfully for " + csarName)
          CodeGenerator.generate(parsedCsar.get, dependencies, recipePath, output)
          logger.info("Code generated to " + output)
        } else {
          logger.error("Semantic errors for " + csarName + " :")
          semanticErrors.foreach {
            case (filePath, errors) => {
              logger.error("For file : " + filePath)
              errors.foreach { error =>
                logger.error("Line " + error.startPosition + " : " + error.error)
              }
              println()
            }
          }
        }
      }
    } finally {
      fileSystemsToClose.foreach { fileSystem =>
        Closeables.close(fileSystem, true)
      }
    }
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
        case (filePath, parseResult) => {
          logger.error("For file : " + filePath)
          logger.error(parseResult.toString)
        }
      }
      None
    } else {
      logger.info("CSAR's syntax analyzer has finished successfully for " + csarPath)
      Some(Csar(successFullResults.map { case (path, parseResult) => (path.toString, parseResult.get) }))
    }
  }
}
