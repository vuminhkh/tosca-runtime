package com.toscaruntime.compiler

import _root_.tosca.nodes.Root
import com.toscaruntime.compiler.tosca.ParsedValue
import com.toscaruntime.util.GitClient

class CompilerSpec extends AbstractSpec {

  "Compiler" must {
    "be able to compile alien normative types" in {
      val normativeTypesOutput = TestConstant.GIT_TEST_DATA_PATH.resolve("tosca-normative-types")
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
    "be able to compile alien extended types" in {
      val normativeTypesOutput = TestConstant.GIT_TEST_DATA_PATH.resolve("tosca-normative-types")
      GitClient.clone("https://github.com/alien4cloud/tosca-normative-types.git", normativeTypesOutput)
      Compiler.install(normativeTypesOutput, TestConstant.CSAR_REPOSITORY_PATH)
      val alienExtendedTypes = TestConstant.GIT_TEST_DATA_PATH.resolve("alien-extended-types")
      GitClient.clone("https://github.com/alien4cloud/alien4cloud-extended-types.git", alienExtendedTypes)
      installAndAssertCompilationResult(alienExtendedTypes.resolve("alien-base-types-1.0-SNAPSHOT"))
      installAndAssertCompilationResult(alienExtendedTypes.resolve("alien-extended-storage-types-1.0-SNAPSHOT"))
    }
  }

  "Compiler" must {
    "be able to compile alien sample types" in {
      val normativeTypesOutput = TestConstant.GIT_TEST_DATA_PATH.resolve("tosca-normative-types")
      GitClient.clone("https://github.com/alien4cloud/tosca-normative-types.git", normativeTypesOutput)
      Compiler.install(normativeTypesOutput, TestConstant.CSAR_REPOSITORY_PATH)
      val alienExtendedTypes = TestConstant.GIT_TEST_DATA_PATH.resolve("alien-extended-types")
      GitClient.clone("https://github.com/alien4cloud/alien4cloud-extended-types.git", alienExtendedTypes)
      Compiler.install(alienExtendedTypes.resolve("alien-base-types-1.0-SNAPSHOT"), TestConstant.CSAR_REPOSITORY_PATH)
      Compiler.install(alienExtendedTypes.resolve("alien-extended-storage-types-1.0-SNAPSHOT"), TestConstant.CSAR_REPOSITORY_PATH)
      val sampleTypesOutput = TestConstant.GIT_TEST_DATA_PATH.resolve("samples")
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
    }
  }
}