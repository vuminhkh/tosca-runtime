package com.toscaruntime.util

import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

import org.abstractmeta.toolbox.compilation.compiler.JavaSourceCompiler.CompilationUnit
import org.abstractmeta.toolbox.compilation.compiler.impl.JavaSourceCompilerImpl
import org.slf4j.{Logger, LoggerFactory}

object JavaCompiler {

  private val log: Logger = LoggerFactory.getLogger(JavaCompiler.getClass)

  def compileJava(sourcePath: Path, dependenciesPaths: List[Path], outputPath: Path): Unit = {
    compileJava(List(sourcePath), dependenciesPaths, outputPath)
  }

  private def compileFile(compilationUnit: CompilationUnit, childPath: Path, sourcePath: Path) = {
    var relativeSourcePath = if (sourcePath == childPath) sourcePath.getFileName.toString else sourcePath.relativize(childPath).toString
    val indexOfExtension = relativeSourcePath.indexOf(".java")
    if (indexOfExtension > 0) {
      relativeSourcePath = relativeSourcePath.substring(0, indexOfExtension)
      val className = relativeSourcePath.replaceAll("/", ".")
      compilationUnit.addJavaSource(className, new String(Files.readAllBytes(childPath)))
    }
  }

  def compileJava(sourcePaths: List[Path], dependenciesPaths: List[Path], outputPath: Path): Unit = {
    val javaSourceCompiler = new JavaSourceCompilerImpl
    val compilationUnit = javaSourceCompiler.createCompilationUnit(outputPath)
    sourcePaths.foreach { sourcePath =>
      if (Files.isDirectory(sourcePath)) {
        Files.walkFileTree(sourcePath, new SimpleFileVisitor[Path] {
          override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
            compileFile(compilationUnit, file, sourcePath)
            super.visitFile(file, attrs)
          }
        })
      } else {
        compileFile(compilationUnit, sourcePath, sourcePath)
      }
    }
    val currentClassPath = dependenciesPaths.mkString(System.getProperty("path.separator"))
    log.info(s"Compiling using classpath : $currentClassPath")
    javaSourceCompiler.compile(null, compilationUnit, "classpath", currentClassPath)
    javaSourceCompiler.persistCompiledClasses(compilationUnit)
  }
}
