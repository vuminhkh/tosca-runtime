package com.mkv.tosca.compiler.runtime

import com.google.common.base.CaseFormat
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

  def toCamelCase(text: String) = {
    CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, text)
  }
}
