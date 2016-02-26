package com.toscaruntime.it.docker.standalone

import com.toscaruntime.it.AbstractSpec
import com.toscaruntime.it.TestConstant._
import com.toscaruntime.it.steps.AgentsSteps._
import com.toscaruntime.it.steps.CsarsSteps._
import com.toscaruntime.it.steps.DeploymentsSteps._
import com.toscaruntime.it.util.URLChecker._
import org.scalatest.MustMatchers

import scala.concurrent.duration.DurationInt

class DemoLifeCycleSpec extends AbstractSpec with MustMatchers {

  info("Test deployment of the topology life-cycle demo in mode masterless")

  feature("Deployment of life-cycle demo app") {
    scenario("Standard deployment") {
      Given("I download and install all necessary csars for lifecycle demo web app deployment")
      installNormativeTypesAndProviders()
      downloadZipFileAndExtract("https://github.com/alien4cloud/samples/archive/master.zip", tempPath)
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("apache")))
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("php")))
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("demo-lifecycle")))

      And("A deployment image has been created for the life-cycle demo docker topology")
      createDeploymentImage("life-cycle") must be(true)

      When("I deploy it")
      launchDeployment("life-cycle")

      Then("I should have an output for the load balancer's public url")
      val url = assertDeploymentHasOutput("life-cycle", "registry_url")

      And("A request on the application's url should return a response 200 OK")
      checkURL(url, 200, Set.empty, 5 minutes)

      When("I scale up the node ComputeA of this deployment to 2 instances")
      scale("life-cycle", "ComputeA", 2)

      Then("The deployment should contains 2 instances of node ComputeA in state started")
      assertDeploymentHasNode("life-cycle", "ComputeA", 2)

      And("The deployment should contains 2 instances of node RegistryConfigurerA in state started")
      assertDeploymentHasNode("life-cycle", "RegistryConfigurerA", 2)

      And("The deployment should contains 2 instances of node GenericHostA in state started")
      assertDeploymentHasNode("life-cycle", "GenericHostA", 2)

      And("The deployment should contains 2 instances of node GenericA in state started")
      assertDeploymentHasNode("life-cycle", "GenericA", 2)

      When("I scale down the node ComputeA of this deployment to 1 instances")
      scale("life-cycle", "ComputeA", 1)

      Then("The deployment should contains 1 instances of node ComputeA in state started")
      assertDeploymentHasNode("life-cycle", "ComputeA", 1)

      And("The deployment should contains 1 instances of node RegistryConfigurerA in state started")
      assertDeploymentHasNode("life-cycle", "RegistryConfigurerA", 1)

      And("The deployment should contains 1 instances of node GenericHostA in state started")
      assertDeploymentHasNode("life-cycle", "GenericHostA", 1)

      And("The deployment should contains 1 instances of node GenericA in state started")
      assertDeploymentHasNode("life-cycle", "GenericA", 1)

      // Test scale up/down for computeB
      When("I scale up the node ComputeB of this deployment to 2 instances")
      scale("life-cycle", "ComputeB", 2)

      Then("The deployment should contains 2 instances of node ComputeB in state started")
      assertDeploymentHasNode("life-cycle", "ComputeB", 2)

      And("The deployment should contains 2 instances of node RegistryConfigurerA in state started")
      assertDeploymentHasNode("life-cycle", "RegistryConfigurerB", 2)

      And("The deployment should contains 2 instances of node GenericHostA in state started")
      assertDeploymentHasNode("life-cycle", "GenericHostB", 2)

      And("The deployment should contains 2 instances of node GenericA in state started")
      assertDeploymentHasNode("life-cycle", "GenericB", 2)

      When("I scale down the node ComputeB of this deployment to 1 instances")
      scale("life-cycle", "ComputeB", 1)

      Then("The deployment should contains 1 instances of node ComputeB in state started")
      assertDeploymentHasNode("life-cycle", "ComputeB", 1)

      And("The deployment should contains 1 instances of node RegistryConfigurerA in state started")
      assertDeploymentHasNode("life-cycle", "RegistryConfigurerB", 1)

      And("The deployment should contains 1 instances of node GenericHostA in state started")
      assertDeploymentHasNode("life-cycle", "GenericHostB", 1)

      And("The deployment should contains 1 instances of node GenericA in state started")
      assertDeploymentHasNode("life-cycle", "GenericB", 1)

      And("I should be able to undeploy it without error")
      launchUndeployment("life-cycle")
    }
  }
}
