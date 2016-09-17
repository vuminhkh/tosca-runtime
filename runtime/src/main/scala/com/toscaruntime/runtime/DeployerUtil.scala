package com.toscaruntime.runtime

import java.nio.file.{Files, Path}

import com.toscaruntime.util.JavaScalaConversionUtil
import org.slf4j.{Logger, LoggerFactory}
import org.yaml.snakeyaml.Yaml

/**
  * All utility for deployer
  *
  * @author Minh Khang VU
  */
object DeployerUtil {

  private val log: Logger = LoggerFactory.getLogger(DeployerUtil.getClass)

  val yamlParser = new Yaml()

  def loadInputs(inputFile: Option[Path]): Map[String, Any] = {
    inputFile.map(input => JavaScalaConversionUtil.toScalaMap(yamlParser.loadAs(Files.newInputStream(input), classOf[java.util.Map[String, AnyRef]]))).getOrElse(Map.empty[String, AnyRef])
  }

  def findImplementations(typesToScan: Iterable[Class[_]], implementedType: Class[_]) = {
    typesToScan.filter(clazz => implementedType.isAssignableFrom(clazz))
  }
}
