package com.mkv.tosca.runtime

import java.io.File
import java.net.{URL, URLClassLoader}
import java.nio.file._
import java.nio.file.attribute.BasicFileAttributes

import com.google.common.collect.Maps
import com.mkv.tosca.compiler.Util
import com.mkv.tosca.constant.CompilerConstant
import com.mkv.tosca.sdk.{Deployment, DeploymentPostConstructor}
import com.typesafe.config.Config
import org.abstractmeta.toolbox.compilation.compiler.impl.JavaSourceCompilerImpl
import org.slf4j.{Logger, LoggerFactory}
import org.yaml.snakeyaml.Yaml

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer

/**
 * Deploy generated code
 */
object Deployer {

  private val log: Logger = LoggerFactory.getLogger(Deployer.getClass)

  val yamlParser = new Yaml()

  def getClassPath(classLoader: ClassLoader) = {
    val buffer = new StringBuilder(System.getProperty("java.class.path"))
    var currentClassLoader = classLoader
    while (currentClassLoader != null) {
      currentClassLoader match {
        case loader: URLClassLoader =>
          val pathSeparator = System.getProperty("path.separator")
          if (buffer.nonEmpty && !buffer.endsWith(pathSeparator)) {
            buffer.append(pathSeparator)
          }
          for (url <- loader.getURLs) {
            buffer.append(new File(url.toURI).getPath)
            buffer.append(System.getProperty("path.separator"))
          }
          buffer.setLength(buffer.length - 1)
        case _ =>
      }
      currentClassLoader = currentClassLoader.getParent
    }
    buffer.toString()
  }

  def createDeploymentClassLoader(libPath: Path) = {
    val parentClassLoader = Thread.currentThread().getContextClassLoader
    if (Files.exists(libPath)) {
      val allJars = ListBuffer[URL]()
      Files.walkFileTree(libPath, new SimpleFileVisitor[Path]() {
        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          if (file.toString.endsWith(".jar")) {
            allJars += file.toUri.toURL
          }
          FileVisitResult.CONTINUE
        }
      })
      log.info("Using url classloader with parent " + parentClassLoader)
      new URLClassLoader(allJars.toList.toArray[URL], parentClassLoader)
    } else {
      log.info("Using parent classloader " + parentClassLoader)
      parentClassLoader
    }
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
    val currentClassPath = getClassPath(contextClassLoader)
    log.info("Compiling using classpath " + currentClassPath)
    (allLoadedClasses.toList, javaSourceCompiler.compile(contextClassLoader, compilationUnit, "classpath", currentClassPath))
  }

  /**
   * Deploy the given recipe from the given recipe folder and the given input file and given provider configuration file
   *
   * @param deploymentRecipeFolder recipe's path
   * @param inputFile deployment input file
   * @param providerConfig provider's configuration
   * @return the created deployment
   */
  def deploy(deploymentRecipeFolder: Path, inputFile: Option[Path], providerConfig: Config): Deployment = {
    val inputs = inputFile.map(input => yamlParser.loadAs(Files.newInputStream(input), classOf[java.util.Map[String, AnyRef]])).getOrElse(Maps.newHashMap[String, AnyRef]())
    val providerConfiguration = providerConfig.entrySet().asScala.map { entry =>
      (entry.getKey, entry.getValue.unwrapped().asInstanceOf[String])
    }.toMap
    deploy(deploymentRecipeFolder, inputs.asScala.toMap, providerConfiguration)
  }

  /**
   * Deploy the given recipe from the given recipe folder and the given input properties and given provider properties
   *
   * @param deploymentRecipeFolder recipe's path
   * @param inputs deployment input
   * @param providerProperties provider's properties
   * @return the created deployment
   */
  def deploy(deploymentRecipeFolder: Path, inputs: Map[String, AnyRef], providerProperties: Map[String, String]): Deployment = {
    val compiledClasses = compileJavaRecipe(
      List(
        deploymentRecipeFolder.resolve(CompilerConstant.TYPES_FOLDER),
        deploymentRecipeFolder.resolve(CompilerConstant.DEPLOYMENT_FOLDER)
      ), deploymentRecipeFolder.resolve(CompilerConstant.LIB_FOLDER))
    val classLoader = compiledClasses._2
    val loadedClasses = compiledClasses._1
    val currentClassLoader = Thread.currentThread().getContextClassLoader
    Thread.currentThread().setContextClassLoader(classLoader)
    val deployment = classLoader.loadClass("Deployment").newInstance().asInstanceOf[Deployment]
    deployment.initializeDeployment(deploymentRecipeFolder, inputs.asJava)
    val deploymentPostConstructors = Util.findImplementations(loadedClasses, classLoader, classOf[DeploymentPostConstructor])
    deploymentPostConstructors.foreach(_.newInstance().asInstanceOf[DeploymentPostConstructor].postConstruct(deployment, providerProperties.asJava))
    deployment.install()
    Thread.currentThread().setContextClassLoader(currentClassLoader)
    deployment
  }
}
