package com.mkv.tosca.runtime

import java.nio.file.attribute.BasicFileAttributes
import java.nio.file._
import java.util.Properties

import com.google.common.io.Closeables
import com.mkv.tosca.compiler.Constant
import com.mkv.tosca.sdk.Deployment
import com.mkv.util.FileUtil
import org.abstractmeta.toolbox.compilation.compiler.impl.JavaSourceCompilerImpl

/**
 * Deploy generated code
 */
object Deployer {

  def compileJavaRecipe(sourcePaths: List[Path]): ClassLoader = {
    val javaSourceCompiler = new JavaSourceCompilerImpl
    val compilationUnit = javaSourceCompiler.createCompilationUnit
    sourcePaths.foreach { sourcePath =>
      Files.walkFileTree(sourcePath, new SimpleFileVisitor[Path] {
        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          var relativeSourcePath = sourcePath.relativize(file).toString
          val indexOfExtension = relativeSourcePath.indexOf(".java")
          if (indexOfExtension > 0) {
            relativeSourcePath = relativeSourcePath.substring(0, indexOfExtension)
            compilationUnit.addJavaSource(relativeSourcePath.replaceAll("/", "."), new String(Files.readAllBytes(file)));
          }
          return super.visitFile(file, attrs)
        }
      })
    }
    return javaSourceCompiler.compile(Thread.currentThread().getContextClassLoader, compilationUnit)
  }

  def deploy(generatedRecipe: Path, inputs: Properties): Unit = {
    var deploymentName = generatedRecipe.getFileName.toString
    val indexOfExtension = deploymentName.indexOf('.')
    if (indexOfExtension > 0) {
      deploymentName = deploymentName.substring(0, indexOfExtension)
    }
    deploy(deploymentName, generatedRecipe, inputs)
  }

  def deploy(deploymentName: String, generatedRecipe: Path, inputs: Properties): Unit = {
    var recipeToDeploy = generatedRecipe
    val isZippedRecipe = Files.isRegularFile(generatedRecipe)
    if (isZippedRecipe) {
      recipeToDeploy = FileUtil.createZipFileSystem(generatedRecipe)
    }
    try {
      val classLoader = compileJavaRecipe(List(recipeToDeploy.resolve(Constant.TYPES_FOLDER), recipeToDeploy.resolve(Constant.DEPLOYMENT_FOLDER)))
      val deployment = classLoader.loadClass("Deployment").newInstance().asInstanceOf[Deployment]
      val deploymentRecipeFolder = ToscarApp.deploymentsDir().resolve(deploymentName)
      FileUtil.copy(recipeToDeploy.resolve(Constant.ARCHIVE_FOLDER), deploymentRecipeFolder, StandardCopyOption.REPLACE_EXISTING)
      deployment.initializeDeployment(deploymentRecipeFolder, inputs)
      deployment.install()
    } finally {
      if (isZippedRecipe) {
        Closeables.close(recipeToDeploy.getFileSystem, true)
      }
    }
  }
}
