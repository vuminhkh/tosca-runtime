package com.toscaruntime.it

import com.toscaruntime.it.TestConstant._
import com.toscaruntime.it.steps.CsarsSteps._
import com.toscaruntime.it.steps.DeploymentsSteps._
import org.scalatest.MustMatchers

/**
  * Deployment images specs
  *
  * @author Minh Khang VU
  */
class DeploymentsSpec extends AbstractSpec with MustMatchers {

  info("As a user I want to be able to create/ list / delete deployment images")

  feature("Create/ list / delete deployment images") {

    scenario("Wordpress") {

      Given("I download and install all necessary types for wordpress deployment")
      downloadZipFileAndExtract("https://github.com/alien4cloud/tosca-normative-types/archive/master.zip", tempPath)
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("tosca-normative-types-master")))
      downloadZipFileAndExtract("https://github.com/alien4cloud/alien4cloud-extended-types/archive/master.zip", tempPath)
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("alien4cloud-extended-types-master").resolve("alien-base-types-1.0-SNAPSHOT")))
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("alien4cloud-extended-types-master").resolve("alien-extended-storage-types-1.0-SNAPSHOT")))
      downloadZipFileAndExtract("https://github.com/alien4cloud/samples/archive/master.zip", tempPath)
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("apache")))
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("mysql")))
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("php")))
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("wordpress")))
      installProvider(docker)

      And("I install the abstract wordpress topology")
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("topology-wordpress")))

      When("I create deployment image for the wordpress docker topology")
      val success = createDeploymentImage("wordpress")

      Then("It should succeed")
      success must be(true)

      When("I list deployment images")
      val deploymentImageList = listDeploymentImages()

      Then("The result should contain wordpress")
      assertDeploymentImageListContain(deploymentImageList, "wordpress")

      When("I delete the deployment image")
      deleteDeploymentImage("wordpress")

      And("I list deployment images")
      val deploymentImageListAfterDeletion = listDeploymentImages()

      Then("The result should not contain wordpress")
      assertDeploymentImageListNotContain(deploymentImageListAfterDeletion, "wordpress")
    }
  }

}
