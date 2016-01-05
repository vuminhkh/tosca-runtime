package com.toscaruntime.compiler

import java.nio.file.Paths

import _root_.tosca.nodes.Root
import com.toscaruntime.compiler.tosca.ParsedValue
import com.toscaruntime.util.{ClassLoaderUtil, GitClient}

class CompilerSpec extends AbstractSpec {

  "Compiler" must {
    "be able to compile alien normative types" in {
      val normativeTypesOutput = gitPath.resolve("tosca-normative-types")
      GitClient.clone("https://github.com/alien4cloud/tosca-normative-types.git", normativeTypesOutput)
      val compilationResult = installAndAssertCompilationResult(normativeTypesOutput)
      val definition = compilationResult.csar.definitions.values.head
      definition.nodeTypes.get.contains(ParsedValue(classOf[Root].getName)) must be(true)
      definition.relationshipTypes.get.contains(ParsedValue(classOf[_root_.tosca.relationships.Root].getName)) must be(true)
      definition.relationshipTypes.get.contains(ParsedValue(classOf[_root_.tosca.relationships.HostedOn].getName)) must be(true)
      definition.relationshipTypes.get.contains(ParsedValue(classOf[_root_.tosca.relationships.AttachTo].getName)) must be(true)
      definition.relationshipTypes.get.contains(ParsedValue(classOf[_root_.tosca.relationships.Network].getName)) must be(true)
    }
  }

  "Compiler" must {
    "be able to show error when import not found" in {
      val compilationResult = Compiler.compile(ClassLoaderUtil.getPathForResource("csars/importNotExist/"), csarsPath)
      showCompilationErrors(compilationResult)
      compilationResult.isSuccessful must be(false)
      compilationResult.errors.head._2.size must be(4)
    }
  }

  "Compiler" must {
    "be able to compile alien extended types" in {
      val normativeTypesOutput = gitPath.resolve("tosca-normative-types")
      GitClient.clone("https://github.com/alien4cloud/tosca-normative-types.git", normativeTypesOutput)
      Compiler.install(normativeTypesOutput, csarsPath)
      val alienExtendedTypes = gitPath.resolve("alien-extended-types")
      GitClient.clone("https://github.com/alien4cloud/alien4cloud-extended-types.git", alienExtendedTypes)
      installAndAssertCompilationResult(alienExtendedTypes.resolve("alien-base-types-1.0-SNAPSHOT"))
      installAndAssertCompilationResult(alienExtendedTypes.resolve("alien-extended-storage-types-1.0-SNAPSHOT"))
    }
  }

  "Compiler" must {
    "be able to compile alien sample" in {
      val normativeTypesOutput = gitPath.resolve("tosca-normative-types")
      GitClient.clone("https://github.com/alien4cloud/tosca-normative-types.git", normativeTypesOutput)
      Compiler.install(normativeTypesOutput, csarsPath)
      val alienExtendedTypes = gitPath.resolve("alien-extended-types")
      GitClient.clone("https://github.com/alien4cloud/alien4cloud-extended-types.git", alienExtendedTypes)
      Compiler.install(alienExtendedTypes.resolve("alien-base-types-1.0-SNAPSHOT"), csarsPath)
      Compiler.install(alienExtendedTypes.resolve("alien-extended-storage-types-1.0-SNAPSHOT"), csarsPath)
      val sampleTypesOutput = gitPath.resolve("samples")
      GitClient.clone("https://github.com/alien4cloud/samples.git", sampleTypesOutput)
      installAndAssertCompilationResult(sampleTypesOutput.resolve("apache-load-balancer"))
      installAndAssertCompilationResult(sampleTypesOutput.resolve("apache"))
      installAndAssertCompilationResult(sampleTypesOutput.resolve("mongo"))
      installAndAssertCompilationResult(sampleTypesOutput.resolve("mysql"))
      installAndAssertCompilationResult(sampleTypesOutput.resolve("nodejs"))
      installAndAssertCompilationResult(sampleTypesOutput.resolve("nodecellar"))
      installAndAssertCompilationResult(sampleTypesOutput.resolve("php"))
      installAndAssertCompilationResult(sampleTypesOutput.resolve("tomcat-war"))
      installAndAssertCompilationResult(sampleTypesOutput.resolve("wordpress"))
      installAndAssertCompilationResult(sampleTypesOutput.resolve("topology-load-balancer-tomcat"))
      installAndAssertCompilationResult(sampleTypesOutput.resolve("topology-nodecellar"))
      installAndAssertCompilationResult(sampleTypesOutput.resolve("topology-tomcatWar"))
      installAndAssertCompilationResult(sampleTypesOutput.resolve("topology-wordpress"))
    }
  }

  "Compiler" must {
    "be able to assembly alien samples topologies for docker" in {
      val normativeTypesOutput = gitPath.resolve("tosca-normative-types")
      GitClient.clone("https://github.com/alien4cloud/tosca-normative-types.git", normativeTypesOutput)
      Compiler.install(normativeTypesOutput, csarsPath)
      val sampleTypesOutput = gitPath.resolve("samples")
      val alienExtendedTypes = gitPath.resolve("alien-extended-types")
      GitClient.clone("https://github.com/alien4cloud/alien4cloud-extended-types.git", alienExtendedTypes)
      Compiler.install(alienExtendedTypes.resolve("alien-base-types-1.0-SNAPSHOT"), csarsPath)
      Compiler.install(alienExtendedTypes.resolve("alien-extended-storage-types-1.0-SNAPSHOT"), csarsPath)

      GitClient.clone("https://github.com/alien4cloud/samples.git", sampleTypesOutput)

      installAndAssertCompilationResult(sampleTypesOutput.resolve("apache-load-balancer"))
      installAndAssertCompilationResult(sampleTypesOutput.resolve("apache"))
      installAndAssertCompilationResult(sampleTypesOutput.resolve("mongo"))
      installAndAssertCompilationResult(sampleTypesOutput.resolve("mysql"))
      installAndAssertCompilationResult(sampleTypesOutput.resolve("nodejs"))
      installAndAssertCompilationResult(sampleTypesOutput.resolve("nodecellar"))
      installAndAssertCompilationResult(sampleTypesOutput.resolve("php"))
      installAndAssertCompilationResult(sampleTypesOutput.resolve("tomcat-war"))
      installAndAssertCompilationResult(sampleTypesOutput.resolve("wordpress"))
      installAndAssertCompilationResult(sampleTypesOutput.resolve("topology-load-balancer-tomcat"))
      installAndAssertCompilationResult(sampleTypesOutput.resolve("topology-nodecellar"))
      installAndAssertCompilationResult(sampleTypesOutput.resolve("topology-tomcatWar"))
      installAndAssertCompilationResult(sampleTypesOutput.resolve("topology-wordpress"))

      installAndAssertCompilationResult(Paths.get("docker/src/main/resources/docker-provider-types"))

      assemblyDockerTopologyAndAssertCompilationResult("csars/topologyWordpressDocker/")
      assemblyDockerTopologyAndAssertCompilationResult("csars/topologyApacheLoadBalancerDocker/")
      assemblyDockerTopologyAndAssertCompilationResult("csars/topologyNodeCellarDocker/")
    }
  }
}