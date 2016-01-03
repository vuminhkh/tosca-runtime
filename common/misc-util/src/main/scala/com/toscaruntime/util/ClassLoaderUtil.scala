package com.toscaruntime.util

import java.nio.file.{FileSystems, Paths}

import com.google.common.collect.Maps

/**
  * Utility to deal with class loader
  *
  * @author Minh Khang VU
  */
object ClassLoaderUtil {

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

  def getPathForResource(resource: String) = {
    val resourcesUrl = Thread.currentThread().getContextClassLoader.getResource(resource)
    val resourcesUri = resourcesUrl.toURI
    if (!"file".equals(resourcesUrl.getProtocol)) {
      val env = Maps.newHashMap[String, String]()
      env.put("create", "true")
      FileSystems.newFileSystem(resourcesUri, env)
    }
    Paths.get(resourcesUri)
  }
}
