package com.toscaruntime.it

import java.nio.file.Paths

import com.toscaruntime.it.TestConstant._
import com.toscaruntime.it.steps.CsarsSteps._

/**
  * Compilation and dependency management specs
  *
  * @author Minh Khang VU
  */
class CsarsSpec extends AbstractSpec {

  info("As a user I want to be able to compile a csar and install it to the local repository")
  info("The Csars may have dependencies between them and ToscaRuntime must be able to handle it")

  feature("Compile and install CSARs to repository") {

    scenario("Install / list / delete csar") {

      Given("I download the normative type from Alien's repository")
      downloadZipFileAndExtract("https://github.com/alien4cloud/tosca-normative-types/archive/master.zip", tempPath)

      When("I install tosca normative types to the repository with 'csars install /path/to/normative-types'")
      val compilationResult = installCsar(tempPath.resolve("tosca-normative-types-master"))

      Then("I should not have any compilation errors")
      assertNoCompilationErrorsDetected(compilationResult)

      When("I list available CSARS with 'csars list tosca-normative-types'")
      val listNormativeCsarResult = listCsarWithName("tosca-normative-types")

      Then("I should have normative types CSAR available in the repository")
      assertCsarFound(listNormativeCsarResult, "tosca-normative-types", "1.0.0-ALIEN11")

      When("I delete normative types csar 'csar delete tosca-normative-types:1.0.0-ALIEN11'")
      deleteCsar("tosca-normative-types", "1.0.0-ALIEN11")

      And("I list available CSARS with 'csars list tosca-normative-types'")
      val listNormativeCsarWithNoResult = listCsarWithName("tosca-normative-types")

      Then("I should not have normative types CSAR available in the repository")
      assertCsarNotFound(listNormativeCsarWithNoResult, "tosca-normative-types", "1.0.0-ALIEN11")
    }

    scenario("Install csars with dependencies") {

      Given("I download alien base types from Alien's repository")
      downloadZipFileAndExtract("https://github.com/alien4cloud/alien4cloud-extended-types/archive/master.zip", tempPath)

      When("I install alien base types to the repository with 'csars install /path/to/alien-base-types'")
      val errorResult = installCsar(tempPath.resolve("alien4cloud-extended-types-master").resolve("alien-base-types-1.0-SNAPSHOT"))

      Then("I should have compilation errors as alien base types need normative types")
      assertCompilationErrorsDetected(errorResult)

      When("I download the normative type from Alien's repository")
      downloadZipFileAndExtract("https://github.com/alien4cloud/tosca-normative-types/archive/master.zip", tempPath)

      And("I install tosca normative types to the repository with 'csars install /path/to/normative-types'")
      installCsar(tempPath.resolve("tosca-normative-types-master"))

      And("I install alien base types to the repository with 'csars install /path/to/alien-base-types'")
      val successfulResult = installCsar(tempPath.resolve("alien4cloud-extended-types-master").resolve("alien-base-types-1.0-SNAPSHOT"))

      Then("I should not have any compilation errors as normative types were installed")
      assertNoCompilationErrorsDetected(successfulResult)
    }
  }
}
