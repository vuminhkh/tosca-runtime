package com.toscaruntime.util

import java.nio.file.{FileSystems, Paths}
import java.util

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
    if (resourcesUrl == null) {
      null
    } else {
      val resourcesUri = resourcesUrl.toURI
      if (!"file".equals(resourcesUrl.getProtocol)) {
        val env = new util.HashMap[String, String]()
        env.put("create", "true")
        FileSystems.newFileSystem(resourcesUri, env)
      }
      Paths.get(resourcesUri)
    }
  }
}
