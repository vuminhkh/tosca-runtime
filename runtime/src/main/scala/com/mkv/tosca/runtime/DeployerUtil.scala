package com.mkv.tosca.runtime

import java.io.File
import java.net.{URL, URLClassLoader}
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Files, Path, SimpleFileVisitor}

import com.mkv.tosca.util.ClassLoaderUtil
import org.abstractmeta.toolbox.compilation.compiler.impl.JavaSourceCompilerImpl
import org.slf4j.{Logger, LoggerFactory}
import org.yaml.snakeyaml.Yaml

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
 * All utility for deployer
 *
 * @author Minh Khang VU
 */
object DeployerUtil {

  private val log: Logger = LoggerFactory.getLogger(DeployerUtil.getClass)

  val yamlParser = new Yaml()

  def loadInputs(inputFile: Option[Path]): Map[String, AnyRef] = {
    inputFile.map(input => yamlParser.loadAs(Files.newInputStream(input), classOf[java.util.Map[String, AnyRef]]).asScala.toMap).getOrElse(Map.empty[String, AnyRef])
  }

  def appendJarURLsToBuffer(buffer: StringBuilder, urls: Array[URL]) = {
    val pathSeparator = System.getProperty("path.separator")
    if (buffer.nonEmpty && !buffer.endsWith(pathSeparator)) {
      buffer.append(pathSeparator)
    }
    for (url <- urls) {
      buffer.append(new File(url.toURI).getPath)
      buffer.append(System.getProperty("path.separator"))
    }
    buffer.setLength(buffer.length - 1)
  }

  def getClassPath(classLoader: ClassLoader, libPath: Path) = {
    val buffer = new StringBuilder()
    if (Files.exists(libPath)) {
      // Real context
      log.info("Loading classpath for on the fly compilation from library folder " + libPath)
      val allJars = ListBuffer[URL]()
      Files.walkFileTree(libPath, new SimpleFileVisitor[Path]() {
        override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
          if (file.toString.endsWith(".jar")) {
            allJars += file.toUri.toURL
          }
          FileVisitResult.CONTINUE
        }
      })
      appendJarURLsToBuffer(buffer, allJars.toArray)
    } else {
      // Dev context
      log.info("Loading classpath for on the fly compilation from existing class loader " + classLoader)
      var currentClassLoader = classLoader
      val allURLs = mutable.LinkedHashSet[URL]()
      while (currentClassLoader != null) {
        currentClassLoader match {
          case urlClassLoader: URLClassLoader =>
            urlClassLoader.getURLs.foreach(allURLs.add)
          case _ =>
        }
        currentClassLoader = currentClassLoader.getParent
      }
      appendJarURLsToBuffer(buffer, allURLs.toArray)
    }
    buffer.toString()
  }

  def compileJavaRecipe(sourcePaths: List[Path], libPath: Path): (List[String], ClassLoader) = {
    // TODO Hacking the class loader in order to force sdk class loading from the context class loader, other classes should be loaded from the dynamically created
    val deploymentClassLoader = new DeploymentClassLoader(Thread.currentThread().getContextClassLoader, "com.mkv.tosca", "tosca")
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
            if (!ClassLoaderUtil.isTypeDefined(className)) {
              // Only load if really does not exist in parent class loader
              compilationUnit.addJavaSource(className, new String(Files.readAllBytes(file)))
            }
          }
          super.visitFile(file, attrs)
        }
      })
    }
    val currentClassPath = getClassPath(Thread.currentThread().getContextClassLoader, libPath)
    log.info("Compiling on the fly using classpath " + currentClassPath)
    (allLoadedClasses.toList, javaSourceCompiler.compile(deploymentClassLoader, compilationUnit, "classpath", currentClassPath))
  }

  def findImplementations(typesToScan: List[String], classLoader: ClassLoader, implementedType: Class[_]) = {
    typesToScan.filter(className => implementedType.isAssignableFrom(classLoader.loadClass(className))).map(classLoader.loadClass)
  }
}
