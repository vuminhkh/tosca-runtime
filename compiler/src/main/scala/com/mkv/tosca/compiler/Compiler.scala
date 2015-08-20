package com.mkv.tosca.compiler

import java.nio.file.attribute.BasicFileAttributes
import java.nio.file._

import com.mkv.tosca.compiler.tosca.Csar
import com.typesafe.scalalogging.LazyLogging

import scala.collection.mutable.ListBuffer

/**
 * Entry point to compile csars to its java implementation
 *
 * @author Minh Khang VU
 */
object Compiler extends LazyLogging {

  def compile(csar: Path, csarPath: List[Path], output: Path): Unit = {
    val dependencies = csarPath.flatMap(analyzeSyntax)
    if(dependencies.size < csarPath.size) {
      logger.error("Errors happened while parsing dependencies")
      return
    }
    val parsedCsar = analyzeSyntax(csar)
    if (parsedCsar.isDefined) {
      val semanticErrors = SemanticAnalyzer.analyze(parsedCsar.get, dependencies)
      if (semanticErrors.isEmpty) {
        logger.info("CSAR's semantic analyzer has finished successfully for " + parsedCsar.get.path)
        CodeGenerator.generateTypesForCsar(parsedCsar.get, output)
        logger.info("Code generated to " + output)
      } else {
        logger.error("Semantic errors for " + parsedCsar.get.path + " :")
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
  }

  def analyzeSyntax(csarPath: Path) = {
    var recipePath = csarPath
    if (Files.isRegularFile(recipePath)) {
      val recipeFileSystem = FileSystems.newFileSystem(recipePath, null)
      recipePath = recipeFileSystem.getPath("/")
    }
    val allYamlFiles = ListBuffer[Path]()
    Files.walkFileTree(recipePath, new SimpleFileVisitor[Path]() {
      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
        val fileName = file.getFileName.toString
        if (fileName.endsWith(".yml") || fileName.endsWith(".yaml")) {
          allYamlFiles += file
        }
        super.visitFile(file, attrs)
      }
    })
    val allParseResults = allYamlFiles.toList.map { yamlPath =>
      val toscaDefinitionText = new String(Files.readAllBytes(yamlPath))
      (yamlPath, SyntaxAnalyzer.parse(SyntaxAnalyzer.definition, toscaDefinitionText))
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
      Some(Csar(successFullResults.map { case (path, parseResult) => (path, parseResult.get) }, recipePath))
    }
  }
}
