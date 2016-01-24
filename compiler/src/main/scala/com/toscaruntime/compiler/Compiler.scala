package com.toscaruntime.compiler

import java.nio.file._
import java.util.Comparator
import java.util.regex.Pattern

import com.toscaruntime.compiler.tosca._
import com.toscaruntime.constant.CompilerConstant
import com.toscaruntime.exception.{DependencyNotFoundException, InvalidTopologyException}
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

  private def resolveDependenciesForCompilationWithRepository(repository: Path) = { (dependencyDefinition: String, basePath: Option[Path]) =>
    resolveWithRepository(repositoryDependencyPattern.pattern, dependencyDefinition, repository).map {
      case (dependencyName, dependencyVersion, resolvedDependency) => (dependencyName + ":" + dependencyVersion, resolvedDependency.resolve(CompilerConstant.ARCHIVE_FOLDER).resolve(dependencyName))
    }
  }

  private def resolveDependenciesForAssemblyWithRepository(repository: Path) = { (dependencyDefinition: String, basePath: Option[Path]) =>
    resolveWithRepository(repositoryDependencyPattern.pattern, dependencyDefinition, repository)
  }

  private def resolveWithRepository(pattern: Pattern, dependencyDefinition: String, repository: Path) = {
    val matcher = pattern.matcher(dependencyDefinition)
    if (matcher.matches()) {
      val dependencyName = matcher.group(1)
      if (!Files.exists(repository.resolve(dependencyName))) {
        None
      } else {
        val dependencyVersion = matcher.group(2)
        if ("*" == dependencyVersion) {
          val maxVersionFound = Files.list(repository.resolve(dependencyName)).max(new Comparator[Path] {
            override def compare(o1: Path, o2: Path): Int = ToscaVersion(o1.getFileName.toString).compare(ToscaVersion(o2.getFileName.toString))
          })
          if (maxVersionFound.isPresent) {
            logger.debug(s"Wildcard dependency $dependencyDefinition resolved to ${maxVersionFound.get()}")
            Some((dependencyName, maxVersionFound.get().getFileName.toString, maxVersionFound.get()))
          } else None
        } else {
          val resolvedDependency = repository.resolve(dependencyName).resolve(dependencyVersion)
          if (!Files.exists(resolvedDependency)) {
            None
          } else Some((dependencyName, dependencyVersion, resolvedDependency))
        }
      }
    } else None
  }

  def resolveDependency(csarName: String,
                        csarVersion: String,
                        repository: Path) = {
    val dependencyResolver = resolveDependenciesForCompilationWithRepository(repository)
    dependencyResolver(csarName + ":" + csarVersion, None).map(_._2)
  }

  def compile(csarName: String,
              csarVersion: String,
              repository: Path): CompilationResult = {
    val dependencyResolver = resolveDependenciesForCompilationWithRepository(repository)
    val csarPathOpt = dependencyResolver(csarName + ":" + csarVersion, None)
    csarPathOpt.map(csarPath => compile(csarPath._2, dependencyResolver)).getOrElse(throw new DependencyNotFoundException(s"Csar $csarName:$csarVersion not found in repository $repository"))
  }

  def compile(path: Path, repository: Path): CompilationResult = {
    compile(path, resolveDependenciesForCompilationWithRepository(repository))
  }

  def install(path: Path, repository: Path): CompilationResult = {
    val dependencyResolver = resolveDependenciesForCompilationWithRepository(repository)
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
              dependencyResolver: (String, Option[Path]) => Option[(String, Path)],
              compilationCache: mutable.Map[String, CompilationResult] = mutable.Map.empty,
              postCompilation: (CompilationResult) => Unit = { _ => }): CompilationResult = {
    logger.debug("Compile {}", path.getFileName.toString)
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
    val csar = Csar(absPath.toString, definitions)
    if (errors.isEmpty) {
      val dependencies = definitions.toSeq.flatMap { definition =>
        definition._2.imports.getOrElse(List.empty).map { defImport =>
          (definition._1, defImport)
        }
      }.map { dependency =>
        (dependency, dependencyResolver(dependency._2.value, Some(absPath)))
      }

      val importErrors = dependencies.filter(_._2.isEmpty).map {
        case ((defFile, importToken), _) => (defFile, CompilationError(s"Dependency ${importToken.value} not found", importToken.pos, Some(importToken.value)))
      }.groupBy(_._1).map {
        case (defFile, errorsWithDefFile) => (defFile, errorsWithDefFile.map(_._2).toList)
      }

      if (importErrors.nonEmpty) {
        val result = CompilationResult(path = absPath, csar = csar, errors = importErrors)
        postCompilation(result)
        result
      } else {
        val dependenciesCompilationResult = dependencies.map {
          case ((defFile, ParsedValue(dependency)), Some((dependencyId, dependencyPath))) =>
            if (compilationCache.contains(dependencyId)) {
              logger.debug("Compile {}, hit cache for dependency {}", path.getFileName.toString, dependency)
              val dependencyCompilationResult = compilationCache.get(dependencyId).get
              (dependencyId, dependencyCompilationResult)
            } else {
              logger.debug("Compile {}, compiling transitive dependency {}", path.getFileName.toString, dependency)
              val dependencyCompilationResult = compile(dependencyPath, dependencyResolver, compilationCache)
              compilationCache.put(dependencyId, dependencyCompilationResult)
              (dependencyId, dependencyCompilationResult)
            }
        }.toMap
        val dependenciesCsars = dependenciesCompilationResult.flatMap {
          case (_, depRes) => depRes.dependencies + (depRes.csar.csarId -> depRes.csar)
        }
        val dependenciesErrors = dependenciesCompilationResult.flatMap {
          case (_, depRes) => depRes.errors
        }
        val semanticErrors = SemanticAnalyzer.analyze(csar, absPath, dependenciesCsars.values.toList)
        val result = CompilationResult(absPath, csar, dependenciesCsars, semanticErrors ++ dependenciesErrors)
        postCompilation(result)
        result
      }
    } else {
      val result = CompilationResult(path = absPath, csar = csar, errors = errors)
      postCompilation(result)
      result
    }
  }

  def assembly(topologyPath: Path,
               outputPath: Path,
               repositoryPath: Path,
               inputs: Option[Path]): CompilationResult = {
    assembly(topologyPath, outputPath, repositoryPath, inputs, resolveDependenciesForAssemblyWithRepository(repositoryPath))
  }

  def assembly(topologyPath: Path,
               outputPath: Path,
               repositoryPath: Path,
               inputs: Option[Path],
               assemblyDependenciesResolver: (String, Option[Path]) => Option[(String, String, Path)]): CompilationResult = {
    val topologyCompilationResult = compile(topologyPath, repositoryPath)
    val inputsParseResult = inputs.map { inputsPath =>
      SyntaxAnalyzer.parse(SyntaxAnalyzer.topologyExternalInputs, FileUtil.readTextFile(inputsPath))
    }
    if (topologyCompilationResult.isSuccessful && (inputsParseResult.isEmpty || (inputsParseResult.isDefined && inputsParseResult.get.successful))) {
      val inputsValues = inputsParseResult.map(_.get).getOrElse(Map.empty)
      val definitionWithTopologyEntry = topologyCompilationResult.csar.definitions.find(definition => definition._2.topologyTemplate.isDefined)
      if (definitionWithTopologyEntry.isEmpty) {
        throw new InvalidTopologyException(s"No topology found at ${topologyPath.toAbsolutePath.toString} for assembly")
      } else {
        logger.debug(s"Performing assembly for topology at ${definitionWithTopologyEntry.get._1}")
      }
      val definitionWithTopology = definitionWithTopologyEntry.get._2
      val topology = definitionWithTopology.topologyTemplate.get
      val dependenciesTopologies = topologyCompilationResult.dependencies.filter {
        case (csarPath, csar) => csar.definitions.nonEmpty && csar.definitions.head._2.topologyTemplate.isDefined
      }.map {
        case (csarPath, csar) =>
          logger.debug(s"Merging topology at ${csar.definitions.head._1} into ${topologyPath.toString} for assembly")
          csar.definitions.head._2.topologyTemplate.get
      }.toList
      val mergedTopology = (dependenciesTopologies :+ topology).foldLeft(TopologyTemplate(topology.description, Some(Map.empty), Some(Map.empty), Some(Map.empty))) { (merged, current) =>
        val mergedInputs = merged.inputs.get ++ current.inputs.getOrElse(Map.empty)
        val mergedOutputs = merged.outputs.get ++ current.outputs.getOrElse(Map.empty)
        val mergedNodeTemplates = merged.nodeTemplates.get ++ current.nodeTemplates.getOrElse(Map.empty)
        TopologyTemplate(merged.description, Some(mergedInputs), Some(mergedOutputs), Some(mergedNodeTemplates))
      }
      val inputErrors = mergedTopology.inputs.getOrElse(Map.empty).flatMap {
        case (inputName, inputDefinition) =>
          if (inputDefinition.required.value && !inputsValues.contains(inputName)) {
            List(CompilationError(s"Input [${inputName.value}] required but not defined", inputName.pos, Some(inputName.value)))
          } else List.empty
      }.toList
      if (inputErrors.isEmpty) {
        val mergedDefinitions = topologyCompilationResult.csar.definitions + (definitionWithTopologyEntry.get._1 -> definitionWithTopologyEntry.get._2.copy(topologyTemplate = Some(mergedTopology)))
        val mergedCsar = Csar(topologyPath.toAbsolutePath.toString, mergedDefinitions)
        CodeGenerator.generate(mergedCsar, topologyCompilationResult.dependencies.values.toList, topologyPath, outputPath)
        topologyCompilationResult.dependencies.foreach {
          case (csarId, csar) =>
            val assemblyDependency = assemblyDependenciesResolver(csarId, None).get._3
            FileUtil.copy(assemblyDependency, outputPath, StandardCopyOption.REPLACE_EXISTING)
        }
        CodeGenerator.generate(mergedCsar, topologyCompilationResult.dependencies.values.toList, topologyPath, outputPath)
        topologyCompilationResult.copy(csar = mergedCsar)
      } else {
        val errorKeyForTopology = topologyPath.toAbsolutePath.toString
        val allErrors = inputErrors ++ topologyCompilationResult.errors.getOrElse(errorKeyForTopology, List.empty)
        topologyCompilationResult.copy(errors = topologyCompilationResult.errors + (errorKeyForTopology -> allErrors))
      }
    } else {
      logger.debug(s"Topology ${topologyPath.toString} has compilation errors")
      topologyCompilationResult
    }
  }
}
