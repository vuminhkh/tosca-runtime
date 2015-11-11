package com.toscaruntime.cli.parser.completion

import java.io.{File, IOException}
import java.nio.file.{Files, Paths}
import com.toscaruntime.util.FileUtil
import sbt.complete.ExampleSource

import scala.collection.JavaConverters._

class FileCompletion(currentPath: String = "") extends ExampleSource {

  override def apply(): Iterable[String] = {
    val filesFound = files(currentPath)
    filesFound.map(_.substring(currentPath.length))
  }

  override def withAddedPrefix(addedPrefix: String): FileCompletion = {
    val newPath = currentPath + addedPrefix
    new FileCompletion(newPath)
  }

  protected def files(filePrefix: String) = {
    var prefix = filePrefix
    if (prefix.endsWith(File.separator + ".")) {
      prefix = prefix.substring(0, prefix.length - 2)
    }
    if (".".equals(prefix)) {
      prefix = ""
    }
    var path = Paths.get(prefix)
    if (!Files.isDirectory(path)) {
      if (path.getParent != null) {
        // It has a parent the try to list the content of its parent
        path = path.getParent
      } else if (path.getRoot == null) {
        // It does not have a root ==> relative path
        path = Paths.get("")
      }
    }
    try {
      FileUtil.ls(path).asScala.filter(_.startsWith(filePrefix)).toSeq
    } catch {
      case e: IOException => Seq.empty
    }
  }
}
