package com.toscaruntime.test.openstack

import java.nio.file.{Files, Paths}
import com.toscaruntime.compiler.Compiler
import com.toscaruntime.test.util.NormativeTypesLoader
import com.toscaruntime.tosca.runtime.Deployer
import com.toscaruntime.util.FileUtil

object CompileOSApacheTest {

  def main(args: Array[String]): Unit = {
    val userHome = System.getProperty("user.home")
    val apachePath = Paths.get("src/test/resources/components/apache")
    val apacheTopology = Paths.get("src/test/resources/topologies/apache-os")
    val openstackPath = Paths.get("../openstack")
    val outputDeployment = Paths.get(userHome + "/.tosca/deployment/recipe-apache-os")
    FileUtil.delete(outputDeployment)
    Files.createDirectories(outputDeployment)
    val compiled = Compiler.compile(apacheTopology, List(apachePath), openstackPath, NormativeTypesLoader.normativeTypesPath, outputDeployment)
    if (compiled) {
      val providerProperties = Map(
        "keystone_url" -> "http://128.136.179.2:5000/v2.0",
        "tenant" -> "facebook1389662728",
        "user" -> "facebook1389662728",
        "region" -> "RegionOne",
        "password" -> "mqAgNPA2c6VDjoOD"
      )
      val inputs = Map(
        "image" -> "cb6b7936-d2c5-4901-8678-c88b3a6ed84c",
        "flavor" -> "2",
        "key_path" -> "toscaruntime.pem",
        "login" -> "ubuntu",
        "key_pair_name" -> "toscaruntime",
        "security_group_names" -> "openbar",
        "external_network_id" -> "0e43db46-8fd9-4ef1-8826-4cf9e809aede"
      )
      Deployer.createDeployment(outputDeployment, inputs, providerProperties, false)
    }
  }
}
