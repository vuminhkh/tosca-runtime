package com.mkv.tosca.compiler

import java.nio.file.{Files, Path, StandardOpenOption}

import com.google.common.base.{CaseFormat, Charsets}
import com.mkv.exception.{NotSupportedGenerationException, RecoverableException}
import com.mkv.tosca.compiler.runtime.Method
import org.clapper.classutil.ClassFinder

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

  def getGeneratedClassRelativePath(outputPath: Path, typeName: String) = {
    val pathElements = (typeName.replaceAll("\\.", "/") + ".java").split("/")
    var outputFile = outputPath
    pathElements.foreach(childPath => outputFile = outputFile.resolve(childPath))
    outputFile
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
    Files.write(outputFile, text.getBytes(Charsets.UTF_8), StandardOpenOption.TRUNCATE_EXISTING)
  }

  def scanDeploymentImplementation(): String = {
    val deploymentImplementations = ClassFinder().getClasses().filter(_.superClassName == classOf[com.mkv.tosca.sdk.Deployment].getName).toSeq
    if (deploymentImplementations.size > 1) {
      throw new RecoverableException("More than one deployment provider is found on the class path " + deploymentImplementations)
    } else if (deploymentImplementations.isEmpty) {
      throw new RecoverableException("No deployment provider is found on the class path")
    } else {
      return deploymentImplementations.head.name
    }
  }
}
