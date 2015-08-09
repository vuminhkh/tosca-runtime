package com.mkv.tosca.compiler

import java.nio.file.{Files, Path, Paths}

import _root_.tosca.relationships.HostedOn
import com.google.common.base.Charsets
import com.mkv.exception.{NonRecoverableException, InvalidTopologyException, NotSupportedGenerationException}
import com.mkv.tosca.compiler.runtime.{Method, Util}
import com.mkv.tosca.compiler.tosca._
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
    operation.inputs.map(_.filter(_._2.isInstanceOf[Function]).map {
      case (inputName: ParsedValue[String], inputFunction: Function) => (inputName.value, runtime.Function(inputFunction.function.value, inputFunction.entity.value, inputFunction.path.value))
    }).getOrElse(Map.empty)
  }

  def parseMethod(interfaceName: String, operationName: String, operation: Operation) = {
    val methodName = Util.getGeneratedMethodName(interfaceName, operationName)
    Method(methodName, getScalarInputs(operation), getFunctionInputs(operation), operation.implementation.map(_.value))
  }

  def parseInterface(interfaceName: String, interface: Interface) = {
    interface.operations.map {
      case (parsedName: ParsedValue[String], operation: Operation) => parseMethod(interfaceName, parsedName.value, operation)
    }
  }

  def parseRuntimeType(runtimeType: RuntimeType) = {
    val packageNameAndClassName = Util.splitClassNameAndPackageName(runtimeType.name.value)
    val methods = runtimeType.interfaces.map(_.flatMap {
      case (name: ParsedValue[String], interface: Interface) => parseInterface(name.value, interface)
    }).getOrElse(Seq.empty).toSeq
    (packageNameAndClassName._2, packageNameAndClassName._1, runtimeType.isAbstract.value, runtimeType.derivedFrom.map(_.value), methods)
  }

  def generateNodeType(nodeType: NodeType, outputDir: Path) = {
    if (Util.isTypeDefined(nodeType.name.value)) {
      logger.info(s"Ignoring node type ${nodeType.name.value} as it's part of SDK")
    } else {
      logger.info(s"Generating node type class ${nodeType.name.value}")
      val parsedType = parseRuntimeType(nodeType)
      val generatedText = html.GeneratedNodeType.render(runtime.NodeType(parsedType._1, parsedType._2, parsedType._3, parsedType._4, parsedType._5)).body
      val outputFile = outputDir.resolve(Paths.get(Util.getGeneratedClassRelativePath(nodeType.name.value)))
      Files.createDirectories(outputFile.getParent)
      Files.write(outputFile, generatedText.getBytes(Charsets.UTF_8))
    }
  }

  def generateRelationshipType(relationshipType: RelationshipType, outputDir: Path) = {
    if (Util.isTypeDefined(relationshipType.name.value)) {
      logger.info(s"Ignoring relationship type ${relationshipType.name.value} as it's part of SDK")
    } else {
      logger.info(s"Generating relationship type class ${relationshipType.name.value}")
      val parsedType = parseRuntimeType(relationshipType)
      val generatedText = html.GeneratedRelationshipType.render(runtime.RelationshipType(parsedType._1, parsedType._2, parsedType._3, parsedType._4, parsedType._5)).body
      val outputFile = outputDir.resolve(Paths.get(Util.getGeneratedClassRelativePath(relationshipType.name.value)))
      Files.createDirectories(outputFile.getParent)
      Files.write(outputFile, generatedText.getBytes(Charsets.UTF_8))
    }
  }

  def generateTypesForDefinition(definition: Definition, outputDir: Path) = {
    definition.nodeTypes.foreach {
      _.values.foreach {
        generateNodeType(_, outputDir)
      }
    }
    definition.relationshipTypes.foreach {
      _.values.foreach {
        generateRelationshipType(_, outputDir)
      }
    }
  }

  def generateTypesForCsar(csar: Csar, outputDir: Path) = {
    csar.definitions.foreach {
      case (path, definition) => generateTypesForDefinition(definition, outputDir)
    }
  }

  def parseTopology(topology: TopologyTemplate, csarPath: Seq[Csar], outputDir: Path) = {
    if (topology.nodeTemplates.isEmpty) {
      throw new InvalidTopologyException("Topology do not contain any node")
    }
    val topologyNodes = topology.nodeTemplates.get.values.map {
      nodeTemplate =>
        val nodeName = nodeTemplate.name.value
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
        (nodeName, new runtime.Node(name = nodeName, typeName = nodeTemplate.typeName.get.value, scalarProperties = scalarProperties, inputProperties = inputProperties))
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
              } else if (TypeLoader.isRelationshipInstanceOf(relationshipType.get.name.value, "tosca.relationships.DependsOn", csarPath)) {
                sourceNode.dependencies = sourceNode.dependencies :+ targetNode
              }
            }
            new runtime.Relationship(sourceNode, targetNode, relationshipType.get.name.value)
        }
      }.getOrElse(Seq.empty)
    }
    runtime.Deployment(topologyNodes.values.toSeq, topologyRelationships.toSeq)
  }

  def generate(csar: Csar, csarPath: List[Csar], outputDir: Path) = {
    generateTypesForCsar(csar, outputDir)
    val definitionsWithTopology = csar.definitions.filter(_._2.topologyTemplate.isDefined)
    if (definitionsWithTopology.size > 1) {
      throw new NotSupportedGenerationException("More than one topology is found in the CSAR at " + definitionsWithTopology.keys + ", this is currently not supported")
    } else if (definitionsWithTopology.nonEmpty) {
      val deployment = parseTopology(definitionsWithTopology.values.head.topologyTemplate.get, csarPath :+ csar, outputDir)
      val generatedTopologyText = html.GeneratedTopology.render(deployment).body
      Util.writeCode(generatedTopologyText, outputDir.resolve("Deployment.java"))
    }
  }
}
