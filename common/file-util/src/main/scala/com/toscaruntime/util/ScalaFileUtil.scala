package com.toscaruntime.util

import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes
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

  def relativizePath(root: Path, child: Path): String = {
    val childPath: String = child.toAbsolutePath.toString
    val rootPath: String = root.toAbsolutePath.toString
    if (childPath == rootPath) return ""
    val indexOfRootInChild: Int = childPath.indexOf(rootPath)
    if (indexOfRootInChild != 0) throw new IllegalArgumentException("Child path " + childPath + "is not beginning with root path " + rootPath)
    var relativizedPath: String = childPath.substring(rootPath.length, childPath.length)
    while (relativizedPath.startsWith(root.getFileSystem.getSeparator)) relativizedPath = relativizedPath.substring(1)
    relativizedPath
  }

  def copyRecursive(source: Path, target: Path, filter: Path => Boolean, copyOptions: CopyOption*): Unit = {
    if (Files.isRegularFile(source)) {
      // Simple file copy
      if (Files.notExists(target)) Files.createDirectories(target.getParent)
      Files.copy(source, target, copyOptions: _*)
      return
    }
    // Directories copy
    if (Files.notExists(target)) Files.createDirectories(target)
    doRecursiveWithPath(source, file => {
      if (Files.isRegularFile(file)) {
        val fileRelativePath = relativizePath(source, file)
        val destFile = target.resolve(fileRelativePath)
        Files.copy(file, destFile, copyOptions: _*)
      } else if (Files.isDirectory(file)) {
        val dirRelativePath = relativizePath(source, file)
        val destDir = target.resolve(dirRelativePath)
        Files.createDirectories(destDir)
      }
    }, filter)
  }

  /**
    * Copy from file to file or from directory to directory
    *
    * @param source source file or directory
    * @param target target file or directory
    */
  def copyRecursive(source: Path, target: Path): Unit = {
    copyRecursive(source, target, _ => true, StandardCopyOption.REPLACE_EXISTING)
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
      .map(path => relativizePath(sourcePath, path))
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
