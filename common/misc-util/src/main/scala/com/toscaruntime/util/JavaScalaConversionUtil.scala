package com.toscaruntime.util

import java.util

import org.apache.commons.lang.StringUtils

import scala.collection.JavaConverters._

object JavaScalaConversionUtil {

  def toScalaMap(javaMap: java.util.Map[String, AnyRef]): Map[String, Any] = {
    if (javaMap == null) {
      Map.empty[String, Any]
    } else {
      javaMap.asScala.filter {
        case (key, value) => value != null && StringUtils.isNotEmpty(key)
      }.map {
        case (key, value) => (key, toScala(value))
      }.toMap
    }
  }

  def toScalaList(javaList: java.util.Collection[AnyRef]): List[Any] = {
    if (javaList == null) {
      List.empty[Any]
    } else {
      javaList.asScala.filter(_ != null).map(toScala).toList
    }
  }

  def toScala(obj: Any) = {
    obj match {
      case list: java.util.Collection[AnyRef] => toScalaList(list)
      case map: java.util.Map[String, AnyRef] => toScalaMap(map)
      case other: Any => other
    }
  }

  def toJava(obj: Any): AnyRef = {
    obj match {
      case list: List[Any] => toJavaList(list)
      case map: Map[String, Any] => toJavaMap(map)
      case other: Any => other.asInstanceOf[AnyRef]
    }
  }

  def toJavaList(scalaList: Seq[Any]): util.ArrayList[AnyRef] = {
    val javaList = new util.ArrayList[AnyRef]()
    scalaList.map { e => javaList.add(toJava(e)) }
    javaList
  }

  def toJavaMap(scalaMap: Map[String, Any]): java.util.Map[String, AnyRef] = {
    val javaMap = new util.HashMap[String, AnyRef]()
    scalaMap.map {
      case (key: String, value: Any) => javaMap.put(key, toJava(value))
    }
    javaMap
  }
}
