package com.mkv.tosca.compiler.parser

import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

import com.mkv.tosca.compiler.{Deployer, SyntaxAnalyzer, SemanticAnalyzer, CodeGenerator}
import com.mkv.tosca.compiler.tosca.Csar

import scala.collection.mutable.ListBuffer
import scala.io.Source

object ParserTest {

  def main(args: Array[String]): Unit = {
    val normativePath = Paths.get("src/test/resources/components/tosca-normative-types")
    val apachePath = Paths.get("src/test/resources/components/apache")
    val mysqlPath = Paths.get("src/test/resources/components/mysql")
    val phpPath = Paths.get("src/test/resources/components/php")
    val wordpressPath = Paths.get("src/test/resources/components/wordpress")
    val wordpressTopology = Paths.get("src/test/resources/topologies/wordpress")
    val dockerPath = Paths.get("/Users/mkv/tosca-runtime/docker/src/main/resources/tosca")
    val output = Paths.get("./target/tosca")

    val normativeCsar = compile(normativePath, List.empty)
    if (normativeCsar.isEmpty) {
      return
    } else {
      CodeGenerator.generateTypesForCsar(normativeCsar.get, output)
    }
    val dockerCsar = compile(dockerPath, List(normativePath))
    val apacheCsar = compile(apachePath, List(normativePath))
    if (apacheCsar.isEmpty) {
      return
    } else {
      CodeGenerator.generateTypesForCsar(apacheCsar.get, output)
    }
    val mysqlCsar = compile(mysqlPath, List(normativePath))
    if (mysqlCsar.isEmpty) {
      return
    } else {
      CodeGenerator.generateTypesForCsar(mysqlCsar.get, output)
    }
    val phpCsar = compile(phpPath, List(normativePath))
    if (phpCsar.isEmpty) {
      return
    } else {
      CodeGenerator.generateTypesForCsar(phpCsar.get, output)
    }
    val wordpressCsar = compile(wordpressPath, List(normativePath, apachePath, mysqlPath, phpPath))
    if (wordpressCsar.isEmpty) {
      return
    } else {
      CodeGenerator.generateTypesForCsar(wordpressCsar.get, output)
    }
    val wordpressTopologyCsar = compile(wordpressTopology, List(wordpressPath, normativePath, apachePath, mysqlPath, phpPath, dockerPath))
    if (wordpressTopologyCsar.isEmpty) {
      return
    } else {
      CodeGenerator.generate(wordpressTopologyCsar.get, List(wordpressCsar.get, normativeCsar.get, apacheCsar.get, mysqlCsar.get, phpCsar.get, dockerCsar.get), output)
    }
    Deployer.deploy(output)
  }

  def compile(csar: Path, csarPath: List[Path]) = {
    val dependencies = csarPath.flatMap(parse)
    val parsedCsar = parse(csar)
    if (parsedCsar.isEmpty) {
      None
    } else {
      if (analyze(parsedCsar.get, dependencies).isEmpty) {
        parsedCsar
      } else {
        None
      }
    }
  }

  def analyze(csar: Csar, csarPath: List[Csar]) = {
    val semanticErrors = SemanticAnalyzer.analyze(csar, csarPath)
    if (semanticErrors.nonEmpty) {
      println("Semantic errors for " + csar.path + " :")
      semanticErrors.foreach {
        case (filePath, errors) => {
          println("For file : " + filePath)
          errors.foreach { error =>
            println("Line " + error.startPosition + " : " + error.error)
          }
          println()
        }
      }
      Some(semanticErrors)
    } else {
      println("CSAR's semantic analyzer has finished successfully for " + csar.path)
      None
    }
  }

  def parse(csarPath: Path) = {
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
      val toscaDefinitionText = Source.fromInputStream(Files.newInputStream(yamlPath)).mkString
      (yamlPath, SyntaxAnalyzer.parse(SyntaxAnalyzer.definition, toscaDefinitionText))
    }.toMap
    val errorParseResults = allParseResults.filter(!_._2.successful)
    val successFullResults = allParseResults.filter(_._2.successful)
    if (errorParseResults.nonEmpty) {
      println("Syntax errors for " + csarPath + " :")
      errorParseResults.foreach {
        case (filePath, parseResult) => {
          println("For file : " + filePath)
          println(parseResult)
        }
      }
      None
    } else {
      println("CSAR's syntax analyzer has finished successfully for " + csarPath)
      Some(Csar(successFullResults.map { case (path, parseResult) => (path, parseResult.get) }, recipePath))
    }
  }
}
