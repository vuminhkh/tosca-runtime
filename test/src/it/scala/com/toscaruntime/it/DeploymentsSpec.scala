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
      installNormativeTypesAndProviders()
      downloadZipFileAndExtract("https://github.com/alien4cloud/samples/archive/master.zip", tempPath)
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("apache")))
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("mysql")))
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("php")))
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("wordpress")))

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
