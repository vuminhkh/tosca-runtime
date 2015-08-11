package com.mkv.tosca.compiler

import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, SimpleFileVisitor, Files, Path}
import collection.JavaConversions._
import com.mkv.tosca.sdk.Deployment
import org.abstractmeta.toolbox.compilation.compiler.impl.JavaSourceCompilerImpl

/**
 * Deploy generated code
 */
object Deployer {

  def compile(generatedRecipe: Path): ClassLoader = {
    val javaSourceCompiler = new JavaSourceCompilerImpl
    val compilationUnit = javaSourceCompiler.createCompilationUnit
    Files.walkFileTree(generatedRecipe, new SimpleFileVisitor[Path] {
      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
        var relativeSourcePath = generatedRecipe.relativize(file).toString
        val indexOfExtension = relativeSourcePath.indexOf(".java")
        if (indexOfExtension > 0) {
          relativeSourcePath = relativeSourcePath.substring(0, indexOfExtension)
          compilationUnit.addJavaSource(relativeSourcePath.replaceAll("/", "."), new String(Files.readAllBytes(file)));
        }
        return super.visitFile(file, attrs)
      }
    })
    return javaSourceCompiler.compile(Thread.currentThread().getContextClassLoader, compilationUnit)
  }

  def deploy(generatedRecipe: Path, inputs: Map[String, String]) = {
    val classLoader = compile(generatedRecipe)
    val deployment = classLoader.loadClass("Deployment").newInstance().asInstanceOf[Deployment]
    deployment.initializeDeployment(generatedRecipe, mapAsJavaMap(inputs))
    deployment.install()
  }
}
