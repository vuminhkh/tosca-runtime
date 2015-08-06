package com.mkv.tosca.compiler

import java.nio.file.{Files, Path, Paths}

import com.google.common.base.Charsets
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
    val methodName = interfaceName match {
      case "Standard" | "tosca.interfaces.node.lifecycle.Standard" => operationName
      case "Configure" | "tosca.interfaces.relationship.Configure" => operationName
      case _ => operationName.replaceAll("\\.", "_") + "_" + operationName
    }
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
      val outputFile = outputDir.resolve("types").resolve(Paths.get(Util.getGeneratedClassRelativePath(nodeType.name.value)))
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
      val outputFile = outputDir.resolve("types").resolve(Paths.get(Util.getGeneratedClassRelativePath(relationshipType.name.value)))
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
}
