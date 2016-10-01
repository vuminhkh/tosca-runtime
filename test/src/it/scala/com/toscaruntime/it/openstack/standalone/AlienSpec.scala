package com.toscaruntime.it.openstack.standalone

import com.toscaruntime.it.AbstractSpec
import com.toscaruntime.it.TestConstant._
import com.toscaruntime.it.steps.AgentsSteps._
import com.toscaruntime.it.steps.CsarsSteps._
import com.toscaruntime.it.steps.DeploymentsSteps._
import com.toscaruntime.it.util.URLChecker._
import org.scalatest.MustMatchers

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

class AlienSpec extends AbstractSpec with MustMatchers {

  info("Test deployment of a topology Alien in mode masterless")
  feature("Deployment of Alien web app") {
    scenario("Standard deployment") {
      Given("I download and install all necessary csars for Alien web app deployment")
      downloadZipFileAndExtract("https://github.com/vuminhkh/samples/archive/master.zip", tempPath)
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("jdk")))
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("elasticsearch")))
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("alien")))
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("topology-alien4cloud-cluster")))

      And("A deployment image has been created for the alien openstack topology")
      createDeploymentImage("alien4cloud", openstackProvider) must be(true)

      When("I deploy it")
      launchDeployment("alien4cloud")

      Then("I should have an output for the alien's public url")
      val url = assertDeploymentHasOutput("alien4cloud", "alien_url")

      And("A request on the application's url should return a response 200 OK")
      checkURL(url, 200, Set.empty, 5 minutes)

      And("I should be able to undeploy it without error")
      launchUndeployment("alien4cloud")
    }
  }
}
