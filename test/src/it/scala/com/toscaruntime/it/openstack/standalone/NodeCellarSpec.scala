package com.toscaruntime.it.openstack.standalone

import com.toscaruntime.it.AbstractSpec
import com.toscaruntime.it.TestConstant._
import com.toscaruntime.it.steps.AgentsSteps._
import com.toscaruntime.it.steps.CsarsSteps._
import com.toscaruntime.it.steps.DeploymentsSteps._
import com.toscaruntime.it.util.URLChecker._
import org.scalatest.MustMatchers

import scala.concurrent.duration.DurationInt

class NodeCellarSpec extends AbstractSpec with MustMatchers {

  info("Test deployment of a topology nodecellar with openstack in mode masterless")

  feature("Deployment of node cellar") {
    scenario("Standard deployment") {
      Given("I download and install all necessary csars for node cellar deployment")
      installNormativeTypesAndProviders()
      downloadZipFileAndExtract("https://github.com/alien4cloud/samples/archive/master.zip", tempPath)
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("nodejs")))
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("mongo")))
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("nodecellar")))
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("topology-nodecellar")))

      And("A deployment image has been created for the nodecellar openstack topology")
      createDeploymentImage("node-cellar", openstackProvider) must be(true)

      When("I deploy it")
      launchDeployment("node-cellar")

      Then("I should have an output for the nodecellar's public url")
      val url = assertDeploymentHasOutput("node-cellar", "Nodecellar_nodecellar_url")

      And("A request on the application's url should return a response 200 OK")
      checkURL(url, 200, Set.empty, 5 minutes)

      And("I should be able to undeploy it without error")
      launchUndeployment("node-cellar")
    }
  }
}
