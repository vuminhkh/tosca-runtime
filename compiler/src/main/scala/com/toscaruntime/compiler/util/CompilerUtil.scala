package com.toscaruntime.compiler.util

import java.nio.file.Path

import com.google.common.base.CaseFormat
import com.toscaruntime.compiler.tosca._

import scala.util.parsing.json._

object CompilerUtil {

  def escapeJavaIdentifier(name: String) = {
    name.map { c =>
      if (Character.isJavaIdentifierPart(c)) c else '_'
    }
  }

  def splitClassNameAndPackageName(typeName: String) = {
    val indexClassName = typeName.lastIndexOf('.')
    if (indexClassName < 0) {
      ("", typeName)
    } else {
      val className = typeName.substring(indexClassName + 1)
      val packageName = typeName.substring(0, indexClassName)
      (packageName, className)
    }
  }

  def getGeneratedClassRelativePath(outputPath: Path, typeName: String) = {
    val pathElements = (typeName.replaceAll("\\.", "/") + ".java").split("/")
    var outputFile = outputPath
    pathElements.foreach(childPath => outputFile = outputFile.resolve(childPath))
    outputFile
  }

  private def convertObject(item: Any): Any = {
    item match {
      case map: ComplexValue => convertMap(map.value)
      case list: ListValue => convertList(list.value)
      case value: ScalarValue => value.value
      case other: Any => throw new UnsupportedOperationException(s"Type not supported ${other.getClass}")
    }
  }

  private def convertMap(map: Map[ParsedValue[String], Any]) = {
    JSONObject(map.map {
      case (key, value) => (key.value, convertObject(value))
    })
  }

  private def convertList(list: Iterable[Any]) = {
    JSONArray(list.map {
      case el => convertObject(el)
    }.toList)
  }

  def serializePropertyValueToJson(obj: PropertyValue[_]) = {
    convertObject(obj).toString
  }

  def normalizeCSARName(csarName: String) = {
    csarName.replaceAll("[\\s:]", "_")
  }
}
