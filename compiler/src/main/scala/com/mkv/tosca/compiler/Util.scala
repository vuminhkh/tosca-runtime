package com.mkv.tosca.compiler

import java.nio.file.{Files, Path}

import com.google.common.base.CaseFormat
import com.mkv.exception.NotSupportedGenerationException
import com.mkv.tosca.compiler.runtime.Method
import com.mkv.util.FileUtil

object Util {

  def methodHasInput(method: Method) = {
    method.functionInputs.nonEmpty || method.scalarInputs.nonEmpty
  }

  def isTypeDefined(typeName: String): Boolean = {
    isTypeDefined(typeName, Thread.currentThread().getContextClassLoader)
  }

  def isTypeDefined(typeName: String, classLoader: ClassLoader): Boolean = {
    try {
      Class.forName(typeName, false, classLoader)
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

  def findImplementations(typesToScan: List[String], classLoader: ClassLoader, implementedType: Class[_]) = {
    typesToScan.filter(className => implementedType.isAssignableFrom(classLoader.loadClass(className))).map(classLoader.loadClass)
  }

  /**
   * Try to create file system if the path is a zip file
   * @param paths the path list to create file systems
   * @return map of path --> file system
   */
  def createZipFileSystems(paths: List[Path]) = {
    paths.map { path =>
      if (!Files.isDirectory(path)) {
        val zipPath = FileUtil.createZipFileSystem(path)
        (zipPath, Some(zipPath.getFileSystem))
      } else {
        (path, None)
      }
    }
  }
}
