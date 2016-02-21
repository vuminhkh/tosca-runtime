package com.toscaruntime.util

import java.io.FileNotFoundException
import java.nio.file.{Files, Path}

object PathUtil {

  /**
    * Open a path (which can be zipped) as a directory and perform the action with it.
    *
    * @param path             the path to the directory (can be zipped)
    * @param action           action with the directory
    * @param createIfNotExist create a zipped file if the given path does not exist, default to false a FileNotFoundException will be thrown
    * @tparam T result type of the action
    * @return result of the action
    */
  def openAsDirectory[T](path: Path, action: Path => T, createIfNotExist: Boolean = false) = {
    val isZipped = Files.isRegularFile(path)
    if (!createIfNotExist && !Files.exists(path)) throw new FileNotFoundException(s"File cannot be found at $path")
    var realPath = path
    if (isZipped) {
      realPath = FileUtil.createZipFileSystem(path)
    }
    try {
      action(realPath)
    } finally {
      if (isZipped) {
        realPath.getFileSystem.close()
      }
    }
  }
}
