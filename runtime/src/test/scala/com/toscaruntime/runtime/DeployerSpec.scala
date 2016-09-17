package com.toscaruntime.runtime

import java.nio.file.{Paths, StandardCopyOption}

import com.toscaruntime.compiler.{AbstractSpec, Compiler}
import com.toscaruntime.deployment.DeploymentPersister
import com.toscaruntime.util.{FileUtil, GitClient}
import org.mockito.Mockito

class DeployerSpec extends AbstractSpec {

  override lazy val testPath = Paths.get("target").resolve("deployer-test-data")

  "Deployer" must {
    "be able to create deployment for wordpress topology for docker" in {
      val normativeTypesOutput = gitPath.resolve("tosca-normative-types")
      GitClient.clone("https://github.com/vuminhkh/tosca-normative-types.git", normativeTypesOutput)
      Compiler.install(normativeTypesOutput, csarsPath)
      val sampleTypesOutput = gitPath.resolve("samples")
      GitClient.clone("https://github.com/vuminhkh/samples.git", sampleTypesOutput)

      installAndAssertCompilationResult(sampleTypesOutput.resolve("apache"))
      installAndAssertCompilationResult(sampleTypesOutput.resolve("mysql"))
      installAndAssertCompilationResult(sampleTypesOutput.resolve("php"))
      installAndAssertCompilationResult(sampleTypesOutput.resolve("wordpress"))
      installAndAssertCompilationResult(sampleTypesOutput.resolve("topology-wordpress"))

      installProvider("docker")

      val wordpressTopologyOutput = assemblyDockerTopologyAndAssertCompilationResult("csars/topologyWordpressDocker/")
      FileUtil.delete(assemblyPath.resolve("topologyWordpressDocker/providers/docker/src/main/java/com/toscaruntime/docker/DockerProviderHook.java"))
      FileUtil.copy(Paths.get("providers/mock/src/main/java/com/toscaruntime/mock/MockProviderHook.java"), assemblyPath.resolve("topologyWordpressDocker/providers/docker/src/main/java/com/toscaruntime/mock/MockProviderHook.java"), StandardCopyOption.REPLACE_EXISTING)
      val deployment = Deployer.createDeployment(
        deploymentName = "wordpress",
        deploymentAssemblyFolder = wordpressTopologyOutput,
        inputs = Map.empty[String, Any],
        providerConfigurations = List.empty,
        pluginConfigurations = List.empty,
        bootstrapContext = Map.empty[String, Any],
        bootstrap = true,
        Thread.currentThread().getContextClassLoader,
        Mockito.mock(classOf[DeploymentPersister])
      )
      deployment.createInstances()
      deployment.getConfig.getDeploymentName must be("wordpress")
      deployment.getConfig.getArtifactsPath must be(wordpressTopologyOutput.resolve("recipe").resolve("src").resolve("main").resolve("resources"))
      deployment.getNodes.size() must be(6)
      deployment.getRelationshipNodes.size() must be(6)
      val container = deployment.getNodeInstancesByNodeName("computeWww").iterator().next()
      container.getMandatoryPropertyAsString("image_id") must be("toscaruntime/ubuntu-trusty")
      container.getMandatoryPropertyAsString("port_mappings[0].from") must be("80")
      container.getMandatoryPropertyAsString("port_mappings[0].to") must be("51000")
      container.getMandatoryPropertyAsString("exposed_ports[0].port") must be("80")
      deployment.getOutputs.size() must be(1)
      deployment.getOutputs.containsKey("wordpress_url") must be(true)
    }
  }

  "Deployer" must {
    "be able to create deployment for apache load balancer topology for docker" in {
      val normativeTypesOutput = gitPath.resolve("tosca-normative-types")
      GitClient.clone("https://github.com/vuminhkh/tosca-normative-types.git", normativeTypesOutput)
      Compiler.install(normativeTypesOutput, csarsPath)

      val alienExtendedTypes = gitPath.resolve("alien-extended-types")
      GitClient.clone("https://github.com/vuminhkh/alien4cloud-extended-types.git", alienExtendedTypes)
      Compiler.install(alienExtendedTypes.resolve("alien-base-types"), csarsPath)
      Compiler.install(alienExtendedTypes.resolve("alien-extended-storage-types"), csarsPath)

      val sampleTypesOutput = gitPath.resolve("samples")
      GitClient.clone("https://github.com/vuminhkh/samples.git", sampleTypesOutput)

      installAndAssertCompilationResult(sampleTypesOutput.resolve("apache-load-balancer"))
      installAndAssertCompilationResult(sampleTypesOutput.resolve("tomcat-war"))
      installAndAssertCompilationResult(sampleTypesOutput.resolve("topology-load-balancer-tomcat"))

      installProvider("docker")

      val tomcatApacheOutput = assemblyDockerTopologyAndAssertCompilationResult("csars/topologyApacheLoadBalancerDocker/")
      FileUtil.delete(assemblyPath.resolve("topologyApacheLoadBalancerDocker/providers/docker/src/main/java/com/toscaruntime/docker/DockerProviderHook.java"))
      FileUtil.copy(Paths.get("providers/mock/src/main/java/com/toscaruntime/mock/MockProviderHook.java"), assemblyPath.resolve("topologyApacheLoadBalancerDocker/providers/docker/src/main/java/com/toscaruntime/mock/MockProviderHook.java"), StandardCopyOption.REPLACE_EXISTING)
      val deployment = Deployer.createDeployment(
        deploymentName = "tomcat-apache",
        deploymentAssemblyFolder = tomcatApacheOutput,
        inputs = Map.empty[String, AnyRef],
        providerConfigurations = List.empty,
        pluginConfigurations = List.empty,
        bootstrapContext = Map.empty[String, AnyRef],
        bootstrap = true,
        Thread.currentThread().getContextClassLoader,
        Mockito.mock(classOf[DeploymentPersister])
      )
      deployment.createInstances()
      deployment.getConfig.getDeploymentName must be("tomcat-apache")
      deployment.getConfig.getArtifactsPath must be(tomcatApacheOutput.resolve("recipe").resolve("src").resolve("main").resolve("resources"))
      deployment.getNodes.size() must be(7)
      val container = deployment.getNodeInstancesByNodeName("LoadBalancerServer").iterator().next()
      container.getMandatoryPropertyAsString("image_id") must be("toscaruntime/ubuntu-trusty")
      container.getMandatoryPropertyAsString("port_mappings[0].from") must be("80")
      container.getMandatoryPropertyAsString("port_mappings[0].to") must be("51000")
      container.getMandatoryPropertyAsString("exposed_ports[0].port") must be("80")
      deployment.getRelationshipNodes.size() must be(8)
      deployment.getOutputs.size() must be(1)
      deployment.getOutputs.containsKey("load_balancer_url") must be(true)
    }
  }
}
