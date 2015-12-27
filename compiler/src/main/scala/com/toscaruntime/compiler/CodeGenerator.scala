package com.toscaruntime.compiler

import java.nio.file.{Files, Path, StandardCopyOption}

import com.google.common.io.Closeables
import com.toscaruntime.compiler.runtime.{Method, Value}
import com.toscaruntime.compiler.tosca._
import com.toscaruntime.constant.CompilerConstant
import com.toscaruntime.exception.{InvalidTopologyException, NonRecoverableException, NotSupportedGenerationException}
import com.toscaruntime.util.{ClassLoaderUtil, CodeGeneratorUtil, FileUtil}
import com.typesafe.scalalogging.LazyLogging

/**
  * Generate code from compiled csar's topology
  *
  * @author Minh Khang VU
  */
object CodeGenerator extends LazyLogging {

  def getInputs(operation: Operation) = {
    operation.inputs.map(_.filter(!_._2.isInstanceOf[PropertyDefinition]).map {
      case (inputName: ParsedValue[String], inputValue: FieldValue) => (inputName.value, parseValue(inputValue))
    }).getOrElse(Map.empty)
  }

  def parseMethod(interfaceName: String, operationName: String, operation: Operation) = {
    val methodName = CodeGeneratorUtil.getGeneratedMethodName(interfaceName, operationName)
    Method(methodName, getInputs(operation), operation.implementation.map(_.value))
  }

  def parseInterface(interfaceName: String, interface: Interface) = {
    interface.operations.map {
      case (parsedName: ParsedValue[String], operation: Operation) => parseMethod(interfaceName, parsedName.value, operation)
    }
  }

  case class RuntimeTypeParseResult(packageName: String,
                                    className: String,
                                    isAbstract: Boolean,
                                    derivedFrom: Option[String],
                                    methods: Seq[runtime.Method],
                                    properties: Map[String, Value],
                                    attributes: Map[String, Value])

  def parseRuntimeType(runtimeType: RuntimeType) = {
    val packageNameAndClassName = CompilerUtil.splitClassNameAndPackageName(runtimeType.name.value)
    val methods = runtimeType.interfaces.map(_.flatMap {
      case (name: ParsedValue[String], interface: Interface) => parseInterface(name.value, interface)
    }).getOrElse(Seq.empty).toSeq
    val attributes = runtimeType.attributes.map(_.filter(!_._2.isInstanceOf[AttributeDefinition]).map {
      case (name: ParsedValue[String], value: FieldValue) => (name.value, parseValue(value))
    }).getOrElse(Map.empty)
    val properties = runtimeType.properties.map(_.filter(!_._2.isInstanceOf[PropertyDefinition]).map {
      case (name: ParsedValue[String], value: FieldValue) => (name.value, parseValue(value))
    }).getOrElse(Map.empty)
    RuntimeTypeParseResult(packageNameAndClassName._1, packageNameAndClassName._2, runtimeType.isAbstract.value, runtimeType.derivedFrom.map(_.value), methods, properties, attributes)
  }

  def generateNodeType(csar: Csar, nodeType: NodeType, outputDir: Path) = {
    if (ClassLoaderUtil.isTypeDefined(nodeType.name.value)) {
      logger.info(s"Ignoring node type ${nodeType.name.value} as it's part of SDK")
    } else {
      logger.info(s"Generating node type class ${nodeType.name.value}")
      val parsedType = parseRuntimeType(nodeType)
      val generatedText = html.GeneratedNodeType.render(runtime.NodeType(parsedType.className, parsedType.packageName, parsedType.isAbstract, parsedType.derivedFrom, parsedType.methods, csar.csarName, parsedType.properties, parsedType.attributes)).body
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
      val generatedText = html.GeneratedRelationshipType.render(runtime.RelationshipType(parsedType.className, parsedType.packageName, parsedType.isAbstract, parsedType.derivedFrom, parsedType.methods, csar.csarName, parsedType.properties, parsedType.attributes)).body
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
        val properties = nodeTemplate.properties.map {
          _.map {
            case (propertyName: ParsedValue[String], propertyValue: FieldValue) => (propertyName.value, parseValue(propertyValue))
          }
        }.getOrElse(Map.empty)
        val defaultProperties = nodeType.properties.map {
          _.filter {
            case (propertyName, propertyDefinition) => propertyDefinition.isInstanceOf[PropertyDefinition] && propertyDefinition.asInstanceOf[PropertyDefinition].default.isDefined
          }.map {
            case (propertyName: ParsedValue[String], propertyDefinition: PropertyDefinition) => (propertyName.value, runtime.ScalarValue(propertyDefinition.default.get.value))
          }
        }.getOrElse(Map.empty)
        (nodeName, new runtime.Node(name = nodeName, typeName = nodeTypeName, defaultProperties ++ properties))
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

            val relationshipTypeNameOpt = requirement.relationshipType.orElse(sourceNodeType.requirements.get(requirement.name).relationshipType)
            val relationshipType = relationshipTypeNameOpt.map {
              relationshipTypeName => TypeLoader.loadRelationshipType(relationshipTypeName.value, csarPath)
            }.getOrElse {
              TypeLoader.loadRelationshipType(sourceNodeTypeName, targetNode.typeName, requirement.targetCapability.map(_.value).getOrElse(sourceNodeType.requirements.get(requirement.name).capabilityType.get.value), csarPath)
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
      case (outputName: ParsedValue[String], output: Output) => (outputName.value, parseValue(output.value.get))
    }

    val topologyRoots = topologyNodes.values.filter(node => node.parent.isEmpty)
    runtime.Deployment(
      topologyNodes.values.toSeq,
      topologyRelationships.toSeq,
      topologyRoots.toSeq,
      topologyOutputs,
      topologyCsarName)
  }

  def parseValue(fieldValue: FieldValue): runtime.Value = {
    fieldValue match {
      case scalarFieldValue: ScalarValue => runtime.ScalarValue(scalarFieldValue.value.value)
      case functionFieldValue: Function => runtime.Function(functionFieldValue.function.value, functionFieldValue.paths.map(_.value))
      case compositeFunctionFieldValue: CompositeFunction =>
        runtime.CompositeFunction(compositeFunctionFieldValue.function.value, compositeFunctionFieldValue.members.map {
          case scalarMemberValue: ScalarValue => runtime.ScalarValue(scalarMemberValue.value.value)
          case functionMemberValue: Function => runtime.Function(functionMemberValue.function.value, functionMemberValue.paths.map(_.value))
        })
    }
  }

  def generate(csar: Csar, csarPath: List[Csar], originalArchivePath: Path, outputPath: Path) = {
    var recipeOutputPath = outputPath
    val createZip = !Files.isDirectory(outputPath)
    if (createZip) {
      recipeOutputPath = FileUtil.createZipFileSystem(outputPath)
    }
    val compilationPath = csarPath :+ csar
    try {
      // Copy original archive to the compiled output
      FileUtil.copy(originalArchivePath, recipeOutputPath.resolve(CompilerConstant.ARCHIVE_FOLDER).resolve(csar.csarName), StandardCopyOption.REPLACE_EXISTING)
      // Generate Java classes for types
      generateTypesForCsar(csar, recipeOutputPath.resolve(CompilerConstant.TYPES_FOLDER))
      val definitionsWithTopology = csar.definitions.filter(_._2.topologyTemplate.isDefined)
      if (definitionsWithTopology.size > 1) {
        throw new NotSupportedGenerationException("More than one topology is found in the CSAR at " + definitionsWithTopology.keys + ", this is currently not supported")
      } else if (definitionsWithTopology.nonEmpty) {
        val deployment = parseTopology(definitionsWithTopology.values.head.topologyTemplate.get, csar.csarName, compilationPath, recipeOutputPath)
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
