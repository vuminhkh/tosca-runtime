package com.toscaruntime.util

import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}
import java.util.function.Consumer

import scala.collection.mutable.ListBuffer

object ScalaFileUtil {

  def doRecursiveWithPath[T](path: Path, action: Path => Unit, filter: Path => Boolean = _ => true) = {
    Files.walkFileTree(path, new SimpleFileVisitor[Path] {
      override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = {
        if (filter(dir)) action(dir)
        FileVisitResult.CONTINUE
      }

      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
        if (filter(file)) action(file)
        FileVisitResult.CONTINUE
      }
    })
  }

  def listRecursive(path: Path, filter: Path => Boolean = _ => true) = {
    val files = ListBuffer[Path]()
    doRecursiveWithPath(path, nestedPath => files += nestedPath, filter)
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
