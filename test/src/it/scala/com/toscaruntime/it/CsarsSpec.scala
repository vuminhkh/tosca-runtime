package com.toscaruntime.it

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

      Given("I download the sample types from Alien's repository")
      downloadZipFileAndExtract("https://github.com/vuminhkh/samples/archive/master.zip", tempPath)

      When("I install Java type to the repository with 'csars install /path/to/jdk'")
      val compilationResult = installCsar(tempPath.resolve("samples-master").resolve("jdk"))

      Then("I should not have any compilation errors")
      assertNoCompilationErrorsDetected(compilationResult)

      When("I list available CSARS with 'csars list jdk-type'")
      val listNormativeCsarResult = listCsarWithName("jdk-type")

      Then("I should have JDK types CSAR available in the repository")
      assertCsarFound(listNormativeCsarResult, "jdk-type", "1.0.0-SNAPSHOT")

      When("I delete JDK types csar 'csar delete jdk-type:1.0.0-SNAPSHOT'")
      deleteCsar("jdk-type", "1.0.0-SNAPSHOT")

      And("I list available CSARS with 'csars list jdk-type'")
      val listNormativeCsarWithNoResult = listCsarWithName("jdk-type")

      Then("I should not have normative types CSAR available in the repository")
      assertCsarNotFound(listNormativeCsarWithNoResult, "jdk-type", "1.0.0-SNAPSHOT")
    }

    scenario("Install csars with dependencies") {

      Given("I download the sample types from Alien's repository")
      downloadZipFileAndExtract("https://github.com/vuminhkh/samples/archive/master.zip", tempPath)
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("tomcat-war")))

      When("I install topology-load-balancer-tomcat to the repository with 'csars install /path/to/topology-load-balancer-tomcat'")
      val errorResult = installCsar(tempPath.resolve("samples-master").resolve("topology-load-balancer-tomcat"))

      Then("I should have compilation errors as topology-load-balancer-tomcat needs apache-load-balancer")
      assertCompilationErrorsDetected(errorResult)

      When("I install apache-load-balancer to the repository with 'csars install /path/to/apache-load-balancer'")
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("apache-load-balancer")))

      And("I install topology-load-balancer-tomcat to the repository with 'csars install /path/to/topology-load-balancer-tomcat'")
      val successfulResult = installCsar(tempPath.resolve("samples-master").resolve("topology-load-balancer-tomcat"))

      Then("I should not have any compilation errors as normative types were installed")
      assertNoCompilationErrorsDetected(successfulResult)
    }
  }
}
