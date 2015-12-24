package com.toscaruntime.compiler

import java.nio.file.{Files, Path}

import com.google.common.base.CaseFormat
import com.toscaruntime.exception.NotSupportedGenerationException
import com.toscaruntime.util.FileUtil

object CompilerUtil {

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

  def toCamelCase(text: String) = {
    CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, text)
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