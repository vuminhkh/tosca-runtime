package com.toscaruntime.compiler

import java.nio.file._
import java.util.Comparator
import java.util.function.Predicate
import java.util.regex.Pattern

import com.toscaruntime.compiler.tosca._
import com.toscaruntime.compiler.util.CompilerUtil
import com.toscaruntime.constant.CompilerConstant
import com.toscaruntime.exception.compilation.{DependencyNotFoundException, EmptyArchiveException, InvalidTopologyException}
import com.toscaruntime.tosca.ToscaVersion
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

  val repositoryDependencyPattern = """([^:]*):([^:]*)""".r

  private def resolveToscaRecipe(repository: Path) = { dependencyDefinition: String =>
    doResolve(repositoryDependencyPattern.pattern, dependencyDefinition, repository).map {
      case (dependencyName, dependencyVersion, resolvedDependency) =>
        (dependencyName + ":" + dependencyVersion,
          resolvedDependency.resolve(CompilerConstant.ARCHIVE_FOLDER).resolve(CompilerUtil.normalizeCSARName(dependencyName)))
    }
  }

  private def resolveCsarPath(repository: Path) = { dependencyDefinition: String =>
    doResolve(repositoryDependencyPattern.pattern, dependencyDefinition, repository)
  }

  private def doResolve(pattern: Pattern, dependencyDefinition: String, repository: Path) = {
    val matcher = pattern.matcher(dependencyDefinition)
    if (matcher.matches()) {
      val dependencyName = matcher.group(1)
      val dependencyPath = repository.resolve(CompilerUtil.normalizeCSARName(dependencyName))
      if (!Files.exists(dependencyPath)) {
        None
      } else {
        val dependencyVersion = matcher.group(2)
        if ("*" == dependencyVersion) {
          val maxVersionFound = Files.list(dependencyPath).filter(new Predicate[Path] {
            override def test(t: Path): Boolean = Files.isReadable(t) && Files.isDirectory(t) && !Files.isHidden(t)
          }).max(new Comparator[Path] {
            override def compare(o1: Path, o2: Path): Int = ToscaVersion(o1.getFileName.toString).compare(ToscaVersion(o2.getFileName.toString))
          })
          if (maxVersionFound.isPresent) {
            logger.debug(s"Wildcard dependency $dependencyDefinition resolved to ${maxVersionFound.get()}")
            Some((dependencyName, maxVersionFound.get().getFileName.toString, maxVersionFound.get()))
          } else None
        } else {
          val resolvedDependency = dependencyPath.resolve(dependencyVersion)
          if (!Files.exists(resolvedDependency)) {
            None
          } else Some((dependencyName, dependencyVersion, resolvedDependency))
        }
      }
    } else None
  }

  /**
    * This method resolve a dependency and point to its recipe's directory
    *
    * @param csarName    name of the csar
    * @param csarVersion version of the csar
    * @param repository  repository
    * @return path to the recipe's directory of the csar
    */
  def getCsarToscaRecipePath(csarName: String, csarVersion: String, repository: Path) = {
    val dependencyResolver = resolveToscaRecipe(repository)
    dependencyResolver(csarName + ":" + csarVersion).map(_._2)
  }

  /**
    * This method resolve a dependency and returns its path in the repository
    *
    * @param csarName    name of the csar
    * @param csarVersion version of the csar
    * @param repository  repository
    * @return path to the recipe's directory of the csar
    */
  def getCsarPath(csarName: String, csarVersion: String, repository: Path) = {
    val dependencyResolver = resolveCsarPath(repository)
    dependencyResolver(csarName + ":" + csarVersion).map(_._3)
  }

  def compile(csarName: String,
              csarVersion: String,
              repository: Path): CompilationResult = {
    val dependencyResolver = resolveToscaRecipe(repository)
    val csarPathOpt = dependencyResolver(csarName + ":" + csarVersion)
    csarPathOpt.map(csarPath => compile(csarPath._2, dependencyResolver)).getOrElse(throw new DependencyNotFoundException(s"Csar $csarName:$csarVersion not found in repository $repository"))
  }

  def compile(path: Path, repository: Path): CompilationResult = {
    compile(path, resolveToscaRecipe(repository))
  }

  private def getDependenciesBinaries(repository: Path, compilationResult: CompilationResult) = {
    val installedDependencyResolver = resolveCsarPath(repository)
    compilationResult.dependencies.keys.map(installedDependencyResolver).map(_.get).map(_._3.resolve(CompilerConstant.TARGET_FOLDER)).toList
  }

  def install(path: Path, repository: Path): CompilationResult = {
    val dependencyToscaRecipeResolver = resolveToscaRecipe(repository)
    compile(path, dependencyToscaRecipeResolver, mutable.Map.empty, {
      compilationResult: CompilationResult =>
        if (compilationResult.isSuccessful) {
          logger.info("Build successful for path {}", path.toString)
          logger.info("Installing {} to {}", compilationResult.csar.csarName + ":" + compilationResult.csar.csarVersion, repository.toString)
          val compilationOutput = repository.resolve(CompilerUtil.normalizeCSARName(compilationResult.csar.csarName)).resolve(compilationResult.csar.csarVersion)
          if (Files.exists(compilationOutput)) {
            FileUtil.delete(compilationOutput)
          }
          Files.createDirectories(compilationOutput)
          CodeGenerator.generate(compilationResult.csar, compilationResult.dependencies.values.toList, getDependenciesBinaries(repository, compilationResult), path, compilationOutput)
        } else {
          logger.info("Build failed for path {}, will not install the csar", path.toString)
        }
    })
  }

  def compile(path: Path,
              dependencyResolver: String => Option[(String, Path)],
              compilationCache: mutable.Map[String, CompilationResult] = mutable.Map.empty,
              postCompilation: (CompilationResult) => Unit = { _ => },
              switchOnAnalyzeSemantic: Boolean = true): CompilationResult = {
    logger.info("Compile {}", path)
    val absPath = path.toAbsolutePath
    val yamlFiles = FileUtil.listFiles(absPath, ".yml", ".yaml").asScala.toList
    if (yamlFiles.isEmpty) {
      throw new EmptyArchiveException(s"Archive is empty, expecting tosca definition with extension .yml or .yaml")
    }
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
        (dependency, dependencyResolver(dependency._2.value))
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
              logger.debug("Compile {}, hit cache for dependency {}", path, dependency)
              val dependencyCompilationResult = compilationCache(dependencyId)
              (dependencyId, dependencyCompilationResult)
            } else {
              logger.debug("Compile {}, compiling transitive dependency {}", path, dependency)
              // Don't perform semantic analyze on dependency as it's already been done when the dependencies were installed
              val dependencyCompilationResult = compile(dependencyPath, dependencyResolver, compilationCache, _ => {}, switchOnAnalyzeSemantic = false)
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
        if (switchOnAnalyzeSemantic) {
          val semanticErrors = SemanticAnalyzer.analyze(csar, absPath, dependenciesCsars.values.toList)
          val result = CompilationResult(absPath, csar, dependenciesCsars, semanticErrors ++ dependenciesErrors)
          postCompilation(result)
          result
        } else {
          val result = CompilationResult(absPath, csar, dependenciesCsars, dependenciesErrors)
          postCompilation(result)
          result
        }
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
               inputs: Option[Path],
               pluginsPaths: List[Path] = List.empty): CompilationResult = {
    assembly(topologyPath, outputPath, repositoryPath, inputs, resolveCsarPath(repositoryPath), pluginsPaths)
  }

  def assembly(topologyPath: Path,
               outputPath: Path,
               repositoryPath: Path,
               inputs: Option[Path],
               assemblyDependenciesResolver: String => Option[(String, String, Path)],
               pluginsPaths: List[Path]): CompilationResult = {
    val topologyCompilationResult = compile(topologyPath, resolveToscaRecipe(repositoryPath), mutable.Map.empty, _ => {}, switchOnAnalyzeSemantic = false)
    val inputsParseResult = inputs.map { inputsPath =>
      SyntaxAnalyzer.parse(SyntaxAnalyzer.topologyExternalInputs, FileUtil.readTextFile(inputsPath))
    }
    if (topologyCompilationResult.isSuccessful && (inputsParseResult.isEmpty || (inputsParseResult.isDefined && inputsParseResult.get.successful))) {
      val inputsValues = inputsParseResult.map(_.get).getOrElse(Map.empty)
      val definitionWithTopologyEntry = topologyCompilationResult.csar.definitions.find(definition => definition._2.topologyTemplate.isDefined)
      if (definitionWithTopologyEntry.isEmpty) {
        throw new InvalidTopologyException(s"No topology found at ${topologyPath.toAbsolutePath.toString} for assembly")
      } else {
        logger.info(s"Performing assembly for topology at ${definitionWithTopologyEntry.get._1}")
      }
      val definitionWithTopology = definitionWithTopologyEntry.get._2
      val topology = definitionWithTopology.topologyTemplate.get
      val dependenciesTopologies = topologyCompilationResult.dependencies.filter {
        case (csarPath, csar) => csar.definitions.nonEmpty && csar.definitions.head._2.topologyTemplate.isDefined
      }.map {
        case (csarPath, csar) =>
          logger.info(s"Merging topology at ${csar.definitions.head._1} into ${topologyPath.toString} for assembly")
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
          if (inputDefinition.default.isEmpty && inputDefinition.required.value) {
            if (!inputsValues.contains(inputName)) {
              List(CompilationError(s"Input [${inputName.value}] required but not defined", inputName.pos, Some(inputName.value)))
            } else if (!inputsValues(inputName).isInstanceOf[PropertyValue[_]]) {
              List(CompilationError(s"Input [${inputName.value}] do not support function in input value", inputName.pos, Some(inputName.value)))
            } else List.empty
          } else List.empty
      }.toList
      val definitionWithMergedTopology = definitionWithTopology.copy(topologyTemplate = Some(mergedTopology))
      val definitionsWithMergedTopology = topologyCompilationResult.csar.definitions + (definitionWithTopologyEntry.get._1 -> definitionWithMergedTopology)
      val semanticErrors = SemanticAnalyzer.analyze(topologyCompilationResult.csar.copy(definitions = definitionsWithMergedTopology), topologyPath, topologyCompilationResult.dependencies.values.toList)
      val beforeDeploymentSemanticErrors = SemanticAnalyzer.analyzeTopologyBeforeDeployment(mergedTopology, topologyCompilationResult.dependencies.values.toList)
      if (inputErrors.isEmpty && semanticErrors.isEmpty && beforeDeploymentSemanticErrors.isEmpty) {
        val mergedDefinitions = topologyCompilationResult.csar.definitions + (definitionWithTopologyEntry.get._1 -> definitionWithTopologyEntry.get._2.copy(topologyTemplate = Some(mergedTopology)))
        val mergedCsar = Csar(topologyPath.toAbsolutePath.toString, mergedDefinitions)
        val recipePath = outputPath.resolve(CompilerConstant.ASSEMBLY_RECIPE_FOLDER)
        Files.createDirectories(outputPath.resolve(CompilerConstant.ASSEMBLY_PROVIDER_FOLDER))
        Files.createDirectories(outputPath.resolve(CompilerConstant.ASSEMBLY_PLUGIN_FOLDER))
        topologyCompilationResult.dependencies.foreach {
          // Do not assembly normative types with the topology as it's provided by the framework
          case (csarId, csar) =>
            val assemblyDependency = assemblyDependenciesResolver(csarId).get._3
            val isProvider = CompilerUtil.isProviderTypes(csar.csarName)
            val isPlugin = CompilerUtil.isPluginTypes(csar.csarName)
            if (isProvider) {
              FileUtil.copy(assemblyDependency, outputPath.resolve(CompilerConstant.ASSEMBLY_PROVIDER_FOLDER).resolve(CompilerUtil.pluginNameFromCsarName(csar.csarName)), StandardCopyOption.REPLACE_EXISTING)
            } else if (isPlugin) {
              FileUtil.copy(assemblyDependency, outputPath.resolve(CompilerConstant.ASSEMBLY_PLUGIN_FOLDER).resolve(CompilerUtil.pluginNameFromCsarName(csar.csarName)), StandardCopyOption.REPLACE_EXISTING)
            } else {
              FileUtil.copy(assemblyDependency, recipePath, StandardCopyOption.REPLACE_EXISTING)
            }
        }
        if (inputs.isDefined) Files.copy(inputs.get, outputPath.resolve("inputs.yaml"), StandardCopyOption.REPLACE_EXISTING)
        CodeGenerator.generate(mergedCsar, topologyCompilationResult.dependencies.values.toList, getDependenciesBinaries(repositoryPath, topologyCompilationResult), topologyPath, recipePath)
        topologyCompilationResult.copy(csar = mergedCsar)
      } else {
        val errorsWithInput = beforeDeploymentSemanticErrors ++ inputErrors ++ topologyCompilationResult.errors.getOrElse(definitionWithTopologyEntry.get._1, List.empty)
        val errorsWithInputAndSemantic = semanticErrors + (definitionWithTopologyEntry.get._1 -> (semanticErrors.getOrElse(definitionWithTopologyEntry.get._1, List.empty) ++ errorsWithInput))
        topologyCompilationResult.copy(errors = errorsWithInputAndSemantic)
      }
    } else {
      logger.info(s"Topology ${topologyPath.toString} has compilation errors")
      val inputsErrors = inputsParseResult.map {
        case error: SyntaxAnalyzer.NoSuccess => Map(inputs.get.toString -> List(CompilationError(error.msg, error.next.pos, None)))
        case _ => Map.empty
      }.getOrElse(Map.empty)
      topologyCompilationResult.copy(errors = topologyCompilationResult.errors ++ inputsErrors)
    }
  }
}
