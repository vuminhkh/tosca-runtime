package com.toscaruntime.compiler

import java.nio.file.{Path, Paths, StandardCopyOption}

import _root_.tosca.nodes.Root
import _root_.tosca.relationships.{AttachTo, HostedOn}
import com.toscaruntime.common.nodes.Compute
import com.toscaruntime.compiler.runtime.Artifact
import com.toscaruntime.compiler.tosca._
import com.toscaruntime.compiler.util.CompilerUtil
import com.toscaruntime.constant.CompilerConstant
import com.toscaruntime.exception.compilation.{InvalidTopologyException, NotSupportedGenerationException}
import com.toscaruntime.util._
import com.typesafe.scalalogging.LazyLogging

/**
  * Generate code from compiled csar's topology
  *
  * @author Minh Khang VU
  */
object CodeGenerator extends LazyLogging {

  def getInputs(operation: Operation) = {
    operation.inputs.map(_.filter(_._2.isInstanceOf[EvaluableFieldValue]).map {
      case (inputName: ParsedValue[String], inputValue: EvaluableFieldValue) => (inputName.value, parseValue(inputValue))
    }).getOrElse(Map.empty)
  }

  def parseMethod(interfaceName: String, operationName: String, operation: Operation, csarPath: List[Csar]) = {
    val artifact = operation.implementation.map { impl =>
      // Semantic analyzer must have validated the artifact type exists
      val artifactType = impl.typeName.getOrElse(TypeLoader.loadArtifactTypeByFileExtension(FileUtil.getFileExtension(impl.ref.get.value), csarPath).get.name)
      Artifact(impl.ref.get.value, artifactType.value)
    }
    runtime.Method(interfaceName, operationName, getInputs(operation), artifact)
  }

  def parseInterface(interfaceName: String, interface: Interface, csarPath: List[Csar]) = {
    interface.operations.map {
      case (parsedName: ParsedValue[String], operation: Operation) => parseMethod(interfaceName, parsedName.value, operation, csarPath)
    }
  }

  case class RuntimeTypeParseResult(packageName: String,
                                    className: String,
                                    isAbstract: Boolean,
                                    derivedFrom: Option[String],
                                    methods: Seq[runtime.Method],
                                    properties: Map[String, runtime.Value],
                                    attributes: Map[String, runtime.Value],
                                    deploymentArtifacts: Map[String, String])

  def parseRuntimeType(runtimeType: RuntimeType, csarPath: List[Csar]) = {
    val packageNameAndClassName = CompilerUtil.splitClassNameAndPackageName(runtimeType.name.value)
    val methods = runtimeType.interfaces.map(_.flatMap {
      case (name: ParsedValue[String], interface: Interface) => parseInterface(name.value, interface, csarPath)
    }).getOrElse(Seq.empty).toSeq
    val attributes = runtimeType.attributes.map(_.filter(_._2.isInstanceOf[EvaluableFieldValue]).map {
      case (name: ParsedValue[String], value: EvaluableFieldValue) => (name.value, parseValue(value))
    }).getOrElse(Map.empty)
    val properties = runtimeType.properties.map(_.filter(_._2.isInstanceOf[EvaluableFieldValue]).map {
      case (name: ParsedValue[String], value: EvaluableFieldValue) => (name.value, parseValue(value))
    }).getOrElse(Map.empty)
    val deploymentArtifacts = runtimeType.artifacts.getOrElse(Map.empty).map {
      case (ParsedValue(artifactName), deploymentArtifact: DeploymentArtifact) => (artifactName, deploymentArtifact.ref.get.value)
    }
    RuntimeTypeParseResult(packageNameAndClassName._1, packageNameAndClassName._2, runtimeType.isAbstract.value, runtimeType.derivedFrom.map(_.value), methods, properties, attributes, deploymentArtifacts)
  }

  def generateNodeType(nodeType: NodeType, outputDir: Path, csarName: String, csarPath: List[Csar]) = {
    if (ClassLoaderUtil.isTypeDefined(nodeType.name.value)) {
      logger.debug(s"Ignoring node type ${nodeType.name.value} as it's part of SDK")
      None
    } else {
      logger.debug(s"Generating node type class ${nodeType.name.value}")
      val parsedType = parseRuntimeType(nodeType, csarPath)
      val generatedText = html.GeneratedNodeType.render(runtime.NodeType(parsedType.className, parsedType.packageName, parsedType.isAbstract, parsedType.derivedFrom, parsedType.methods, csarName, parsedType.deploymentArtifacts, parsedType.properties, parsedType.attributes)).body
      val outputFile = CompilerUtil.getGeneratedClassRelativePath(outputDir, nodeType.name.value)
      val generatedPath = FileUtil.writeTextFile(generatedText, outputFile)
      logger.debug(s"Generated node type class ${nodeType.name.value} to $generatedPath")
      Some(generatedPath)
    }
  }

  def generateRelationshipType(relationshipType: RelationshipType, outputDir: Path, csarName: String, csarPath: List[Csar]) = {
    if (ClassLoaderUtil.isTypeDefined(relationshipType.name.value)) {
      logger.debug(s"Ignoring relationship type ${relationshipType.name.value} as it's part of SDK")
      None
    } else {
      logger.debug(s"Generating relationship type class ${relationshipType.name.value}")
      val parsedType = parseRuntimeType(relationshipType, csarPath)
      val generatedText = html.GeneratedRelationshipType.render(runtime.RelationshipType(parsedType.className, parsedType.packageName, parsedType.isAbstract, parsedType.derivedFrom, parsedType.methods, csarName, parsedType.deploymentArtifacts, parsedType.properties, parsedType.attributes)).body
      val outputFile = CompilerUtil.getGeneratedClassRelativePath(outputDir, relationshipType.name.value)
      val generatedPath = FileUtil.writeTextFile(generatedText, outputFile)
      logger.debug(s"Generated relationship type class ${relationshipType.name.value} to $generatedPath")
      Some(generatedPath)
    }
  }

  def generateTypesForDefinition(definition: Definition, outputDir: Path, csarName: String, csarPath: List[Csar]) = {
    val generatedNodeTypes = definition.nodeTypes.map {
      _.values.flatMap {
        generateNodeType(_, outputDir, csarName, csarPath)
      }.toList
    }.getOrElse(List.empty)
    val generatedRelationshipTypes = definition.relationshipTypes.map {
      _.values.flatMap {
        generateRelationshipType(_, outputDir, csarName, csarPath)
      }.toList
    }.getOrElse(List.empty)
    generatedNodeTypes ++ generatedRelationshipTypes
  }

  def generateTypesForCsar(csar: Csar, outputDir: Path, csarName: String, csarPath: List[Csar]) = {
    csar.definitions.flatMap {
      case (path, definition) => generateTypesForDefinition(definition, outputDir, csarName, csarPath)
    }.toList
  }

  def parseProperties(properties: Option[Map[ParsedValue[String], EvaluableFieldValue]], propertiesDefinitions: Option[Map[ParsedValue[String], FieldValue]]) = {
    val propertiesValues = properties.map {
      _.map {
        case (propertyName: ParsedValue[String], propertyValue: FieldValue) => (propertyName.value, parseValue(propertyValue))
      }
    }.getOrElse(Map.empty)
    val defaultProperties = propertiesDefinitions.map {
      _.filter {
        case (propertyName, propertyDefinition) => propertyDefinition.isInstanceOf[PropertyDefinition] && propertyDefinition.asInstanceOf[PropertyDefinition].default.isDefined
      }.map {
        case (propertyName: ParsedValue[String], propertyDefinition: PropertyDefinition) => (propertyName.value, parseValue(propertyDefinition.default.get))
      }
    }.getOrElse(Map.empty)
    defaultProperties ++ propertiesValues
  }

  def parseCapabilityProperties(capabilities: Option[Map[ParsedValue[String], Capability]],
                                capabilitiesDefinitions: Option[Map[ParsedValue[String], CapabilityDefinition]],
                                csarPath: Seq[Csar]) = {
    capabilitiesDefinitions.map {
      _.map {
        case (capabilityName, capabilityDefinition) =>
          val capability = capabilities.flatMap(_.get(capabilityName))
          // We don't perform check that the capability type exist as it must have been done in semantic analyzer
          (capabilityName.value, parseProperties(capability.map(_.properties), TypeLoader.loadCapabilityTypeWithHierarchy(capabilityDefinition.capabilityType.get.value, csarPath).get.properties))
      }.filter(_._2.nonEmpty)
    }.getOrElse(Map.empty)
  }

  def parseTopology(topology: TopologyTemplate, topologyCsarName: String, csarPath: Seq[Csar], outputDir: Path) = {
    if (topology.nodeTemplates.isEmpty) {
      throw new InvalidTopologyException("Topology do not contain any node")
    }
    val topologyNodes = topology.nodeTemplates.get.values.map {
      nodeTemplate =>
        val nodeName = nodeTemplate.name.value
        val nodeTypeName = nodeTemplate.typeName.get.value
        val nodeType = TypeLoader.loadNodeTypeWithHierarchy(nodeTypeName, csarPath).get
        val properties = parseProperties(nodeTemplate.properties, nodeType.properties)
        val capabilitiesProperties = parseCapabilityProperties(nodeTemplate.capabilities, nodeType.capabilities, csarPath)
        (nodeName, new runtime.Node(nodeName, nodeTypeName, properties, capabilitiesProperties))
    }.toMap

    val topologyRelationships = topology.nodeTemplates.get.values.flatMap {
      nodeTemplate => nodeTemplate.requirements.map {
        _.map {
          requirement =>
            val sourceNodeName = nodeTemplate.name.value
            val sourceNodeTypeName = nodeTemplate.typeName.get.value
            val sourceNodeType = TypeLoader.loadNodeTypeWithHierarchy(sourceNodeTypeName, csarPath).get
            val sourceNode = topologyNodes(sourceNodeName)
            val targetNodeName = requirement.targetNode.get.value
            val targetNode = topologyNodes(targetNodeName)
            val relationshipType = TypeLoader.loadRelationshipWithHierarchy(requirement, sourceNodeType.requirements.get(requirement.name), sourceNodeTypeName, targetNode.typeName, csarPath)
            if (relationshipType.isEmpty) {
              throw new InvalidTopologyException(s"Missing relationship type for requirement ${requirement.name.value} of node $sourceNodeTypeName")
            } else if (TypeLoader.isRelationshipInstanceOf(relationshipType.get.name.value, classOf[HostedOn].getName, csarPath)) {
              sourceNode.host = Some(targetNode)
              sourceNode.parent = Some(targetNode)
              targetNode.children = targetNode.children :+ sourceNode
            } else if (TypeLoader.isRelationshipInstanceOf(relationshipType.get.name.value, classOf[AttachTo].getName, csarPath)) {
              sourceNode.parent = Some(targetNode)
              targetNode.children = targetNode.children :+ sourceNode
            }
            val properties = parseProperties(requirement.properties, relationshipType.flatMap(_.properties))
            new runtime.Relationship(sourceNode, targetNode, relationshipType.get.name.value, properties)
        }
      }.getOrElse(Seq.empty)
    }
    val topologyOutputs = topology.outputs.getOrElse(Map.empty).map {
      case (outputName: ParsedValue[String], output: Output) => (outputName.value, parseValue(output.value.get))
    }
    val defaultInputs = parseProperties(None, topology.inputs)
    val topologyRoots = topologyNodes.values.filter(node => node.parent.isEmpty)
    runtime.Deployment(
      topologyNodes.values.toSeq,
      topologyRelationships.toSeq,
      topologyRoots.toSeq,
      topologyOutputs,
      topologyCsarName,
      defaultInputs)
  }

  def parseValue(fieldValue: EvaluableFieldValue): runtime.Value = {
    fieldValue match {
      case scalarFieldValue: ScalarValue => runtime.ScalarValue(scalarFieldValue.value)
      case listFieldValue: ListValue => runtime.ListValue(CompilerUtil.serializePropertyValueToJson(listFieldValue))
      case complexFieldValue: ComplexValue => runtime.ComplexValue(CompilerUtil.serializePropertyValueToJson(complexFieldValue))
      case functionFieldValue: Function => runtime.Function(functionFieldValue.function.value, functionFieldValue.paths.map(_.value))
      case compositeFunctionFieldValue: CompositeFunction =>
        runtime.CompositeFunction(compositeFunctionFieldValue.function.value, compositeFunctionFieldValue.members.map {
          case scalarMemberValue: ScalarValue => runtime.ScalarValue(scalarMemberValue.value)
          case functionMemberValue: Function => runtime.Function(functionMemberValue.function.value, functionMemberValue.paths.map(_.value))
        })
    }
  }

  private def getJarLocation[T](clazz: Class[T]) = {
    Paths.get(clazz.getProtectionDomain.getCodeSource.getLocation.toURI.getPath)
  }

  private lazy val nativeCodePath = List(
    getJarLocation(classOf[Root]),
    getJarLocation(classOf[PropertyUtil]),
    getJarLocation(classOf[Compute])
  )

  def generate(csar: Csar, csarPath: List[Csar], csarBinaryPath: List[Path], originalArchivePath: Path, outputPath: Path) = {
    PathUtil.openAsDirectory(outputPath, recipeOutputPath => {
      val compilationPath = csarPath :+ csar
      // Copy original archive to the compiled output
      ScalaFileUtil.copyRecursive(originalArchivePath, recipeOutputPath.resolve(CompilerConstant.ARCHIVE_FOLDER).resolve(CompilerUtil.normalizeCSARName(csar.csarName)))
      // Generate Java classes for types
      val generatedClasses = generateTypesForCsar(csar, recipeOutputPath.resolve(CompilerConstant.SOURCE_FOLDER), csar.csarName, csarPath)
      // Compile generated Java sources and put classes in target folder
      if (generatedClasses.nonEmpty) JavaCompiler.compileJava(recipeOutputPath.resolve(CompilerConstant.SOURCE_FOLDER), nativeCodePath ++ csarBinaryPath, recipeOutputPath.resolve(CompilerConstant.TARGET_FOLDER))
      val definitionsWithTopology = csar.definitions.filter(_._2.topologyTemplate.isDefined)
      if (definitionsWithTopology.size > 1) {
        throw new NotSupportedGenerationException("More than one topology is found in the CSAR at " + definitionsWithTopology.keys + ", this is currently not supported")
      } else if (definitionsWithTopology.nonEmpty) {
        val deployment = parseTopology(definitionsWithTopology.values.head.topologyTemplate.get, csar.csarName, compilationPath, recipeOutputPath)
        val generatedTopologyText = html.GeneratedTopology.render(deployment).body
        // Generate Deployment for the topology
        val topologySource = recipeOutputPath.resolve(CompilerConstant.SOURCE_FOLDER).resolve(CompilerConstant.DEPLOYMENT_FILE)
        FileUtil.writeTextFile(generatedTopologyText, topologySource)
        JavaCompiler.compileJava(topologySource, nativeCodePath ++ csarBinaryPath, recipeOutputPath.resolve(CompilerConstant.TARGET_FOLDER))
      }
    }, createIfNotExist = true)
  }
}
