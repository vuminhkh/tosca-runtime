package com.mkv.tosca.runtime

import java.nio.file._

import com.mkv.tosca.compiler.Compiler


object ParserTest {

  def main(args: Array[String]): Unit = {
    val normativePath = Paths.get("src/test/resources/components/tosca-normative-types")
    val apachePath = Paths.get("src/test/resources/components/apache")
    val mysqlPath = Paths.get("src/test/resources/components/mysql")
    val phpPath = Paths.get("src/test/resources/components/php")
    val wordpressPath = Paths.get("src/test/resources/components/wordpress")
    val wordpressTopology = Paths.get("src/test/resources/topologies/wordpress")
    val dockerPath = Paths.get("/Users/mkv/tosca-runtime/docker/src/main/resources/tosca")
    val output = Paths.get("./target/tosca")

    Compiler.compile(normativePath, List.empty, output)
    Compiler.compile(dockerPath, List(normativePath), output)
    Compiler.compile(mysqlPath, List(normativePath), output)
    Compiler.compile(phpPath, List(normativePath), output)
    Compiler.compile(wordpressPath, List(normativePath, apachePath, mysqlPath, phpPath), output)
    Compiler.compile(wordpressTopology, List(wordpressPath, normativePath, apachePath, mysqlPath, phpPath, dockerPath), output)
    Deployer.deploy(output, Map("daemon_url" -> "https://192.168.59.103:2376"))
  }

}
