package com.toscaruntime.test.openstack

import java.nio.file.{Files, Paths}

import com.toscaruntime.compiler.Compiler
import com.toscaruntime.runtime.Deployer
import com.toscaruntime.test.util.NormativeTypesLoader

object CompileOSTest {

  def main(args: Array[String]): Unit = {
    val userHome = System.getProperty("user.home")
    val apachePath = Paths.get("src/test/resources/components/apache")
    val mysqlPath = Paths.get("src/test/resources/components/mysql")
    val phpPath = Paths.get("src/test/resources/components/php")
    val wordpressPath = Paths.get("src/test/resources/components/wordpress")
    val wordpressTopology = Paths.get("src/test/resources/topologies/wordpress-os")
    val openstackPath = Paths.get("../openstack")
    val outputDeployment = Paths.get(userHome + "/.tosca/deployment/recipe-os")
    Files.createDirectories(outputDeployment)
    val compiled = Compiler.compile(wordpressTopology, List(apachePath, mysqlPath, phpPath, wordpressPath), openstackPath, NormativeTypesLoader.normativeTypesPath, outputDeployment)
    if (compiled) {
      val providerProperties = Map(
        "keystone_url" -> "http://128.136.179.2:5000/v2.0",
        "tenant" -> "facebook1389662728",
        "user" -> "facebook1389662728",
        "region" -> "RegionOne",
        "password" -> "mqAgNPA2c6VDjoOD",
        "external_network_id" -> "0e43db46-8fd9-4ef1-8826-4cf9e809aede"
      )
      val inputs = Map(
        "image" -> "cb6b7936-d2c5-4901-8678-c88b3a6ed84c",
        "flavor" -> "2",
        "key_path" -> "toscaruntime.pem",
        "login" -> "ubuntu",
        "key_pair_name" -> "toscaruntime",
        "security_group_names" -> "openbar"
      )
      Deployer.createDeployment(outputDeployment, inputs, providerProperties, Map.empty[String, AnyRef], true)
    }
  }
}
