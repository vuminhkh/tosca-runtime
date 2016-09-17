package com.toscaruntime.util

import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}
import java.util.function.Consumer

import scala.collection.mutable.ListBuffer

object ScalaFileUtil {

  def listRecursive(path: Path, filter: Path => Boolean = _ => true) = {
    val files = ListBuffer[Path]()
    Files.walkFileTree(path, new SimpleFileVisitor[Path] {
      override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = {
        if (filter(dir)) files += dir
        FileVisitResult.CONTINUE
      }

      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
        if (filter(file)) files += file
        FileVisitResult.CONTINUE
      }
    })
    files.toList
  }

  def list(path: Path, filter: Path => Boolean = _ => true) = {
    val files = ListBuffer[Path]()
    Files.newDirectoryStream(path).forEach(new Consumer[Path] {
      override def accept(t: Path): Unit = if (filter(t)) files += t
    })
    files.toList
  }

  def listJavaClassNames(sourcePath: Path) = {
    ScalaFileUtil.listRecursive(sourcePath, path => path.getFileName.toString.endsWith(".java"))
      .map(path => FileUtil.relativizePath(sourcePath, path))
      .map(relativePath => {
        val withoutExtension = relativePath.substring(0, relativePath.length - 5)
        withoutExtension.replaceAll("""[/\\]""", """\.""")
      })
  }

  def listDirectories(path: Path) = {
    list(path, path => Files.isDirectory(path))
  }

  def listFiles(path: Path) = {
    list(path, path => Files.isRegularFile(path))
  }
}
