package com.toscaruntime.compiler

import java.nio.file.{Files, Path, StandardCopyOption}

import com.google.common.io.Closeables
import com.toscaruntime.compiler.runtime.Method
import com.toscaruntime.compiler.tosca._
import com.toscaruntime.constant.CompilerConstant
import com.toscaruntime.exception.{InvalidTopologyException, NonRecoverableException, NotSupportedGenerationException}
import com.toscaruntime.util.{ClassLoaderUtil, FileUtil}
import com.typesafe.scalalogging.LazyLogging

/**
  * Generate code from compiled csar's topology
  *
  * @author Minh Khang VU
  */
object CodeGenerator extends LazyLogging {

  def getScalarInputs(operation: Operation) = {
    operation.inputs.map(_.filter(_._2.isInstanceOf[ParsedValue[String]]).map {
      case (inputName: ParsedValue[String], inputValue: ParsedValue[String]) => (inputName.value, inputValue.value)
    }).getOrElse(Map.empty)
  }

  def getFunctionInputs(operation: Operation) = {
    operation.inputs.map(_.filter(_._2.isInstanceOf[tosca.Function]).map {
      case (inputName: ParsedValue[String], inputFunction: tosca.Function) => (inputName.value, runtime.Function(inputFunction.function.value, inputFunction.entity.value, inputFunction.path.value))
    }).getOrElse(Map.empty)
  }

  def parseMethod(interfaceName: String, operationName: String, operation: Operation) = {
    val methodName = CompilerUtil.getGeneratedMethodName(interfaceName, operationName)
    Method(methodName, getScalarInputs(operation), getFunctionInputs(operation), operation.implementation.map(_.value))
  }

  def parseInterface(interfaceName: String, interface: Interface) = {
    interface.operations.map {
      case (parsedName: ParsedValue[String], operation: Operation) => parseMethod(interfaceName, parsedName.value, operation)
    }
  }

  def parseRuntimeType(runtimeType: RuntimeType) = {
    val packageNameAndClassName = CompilerUtil.splitClassNameAndPackageName(runtimeType.name.value)
    val methods = runtimeType.interfaces.map(_.flatMap {
      case (name: ParsedValue[String], interface: Interface) => parseInterface(name.value, interface)
    }).getOrElse(Seq.empty).toSeq
    (packageNameAndClassName._2, packageNameAndClassName._1, runtimeType.isAbstract.value, runtimeType.derivedFrom.map(_.value), methods)
  }

  def generateNodeType(csar: Csar, nodeType: NodeType, outputDir: Path) = {
    if (ClassLoaderUtil.isTypeDefined(nodeType.name.value)) {
      logger.info(s"Ignoring node type ${nodeType.name.value} as it's part of SDK")
    } else {
      logger.info(s"Generating node type class ${nodeType.name.value}")
      val parsedType = parseRuntimeType(nodeType)
      val generatedText = html.GeneratedNodeType.render(runtime.NodeType(parsedType._1, parsedType._2, parsedType._3, parsedType._4, parsedType._5, csar.csarName)).body
      val outputFile = CompilerUtil.getGeneratedClassRelativePath(outputDir, nodeType.name.value)
      val generatedPath = FileUtil.writeTextFile(generatedText, outputFile)
      logger.info(s"Generated node type class ${nodeType.name.value} to $generatedPath")
    }
  }

  def generateRelationshipType(csar: Csar, relationshipType: RelationshipType, outputDir: Path) = {
    if (ClassLoaderUtil.isTypeDefined(relationshipType.name.value)) {
      logger.info(s"Ignoring relationship type ${relationshipType.name.value} as it's part of SDK")
    } else {
      logger.info(s"Generating relationship type class ${relationshipType.name.value}")
      val parsedType = parseRuntimeType(relationshipType)
      val generatedText = html.GeneratedRelationshipType.render(runtime.RelationshipType(parsedType._1, parsedType._2, parsedType._3, parsedType._4, parsedType._5, csar.csarName)).body
      val outputFile = CompilerUtil.getGeneratedClassRelativePath(outputDir, relationshipType.name.value)
      val generatedPath = FileUtil.writeTextFile(generatedText, outputFile)
      logger.info(s"Generated relationship type class ${relationshipType.name.value} to $generatedPath")
    }
  }

  def generateTypesForDefinition(csar: Csar, definition: Definition, outputDir: Path) = {
    definition.nodeTypes.foreach {
      _.values.foreach {
        generateNodeType(csar, _, outputDir)
      }
    }
    definition.relationshipTypes.foreach {
      _.values.foreach {
        generateRelationshipType(csar, _, outputDir)
      }
    }
  }

  def generateTypesForCsar(csar: Csar, outputDir: Path) = {
    csar.definitions.foreach {
      case (path, definition) => generateTypesForDefinition(csar, definition, outputDir)
    }
  }

  def parseTopology(topology: TopologyTemplate, topologyCsarName: String, csarPath: Seq[Csar], outputDir: Path) = {
    if (topology.nodeTemplates.isEmpty) {
      throw new InvalidTopologyException("Topology do not contain any node")
    }
    val topologyNodes = topology.nodeTemplates.get.values.map {
      nodeTemplate =>
        val nodeName = nodeTemplate.name.value
        val nodeTypeName = nodeTemplate.typeName.get.value
        val nodeType = TypeLoader.loadPolymorphismResolvedNodeType(nodeTypeName, csarPath).get
        val defaultProperties = nodeType.properties.map {
          _.filter(_._2.default.isDefined).map {
            case (propertyName: ParsedValue[String], propertyDefinition: PropertyDefinition) => (propertyName.value, propertyDefinition.default.get.value)
          }
        }.getOrElse(Map.empty)
        val scalarProperties = nodeTemplate.properties.map {
          _.filter(_._2.isInstanceOf[ParsedValue[String]]).map {
            case (propertyName: ParsedValue[String], propertyValue: ParsedValue[String]) => (propertyName.value, propertyValue.value)
          }
        }.getOrElse(Map.empty)
        val inputProperties = nodeTemplate.properties.map {
          _.filter(_._2.isInstanceOf[Input]).map {
            case (propertyName: ParsedValue[String], propertyValue: Input) => (propertyName.value, runtime.Input(propertyValue.name.value))
          }
        }.getOrElse(Map.empty)
        (nodeName, new runtime.Node(name = nodeName, typeName = nodeTypeName, scalarProperties = defaultProperties ++ scalarProperties, inputProperties = inputProperties))
    }.toMap

    val topologyRelationships = topology.nodeTemplates.get.values.flatMap {
      nodeTemplate => nodeTemplate.requirements.map {
        _.map {
          requirement =>
            val sourceNodeName = nodeTemplate.name.value
            val sourceNodeTypeName = nodeTemplate.typeName.get.value
            val sourceNodeType = TypeLoader.loadPolymorphismResolvedNodeType(sourceNodeTypeName, csarPath).get
            val sourceNode = topologyNodes(sourceNodeName)
            val targetNodeName = requirement.targetNode.get.value
            val targetNode = topologyNodes(targetNodeName)

            val relationshipTypeNameOpt = sourceNodeType.requirements.get(requirement.name).relationshipType
            val relationshipType = relationshipTypeNameOpt.map {
              relationshipTypeName => TypeLoader.loadRelationshipType(relationshipTypeName.value, csarPath)
            }.getOrElse {
              TypeLoader.loadRelationshipType(sourceNodeTypeName, requirement.targetCapability.map(_.value).getOrElse(sourceNodeType.requirements.get(requirement.name).capabilityType.get.value), csarPath)
            }
            if (relationshipType.isEmpty) {
              throw new NonRecoverableException(s"Missing relationship type for requirement ${requirement.name.value} of node $sourceNodeTypeName")
            } else {
              if (TypeLoader.isRelationshipInstanceOf(relationshipType.get.name.value, "tosca.relationships.HostedOn", csarPath)) {
                sourceNode.parent = Some(targetNode)
                targetNode.children = targetNode.children :+ sourceNode
              } else {
                sourceNode.dependencies = sourceNode.dependencies :+ targetNode
              }
            }
            new runtime.Relationship(sourceNode, targetNode, relationshipType.get.name.value)
        }
      }.getOrElse(Seq.empty)
    }
    val topologyOutputs = topology.outputs.getOrElse(Map.empty).map {
      case (outputName: ParsedValue[String], output: Output) =>
        output.value.get match {
          case value: ParsedValue[String] =>
            (outputName.value, value.value)
          case outputFunction: Function =>
            (outputName.value, runtime.Function(outputFunction.function.value, outputFunction.entity.value, outputFunction.path.value))
          case compositeOutputFunction: CompositeFunction =>
            (outputName.value, runtime.CompositeFunction(compositeOutputFunction.function.value, compositeOutputFunction.members.map {
              case value: ParsedValue[String] => value.value
              case function: Function => runtime.Function(function.function.value, function.entity.value, function.path.value)
            }))
        }
    }

    val topologyRoots = topologyNodes.values.filter(node => node.parent.isEmpty)
    runtime.Deployment(
      topologyNodes.values.toSeq,
      topologyRelationships.toSeq,
      topologyRoots.toSeq,
      topologyOutputs,
      topologyCsarName)
  }

  def generate(csar: Csar, csarPath: List[Csar], originalArchivePath: Path, outputPath: Path) = {
    var recipeOutputPath = outputPath
    val createZip = !Files.isDirectory(outputPath)
    if (createZip) {
      recipeOutputPath = FileUtil.createZipFileSystem(outputPath)
    }
    try {
      // Copy original archive to the compiled output
      FileUtil.copy(originalArchivePath, recipeOutputPath.resolve(CompilerConstant.ARCHIVE_FOLDER).resolve(csar.csarName), StandardCopyOption.REPLACE_EXISTING)
      // Generate Java classes for types
      generateTypesForCsar(csar, recipeOutputPath.resolve(CompilerConstant.TYPES_FOLDER))
      val definitionsWithTopology = csar.definitions.filter(_._2.topologyTemplate.isDefined)
      if (definitionsWithTopology.size > 1) {
        throw new NotSupportedGenerationException("More than one topology is found in the CSAR at " + definitionsWithTopology.keys + ", this is currently not supported")
      } else if (definitionsWithTopology.nonEmpty) {
        val deployment = parseTopology(definitionsWithTopology.values.head.topologyTemplate.get, csar.csarName, csarPath :+ csar, recipeOutputPath)
        val generatedTopologyText = html.GeneratedTopology.render(deployment).body
        // Generate Deployment for the topology
        FileUtil.writeTextFile(generatedTopologyText, recipeOutputPath.resolve(CompilerConstant.DEPLOYMENT_FILE))
      }
    } finally {
      if (createZip) {
        Closeables.close(recipeOutputPath.getFileSystem, true)
      }
    }
  }
}
