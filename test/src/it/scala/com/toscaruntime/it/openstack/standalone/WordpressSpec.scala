package com.toscaruntime.it.openstack.standalone

import com.toscaruntime.it.AbstractSpec
import com.toscaruntime.it.TestConstant._
import com.toscaruntime.it.steps.AgentsSteps._
import com.toscaruntime.it.steps.CsarsSteps._
import com.toscaruntime.it.steps.DeploymentsSteps._
import com.toscaruntime.it.util.URLChecker._
import org.scalatest.MustMatchers

import scala.concurrent.duration.DurationInt

class WordpressSpec extends AbstractSpec with MustMatchers {

  info("Test deployment of a topology wordpress with openstack in mode masterless")

  feature("Deployment of wordpress") {
    scenario("Standard deployment") {
      Given("I download and install all necessary csars for wordpress deployment")
      installNormativeTypesAndProviders()
      downloadZipFileAndExtract("https://github.com/alien4cloud/samples/archive/master.zip", tempPath)
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("apache")))
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("mysql")))
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("php")))
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("wordpress")))
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("topology-wordpress")))

      And("A deployment image has been created for the wordpress openstack topology")
      createDeploymentImage("wordpress", openstackProvider) must be(true)

      When("I deploy it")
      launchDeployment("wordpress")

      Then("I should have an output for the wordpress's public url")
      val url = assertDeploymentHasOutput("wordpress", "wordpress_url")

      And("A request on the application's url should return a response 200 OK")
      checkURL(url, 200, Set.empty, None, 5 minutes)

      And("I should be able to undeploy it without error")
      launchUndeployment("wordpress")
    }
  }
}
