package com.mkv.tosca.compiler.runtime

import java.nio.file.{Files, Path}

import com.google.common.base.{CaseFormat, Charsets}
import com.mkv.exception.NotSupportedGenerationException

object Util {

  def methodHasInput(method: Method) = {
    method.functionInputs.nonEmpty || method.scalarInputs.nonEmpty
  }

  def isTypeDefined(typeName: String) = {
    try {
      Class.forName(typeName, false, getClass.getClassLoader)
      true
    } catch {
      case e: ClassNotFoundException => false
    }
  }

  def splitClassNameAndPackageName(typeName: String) = {
    val indexClassName = typeName.lastIndexOf('.')
    if (indexClassName < 0) {
      throw new NotSupportedGenerationException("Package is mandatory, please prefix " + typeName + " with a package")
    }
    val className = typeName.substring(indexClassName + 1)
    val packageName = typeName.substring(0, indexClassName)
    (packageName, className)
  }

  def getGeneratedClassRelativePath(typeName: String) = {
    typeName.replaceAll("\\.", "/") + ".java"
  }

  def getGeneratedMethodName(interfaceName: String, operationName: String) = {
    interfaceName match {
      case "Standard" | "tosca.interfaces.node.lifecycle.Standard" => operationName
      case "Configure" | "tosca.interfaces.relationship.Configure" => operationName
      case _ => interfaceName.replaceAll("\\.", "_") + "_" + operationName
    }
  }

  def toCamelCase(text: String) = {
    CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, text)
  }

  def writeCode(text: String, outputFile: Path) = {
    Files.createDirectories(outputFile.getParent)
    Files.write(outputFile, text.getBytes(Charsets.UTF_8))
  }
}
