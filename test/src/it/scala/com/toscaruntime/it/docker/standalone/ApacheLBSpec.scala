package com.toscaruntime.it.docker.standalone

import com.toscaruntime.it.AbstractSpec
import com.toscaruntime.it.TestConstant._
import com.toscaruntime.it.steps.AgentsSteps._
import com.toscaruntime.it.steps.CsarsSteps._
import com.toscaruntime.it.steps.DeploymentsSteps._
import com.toscaruntime.it.util.URLChecker._
import org.scalatest.MustMatchers

import scala.concurrent.duration.DurationInt

class ApacheLBSpec extends AbstractSpec with MustMatchers {

  info("Test deployment of a topology apache load balancer with docker in mode masterless")

  feature("Deployment of a load balanced web app") {
    scenario("Standard deployment") {
      Given("I download and install all necessary csars for apache load balancer web app deployment")
      installNormativeTypesAndProviders()
      downloadZipFileAndExtract("https://github.com/alien4cloud/samples/archive/master.zip", tempPath)
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("apache-load-balancer")))
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("tomcat-war")))
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("topology-load-balancer-tomcat")))

      And("A deployment image has been created for the tomcat with apache load balancer docker topology")
      createDeploymentImage("apache-lb") must be(true)

      When("I deploy it")
      launchDeployment("apache-lb")

      Then("I should have an output for the load balancer's public url")
      val url = assertDeploymentHasOutput("apache-lb", "load_balancer_url")

      And("A request on the application's url should return a response 200 OK")
      checkURL(url, 200, Set.empty, None, 5 minutes)

      When("I scale up the node WebServer of this deployment to 2 instances")
      scale("apache-lb", "WebServer", 2)

      Then("The deployment should contains 2 instances of node WebServer in state started")
      assertDeploymentHasNode("apache-lb", "WebServer", 2)

      Then("The deployment should contains 2 instances of node Java in state started")
      assertDeploymentHasNode("apache-lb", "Java", 2)

      Then("The deployment should contains 2 instances of node Tomcat in state started")
      assertDeploymentHasNode("apache-lb", "Tomcat", 2)

      Then("The deployment should contains 2 instances of node War in state started")
      assertDeploymentHasNode("apache-lb", "War", 2)

      When("I scale down the node WebServer of this deployment to 1 instances")
      scale("apache-lb", "WebServer", 1)

      Then("The deployment should contains 1 instances of node WebServer in state started")
      assertDeploymentHasNode("apache-lb", "WebServer", 1)

      Then("The deployment should contains 1 instances of node Java in state started")
      assertDeploymentHasNode("apache-lb", "Java", 1)

      Then("The deployment should contains 1 instances of node Tomcat in state started")
      assertDeploymentHasNode("apache-lb", "Tomcat", 1)

      Then("The deployment should contains 1 instances of node War in state started")
      assertDeploymentHasNode("apache-lb", "War", 1)

      And("I should be able to undeploy it without error")
      launchUndeployment("apache-lb")
    }
  }
}