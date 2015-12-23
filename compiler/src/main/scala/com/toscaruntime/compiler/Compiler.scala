package com.toscaruntime.compiler

import java.nio.file._
import java.util.regex.Pattern

import com.google.common.io.Closeables
import com.toscaruntime.compiler.tosca.{CompilationError, CompilationResult, Csar, Definition}
import com.toscaruntime.constant.CompilerConstant
import com.toscaruntime.exception.{DependencyNotFoundException, InitializationException}
import com.toscaruntime.util.FileUtil
import com.typesafe.scalalogging.LazyLogging

import scala.collection.JavaConverters._
import scala.collection.mutable

/**
  * Entry point to compile csars to its java implementation
  *
  * @author Minh Khang VU
  */
object Compiler extends LazyLogging {

  val repositoryDependencyPattern = """([^\s:]*):([^\s]*)""".r

  private def resolveDependencies(repository: Path) = { (dependencyDefinition: String, basePath: Path) =>
    checkRepositoryPattern(repositoryDependencyPattern.pattern, dependencyDefinition, basePath, repository).getOrElse(basePath.resolve(dependencyDefinition))
  }

  private def checkRepositoryPattern(pattern: Pattern, dependencyDefinition: String, basePath: Path, repository: Path) = {
    val matcher = pattern.matcher(dependencyDefinition)
    if (matcher.matches()) {
      val dependencyName = matcher.group(1)
      val dependencyVersion = matcher.group(2)
      val resolvedDependency = repository.resolve(dependencyName).resolve(dependencyVersion).resolve(CompilerConstant.ARCHIVE_FOLDER).resolve(dependencyName)
      if (!Files.exists(resolvedDependency)) {
        throw new DependencyNotFoundException("Dependency " + dependencyDefinition + " not found")
      } else Some(resolvedDependency)
    } else None
  }

  def compile(path: Path, repository: Path): CompilationResult = {
    compile(path, resolveDependencies(repository))
  }

  def install(path: Path, repository: Path): CompilationResult = {
    val dependencyResolver = resolveDependencies(repository)
    compile(path, dependencyResolver, mutable.Map.empty, {
      case compilationResult: CompilationResult =>
        if (compilationResult.isSuccessful) {
          logger.info("Build successful for path {}", path.toString)
          logger.info("Installing {} to {}", compilationResult.csar.csarName + ":" + compilationResult.csar.csarVersion, repository.toString)
          val compilationOutput = repository.resolve(compilationResult.csar.csarName).resolve(compilationResult.csar.csarVersion)
          if (Files.exists(compilationOutput)) {
            FileUtil.delete(compilationOutput)
          }
          Files.createDirectories(compilationOutput)
          CodeGenerator.generate(compilationResult.csar, compilationResult.dependencies.values.toList, path, compilationOutput)
        } else {
          logger.info("Build failed for path {}, will not install the csar", path.toString)
        }
    })
  }

  def compile(path: Path,
              dependencyResolver: (String, Path) => Path,
              compilationCache: mutable.Map[String, CompilationResult] = mutable.Map.empty,
              postCompilation: (CompilationResult) => Unit = { _ => }): CompilationResult = {
    logger.info("Compile {}", path.getFileName.toString)
    val absPath = path.toAbsolutePath
    val yamlFiles = FileUtil.listFiles(absPath, ".yml", ".yaml").asScala.toList
    val parseResults = yamlFiles.map { yamlPath =>
      val toscaDefinitionText = FileUtil.readTextFile(yamlPath)
      (yamlPath.toAbsolutePath.toString, SyntaxAnalyzer.parse(SyntaxAnalyzer.definition, toscaDefinitionText))
    }.toMap
    val definitions = parseResults.filter(_._2.successful).map {
      case (filePath, success: SyntaxAnalyzer.Success[Definition]) => (filePath, success.get)
    }
    val errors = parseResults.filter(!_._2.successful).map {
      case (fileName, error: SyntaxAnalyzer.NoSuccess) => (fileName, List(CompilationError(error.msg, error.next.pos, None)))
    }
    val csar = Csar(absPath, definitions)
    if (errors.isEmpty) {
      val dependencies = definitions.values.flatMap { definition =>
        definition.imports.map(_.map(_.value).toSet).getOrElse(Set.empty)
      }.toSet
      val dependenciesCompilationResult = dependencies.map { dependency =>
        if (compilationCache.contains(dependency)) {
          logger.info("Compile {}, hit cache for dependency {}", path.getFileName.toString, dependency)
          val dependencyCompilationResult = compilationCache.get(dependency).get
          (dependencyCompilationResult.path.toString, dependencyCompilationResult)
        } else {
          logger.info("Compile {}, compiling transitive dependency {}", path.getFileName.toString, dependency)
          val dependencyPath = dependencyResolver(dependency, absPath).toAbsolutePath
          val dependencyCompilationResult = compile(dependencyPath, dependencyResolver, compilationCache)
          compilationCache.put(dependency, dependencyCompilationResult)
          (dependencyCompilationResult.path.toString, dependencyCompilationResult)
        }
      }.toMap
      val dependenciesCsars = dependenciesCompilationResult.flatMap {
        case (_, depRes) => depRes.dependencies + (depRes.path.toString -> depRes.csar)
      }
      val dependenciesErrors = dependenciesCompilationResult.flatMap {
        case (_, depRes) => depRes.errors
      }
      val semanticErrors = SemanticAnalyzer.analyze(csar, absPath, dependenciesCsars.values.toList)
      val result = CompilationResult(absPath, csar, dependenciesCsars, semanticErrors ++ dependenciesErrors)
      postCompilation(result)
      result
    } else {
      val result = CompilationResult(path = absPath, csar = csar, errors = errors)
      postCompilation(result)
      result
    }
  }

  def loadNormativeTypes(normativeTypesPath: Path): Csar = {
    val parsedCsar = analyzeSyntax(normativeTypesPath)
    if (parsedCsar.isDefined) {
      val semanticErrors = analyzeSemantic(parsedCsar.get, normativeTypesPath, List.empty)
      if (semanticErrors.isEmpty) {
        parsedCsar.get
      } else {
        throw new InitializationException("Errors exist in normative types, cannot load compiler")
      }
    } else {
      throw new InitializationException("Errors exist in normative types, cannot load compiler")
    }
  }

  /**
    * Batch compile a topology with all its dependencies and produce a deployable package
    * @param topology the topology to produce deployment for
    * @param dependencies the topology's dependencies
    * @param provider the provider's binary package
    * @param normativeTypesPath path to the normative types definition
    * @param output output for the deployable package
    */
  def compile(topology: Path, dependencies: List[Path], provider: Path, normativeTypesPath: Path, output: Path): Boolean = {
    val normativeTypes = loadNormativeTypes(normativeTypesPath)
    var topologyFileSystem = topology
    val topologyIsZipped = Files.isRegularFile(topology)
    if (topologyIsZipped) {
      topologyFileSystem = FileUtil.createZipFileSystem(topology)
    }
    var providerFileSystem = provider
    val providerIsZipped = Files.isRegularFile(provider)
    if (providerIsZipped) {
      providerFileSystem = FileUtil.createZipFileSystem(provider)
    }
    var outputFileSystem = output
    val outputIsZipped = !Files.isDirectory(output)
    if (outputIsZipped) {
      Files.createDirectories(output.getParent)
      outputFileSystem = FileUtil.createZipFileSystem(output)
    }
    // Load dependencies
    val dependenciesFileSystems = CompilerUtil.createZipFileSystems(dependencies)
    try {
      val parsedDependenciesWithPaths = dependenciesFileSystems.flatMap {
        case (dependencyPath: Path, _) => analyzeSyntax(dependencyPath).map((_, dependencyPath))
      }
      if (parsedDependenciesWithPaths.size < dependencies.size) {
        logger.error("Syntax error detected in dependencies, check output log for details")
        return false
      }
      val providerCsar = analyzeSyntax(providerFileSystem.resolve(CompilerConstant.ARCHIVE_FOLDER))
      if (providerCsar.isEmpty) {
        logger.error("Syntax error detected in provider definitions, check output log for details")
        return false
      } else {
        val semanticErrors = analyzeSemantic(providerCsar.get, providerFileSystem, List(normativeTypes))
        if (semanticErrors.nonEmpty) {
          logger.error("Semantic error detected in provider definitions, check output log for details")
          return false
        } else {
          FileUtil.copy(providerFileSystem, outputFileSystem, StandardCopyOption.REPLACE_EXISTING)
        }
      }
      val parsedDependencies = parsedDependenciesWithPaths.map(_._1)
      for ((parsedDependencyWithPath, index) <- parsedDependenciesWithPaths.zipWithIndex) {
        val currentDependencies = normativeTypes :: providerCsar.get :: parsedDependencies.slice(0, index)
        val semanticErrors = analyzeSemantic(parsedDependencyWithPath._1, parsedDependencyWithPath._2, currentDependencies)
        if (semanticErrors.nonEmpty) {
          logger.error("Semantic error with " + parsedDependencyWithPath._1)
          return false
        } else {
          CodeGenerator.generate(parsedDependencyWithPath._1, currentDependencies, parsedDependencyWithPath._2, outputFileSystem)
        }
      }
      val parsedTopology = analyzeSyntax(topologyFileSystem)
      if (parsedTopology.isDefined) {
        val topologyDependencies = normativeTypes :: providerCsar.get :: parsedDependencies
        val semanticErrors = analyzeSemantic(parsedTopology.get, topologyFileSystem, topologyDependencies)
        if (semanticErrors.isEmpty) {
          CodeGenerator.generate(normativeTypes, List.empty, normativeTypesPath, outputFileSystem)
          CodeGenerator.generate(parsedTopology.get, topologyDependencies, topologyFileSystem, outputFileSystem)
          logger.info("Deployment generated to " + output)
          return true
        }
      }
      false
    } finally {
      if (topologyIsZipped) {
        Closeables.close(topologyFileSystem.getFileSystem, true)
      }
      if (outputIsZipped) {
        Closeables.close(outputFileSystem.getFileSystem, true)
      }
      if (providerIsZipped) {
        Closeables.close(provider.getFileSystem, true)
      }
      dependenciesFileSystems.filter(_._2.isDefined).foreach { case (_, fileSystem: Option[FileSystem]) =>
        Closeables.close(fileSystem.get, true)
      }
    }
  }

  def analyzeSemantic(parsedCsar: Csar, csarFileSystem: Path, parsedDependencies: List[Csar]) = {
    // Analyze semantic to be sure that there are no errors
    val semanticErrors = SemanticAnalyzer.analyze(parsedCsar, csarFileSystem, parsedDependencies)
    if (semanticErrors.isEmpty) {
      logger.info("CSAR's semantic analyzer has finished successfully for " + parsedCsar.path)
    } else {
      logger.error("Semantic errors for " + parsedCsar.path + " :")
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
    val allYamlFiles = FileUtil.listFiles(csarPath, ".yml", ".yaml").asScala.toList
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
      Some(Csar(csarPath, successFullResults.map { case (path, parseResult) => (path.toString, parseResult.get) }))
    }
  }
}
