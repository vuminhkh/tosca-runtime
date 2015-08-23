package com.mkv.tosca.runtime

import java.nio.file._
import java.util.Properties

import com.mkv.tosca.compiler.{Compiler, Packager}


object ParserTest {

  def main(args: Array[String]): Unit = {
    val normativePath = Paths.get("src/test/resources/components/tosca-normative-types")
    val outputNormativePath = Paths.get("target/tosca/normative.zip")
    val apachePath = Paths.get("src/test/resources/components/apache")
    val outputApachePath = Paths.get("target/tosca/apache.zip")
    val mysqlPath = Paths.get("src/test/resources/components/mysql")
    val outputMysqlPath = Paths.get("target/tosca/mysql.zip")
    val phpPath = Paths.get("src/test/resources/components/php")
    val outputPhpPath = Paths.get("target/tosca/php.zip")
    val wordpressPath = Paths.get("src/test/resources/components/wordpress")
    val outputWordpressPath = Paths.get("target/tosca/wordpress.zip")
    val wordpressTopology = Paths.get("src/test/resources/topologies/wordpress")
    val outputWordpressTopologyPath = Paths.get("target/tosca/wordpressTopology.zip")
    val dockerPath = Paths.get("/Users/mkv/tosca-runtime/docker/src/main/resources/tosca")
    val outputDockerPath = Paths.get("target/tosca/docker.zip")
    val outputDeployment = Paths.get("target/tosca/wordpress-deployment.zip")
    Compiler.compile(normativePath, List.empty, outputNormativePath)
    Compiler.compile(apachePath, List(outputNormativePath), outputApachePath)
    Compiler.compile(mysqlPath, List(outputNormativePath), outputMysqlPath)
    Compiler.compile(phpPath, List(outputNormativePath), outputPhpPath)
    Compiler.compile(wordpressPath, List(outputNormativePath, outputApachePath, outputMysqlPath, outputPhpPath), outputWordpressPath)
    Compiler.compile(dockerPath, List(outputNormativePath), outputDockerPath)
    Compiler.compile(wordpressTopology, List(outputNormativePath, outputApachePath, outputMysqlPath, outputPhpPath, outputWordpressPath, outputDockerPath), outputWordpressTopologyPath)
    Packager.produceDeployablePackage(outputWordpressTopologyPath, List(outputNormativePath, outputApachePath, outputMysqlPath, outputPhpPath, outputWordpressPath, outputDockerPath), outputDeployment)
    //    docker.io.version=1.16
    //    docker.io.username=dockeruser
    //    docker.io.password=ilovedocker
    //    docker.io.email=dockeruser@github.com
    //    docker.io.serverAddress=https://index.docker.io/v1/
    val properties = new Properties()
    properties.setProperty("docker.io.url", "https://192.168.99.100:2376")
    properties.setProperty("docker.io.dockerCertPath", "/Users/mkv/.docker/machine/machines/default")
    Deployer.deploy(outputDeployment, properties)
  }

}
