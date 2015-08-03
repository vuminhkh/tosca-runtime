package com.mkv.tosca.compiler

import java.nio.file.{Files, Path, Paths}

import com.google.common.base.Charsets
import com.mkv.exception.NotSupportedGenerationException
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
      case (inputName: ParsedValue[String], inputFunction: Function) => (inputName.value, generator.Function(inputFunction.function.value, inputFunction.entity.value, inputFunction.path.value))
    }).getOrElse(Map.empty)
  }

  def parseMethod(interfaceName: String, operationName: String, operation: Operation) = {
    val methodName = interfaceName match {
      case "Standard" | "tosca.interfaces.node.lifecycle.Standard" => operationName
      case _ => operationName.replaceAll("\\.", "_") + "_" + operationName
    }
    generator.Method(methodName, getScalarInputs(operation), getFunctionInputs(operation), operation.implementation.map(_.value))
  }

  def parseInterface(interfaceName: String, interface: Interface) = {
    interface.operations.map {
      case (parsedName: ParsedValue[String], operation: Operation) => parseMethod(interfaceName, parsedName.value, operation)
    }
  }

  def generateNodeType(nodeType: NodeType, csarPath: List[Csar], outputDir: Path) = {
    val indexClassName = nodeType.name.value.lastIndexOf('.')
    if (indexClassName < 0) {
      throw new NotSupportedGenerationException("Package is mandatory, please prefix " + nodeType.name + " with a package")
    }
    try {
      Class.forName(nodeType.name.value, false, getClass.getClassLoader)
      logger.info(s"Ignoring class ${nodeType.name.value} as it's part of SDK")
    } catch {
      case e: ClassNotFoundException =>
        logger.info(s"Generating class ${nodeType.name.value}")
        val className = nodeType.name.value.substring(indexClassName + 1)
        val packageName = nodeType.name.value.substring(0, indexClassName)
        val methods = nodeType.interfaces.map(_.flatMap {
          case (name: ParsedValue[String], interface: Interface) => parseInterface(name.value, interface)
        }).getOrElse(Seq.empty).toSeq
        val parsedType = generator.NodeType(className, packageName, nodeType.isAbstract.value, nodeType.derivedFrom.map(_.value).getOrElse("tosca.nodes.Root"), methods)
        val generatedText = html.GeneratedNodeType.render(parsedType, csarPath).body
        val outputFile = outputDir.resolve(Paths.get(nodeType.name.value.replaceAll("\\.", "/") + ".java"))
        Files.createDirectories(outputFile.getParent)
        Files.write(outputFile, generatedText.getBytes(Charsets.UTF_8))
    }
  }

  def generateTypesForDefinition(definition: Definition, csarPath: List[Csar], outputDir: Path) = {
    definition.nodeTypes.foreach {
      _.values.foreach {
        generateNodeType(_, csarPath, outputDir)
      }
    }
  }

  def generateTypesForCsar(csar: Csar, csarPath: List[Csar], outputDir: Path) = {
    val csarPathWithSelf = csar :: csarPath
    csar.definitions.foreach {
      case (path, definition) => generateTypesForDefinition(definition, csarPathWithSelf, outputDir)
    }
  }
}
