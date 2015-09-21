package com.mkv.tosca.runtime

import java.net.URL
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

import com.mkv.tosca.compiler.{Constant, Util}
import com.mkv.tosca.sdk.{Deployment, DeploymentPostConstructor}
import org.abstractmeta.toolbox.compilation.compiler.impl.JavaSourceCompilerImpl

import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer
import scala.reflect.internal.util.ScalaClassLoader.URLClassLoader

/**
 * Deploy generated code
 */
object Deployer {

  def createDeploymentClassLoader(libPath: Path) = {
    if (Files.exists(libPath)) {
      val allJars = ListBuffer[URL]()
      Files.walkFileTree(libPath, new SimpleFileVisitor[Path]() {
        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          if (file.endsWith(".jar")) {
            allJars += file.toUri.toURL
          }
          FileVisitResult.CONTINUE
        }
      })
      new URLClassLoader(allJars.toList.toArray[URL], Thread.currentThread().getContextClassLoader)
    } else Thread.currentThread().getContextClassLoader
  }

  def compileJavaRecipe(sourcePaths: List[Path], libPath: Path): (List[String], ClassLoader) = {
    val contextClassLoader = createDeploymentClassLoader(libPath)
    val javaSourceCompiler = new JavaSourceCompilerImpl
    val compilationUnit = javaSourceCompiler.createCompilationUnit
    val allLoadedClasses = ListBuffer[String]()
    sourcePaths.foreach { sourcePath =>
      Files.walkFileTree(sourcePath, new SimpleFileVisitor[Path] {
        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          var relativeSourcePath = sourcePath.relativize(file).toString
          val indexOfExtension = relativeSourcePath.indexOf(".java")
          if (indexOfExtension > 0) {
            relativeSourcePath = relativeSourcePath.substring(0, indexOfExtension)
            val className = relativeSourcePath.replaceAll("/", ".")
            allLoadedClasses += className
            if (!Util.isTypeDefined(className)) {
              // Only load if really does not exist in parent class loader
              compilationUnit.addJavaSource(className, new String(Files.readAllBytes(file)))
            }
          }
          super.visitFile(file, attrs)
        }
      })
    }
    (allLoadedClasses.toList, javaSourceCompiler.compile(contextClassLoader, compilationUnit))
  }

  /**
   * Deploy the given recipe
   * @param deploymentRecipeFolder recipe's path
   * @param inputs deployment input
   * @param providerProperties provider's properties
   * @return the created deployment
   */
  def deploy(deploymentRecipeFolder: Path, inputs: Map[String, AnyRef], providerProperties: Map[String, AnyRef]): Deployment = {
    val compiledClasses = compileJavaRecipe(
      List(
        deploymentRecipeFolder.resolve(Constant.TYPES_FOLDER),
        deploymentRecipeFolder.resolve(Constant.DEPLOYMENT_FOLDER)
      ), deploymentRecipeFolder.resolve(Constant.LIB_FOLDER))
    val classLoader = compiledClasses._2
    val loadedClasses = compiledClasses._1
    val deployment = classLoader.loadClass("Deployment").newInstance().asInstanceOf[Deployment]

    deployment.initializeDeployment(deploymentRecipeFolder.resolve(Constant.ARCHIVE_FOLDER), mapAsJavaMap(inputs))
    val deploymentPostConstructors = Util.findImplementations(loadedClasses, classLoader, classOf[DeploymentPostConstructor])
    deploymentPostConstructors.foreach(_.newInstance().asInstanceOf[DeploymentPostConstructor].postConstruct(deployment, providerProperties))
    deployment.install()
    deployment
  }
}
