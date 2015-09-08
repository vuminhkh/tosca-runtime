package com.mkv.tosca.test.docker

import java.nio.file.{Files, Paths}

import com.mkv.tosca.compiler.Compiler
import com.mkv.tosca.runtime.Deployer

object CompileTest {

  def main(args: Array[String]): Unit = {
    val apachePath = Paths.get("src/test/resources/components/apache")
    val mysqlPath = Paths.get("src/test/resources/components/mysql")
    val phpPath = Paths.get("src/test/resources/components/php")
    val wordpressPath = Paths.get("src/test/resources/components/wordpress")
    val wordpressTopology = Paths.get("src/test/resources/topologies/wordpress")
    val dockerPath = Paths.get("../docker")
    val outputDeployment = Paths.get("target/tosca/")
    Files.createDirectories(outputDeployment)
    val compiled = Compiler.compile(wordpressTopology, List(apachePath, mysqlPath, phpPath, wordpressPath), dockerPath, outputDeployment)
    if (compiled) {
      val providerProperties = Map("docker.io.url" -> "https://192.168.99.100:2376", "docker.io.dockerCertPath" -> (System.getProperty("user.home") + "/.docker/machine/machines/default"))
      Deployer.deploy(outputDeployment, Map.empty, providerProperties)
    }
  }
}
