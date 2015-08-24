package com.mkv.tosca.runtime

import java.nio.file.Paths
import java.util.Properties

import com.mkv.tosca.compiler.Compiler

object BatchCompileTest {

  def main(args: Array[String]): Unit = {
    val normativePath = Paths.get("src/test/resources/components/tosca-normative-types")
    val apachePath = Paths.get("src/test/resources/components/apache")
    val mysqlPath = Paths.get("src/test/resources/components/mysql")
    val phpPath = Paths.get("src/test/resources/components/php")
    val wordpressPath = Paths.get("src/test/resources/components/wordpress")
    val wordpressTopology = Paths.get("src/test/resources/topologies/wordpress")
    val dockerPath = Paths.get("/Users/mkv/tosca-runtime/docker/src/main/resources/tosca")
    val outputDeployment = Paths.get("target/tosca/wordpress-deployment-batch.zip")
    Compiler.batchCompile(wordpressTopology, List(normativePath, apachePath, mysqlPath, phpPath, wordpressPath, dockerPath), outputDeployment)
    val properties = new Properties()
    properties.setProperty("docker.io.url", "https://192.168.99.100:2376")
    properties.setProperty("docker.io.dockerCertPath", "/Users/mkv/.docker/machine/machines/default")
    Deployer.deploy(outputDeployment, properties)
  }
}
