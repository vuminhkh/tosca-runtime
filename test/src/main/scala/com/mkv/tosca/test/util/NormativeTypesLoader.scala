package com.mkv.tosca.test.util

import java.nio.file.{FileSystems, Path, Paths}

import com.google.common.collect.Maps

/**
 * Utility class to load normative types from classpath
 *
 * @author Minh Khang VU
 */
object NormativeTypesLoader {

  val normativeTypesPath: Path = {
    val normativeTypesUrl = Thread.currentThread().getContextClassLoader.getResource("tosca-normative-types/")
    val normativeTypesUri = normativeTypesUrl.toURI
    if (!"file".equals(normativeTypesUrl.getProtocol)) {
      val env = Maps.newHashMap[String, String]()
      env.put("create", "true")
      FileSystems.newFileSystem(normativeTypesUri, env)
    }
    Paths.get(normativeTypesUri)
  }

}
