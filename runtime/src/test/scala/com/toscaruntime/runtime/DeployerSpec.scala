package com.toscaruntime.runtime

import java.nio.file.Paths

import com.toscaruntime.compiler.{AbstractSpec, Compiler, TestConstant}
import com.toscaruntime.util.GitClient

class DeployerSpec extends AbstractSpec {

  "Deployer" must {
    "be able to create deployment for assembled topology for docker" in {
      val normativeTypesOutput = TestConstant.GIT_TEST_DATA_PATH.resolve("tosca-normative-types")
      GitClient.clone("https://github.com/alien4cloud/tosca-normative-types.git", normativeTypesOutput)
      Compiler.install(normativeTypesOutput, TestConstant.CSAR_REPOSITORY_PATH)
      val sampleTypesOutput = TestConstant.GIT_TEST_DATA_PATH.resolve("samples")
      GitClient.clone("https://github.com/alien4cloud/samples.git", sampleTypesOutput)

      installAndAssertCompilationResult(sampleTypesOutput.resolve("apache"))
      installAndAssertCompilationResult(sampleTypesOutput.resolve("mysql"))
      installAndAssertCompilationResult(sampleTypesOutput.resolve("php"))
      installAndAssertCompilationResult(sampleTypesOutput.resolve("wordpress"))
      installAndAssertCompilationResult(sampleTypesOutput.resolve("topology-wordpress"))

      installAndAssertCompilationResult(Paths.get("docker/src/main/resources/docker-provider-types"))

      val wordpressTopologyOutput = assemblyDockerTopologyAndAssertCompilationResult("csars/topologyWordpressDocker/")
      val deployment = Deployer.createDeployment(
        deploymentName = "wordpress",
        deploymentRecipeFolder = wordpressTopologyOutput,
        inputs = Map.empty[String, AnyRef],
        providerProperties = Map.empty[String, String],
        bootstrapContext = Map.empty[String, AnyRef],
        bootstrap = true
      )
      deployment.getConfig.getDeploymentName must be("wordpress")
      deployment.getConfig.getRecipePath must be(wordpressTopologyOutput)
      deployment.getConfig.getArtifactsPath must be(wordpressTopologyOutput.resolve("src").resolve("main").resolve("resources"))
      deployment.getNodes.size() must be(6)
      deployment.getRelationshipNodes.size() must be(6)
      val container = deployment.getNodeInstancesByNodeName("computeWww").iterator().next()
      container.getMandatoryPropertyAsString("image_id") must be("phusion/baseimage")
      container.getMandatoryPropertyAsString("port_mappings[0].from") must be("80")
      container.getMandatoryPropertyAsString("port_mappings[0].to") must be("51000")
      container.getMandatoryPropertyAsString("exposed_ports[0].port") must be("80")
      deployment.getOutputs.size() must be(1)
      deployment.getOutputs.containsKey("wordpress_url") must be(true)
    }
  }
}
