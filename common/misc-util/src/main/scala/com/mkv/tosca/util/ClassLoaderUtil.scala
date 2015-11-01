package com.mkv.tosca.util

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
}
