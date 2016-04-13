package com.toscaruntime.it.docker.standalone

import com.toscaruntime.it.AbstractSpec
import com.toscaruntime.it.TestConstant._
import com.toscaruntime.it.steps.AgentsSteps._
import com.toscaruntime.it.steps.CsarsSteps._
import com.toscaruntime.it.steps.DeploymentsSteps._
import com.toscaruntime.it.util.URLChecker._
import org.scalatest.MustMatchers

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

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
      checkURL(url, 200, Set.empty, 5 minutes)

      When("I disconnect the war from the load balancer")
      executeRelationshipOperation("apache-lb", Some("War"), Some("ApacheLoadBalancer"), "alien.relationships.WebApplicationConnectsToApacheLoadBalancer", "remove_source")

      Then("A request on the application's url should return a response 503 service unavailable")
      checkURL(url, 503, Set.empty, 5 minutes)

      When("I reconnect the war to the load balancer")
      executeRelationshipOperation("apache-lb", None, None, "alien.relationships.WebApplicationConnectsToApacheLoadBalancer", "add_source", Some("War_1_1_1"), Some("ApacheLoadBalancer_1_1"))

      Then("A request on the application's url should return a response 200 OK")
      checkURL(url, 200, Set.empty, 5 minutes)

      When("I stop the load balancer")
      executeNodeOperation("apache-lb", Some("ApacheLoadBalancer"), "stop")

      Then("The url should not be reachable anymore")
      checkURLNonAvailable(url)

      When("I start the load balancer")
      executeNodeOperation("apache-lb", None, "start", Some("ApacheLoadBalancer_1_1"))

      Then("A request on the application's url should return a response 200 OK")
      checkURL(url, 200, Set.empty, 5 minutes)

      When("I stop the manager daemon to test container state persistence")
      stopAgent("apache-lb")

      And("I start the manager daemon")
      startAgent("apache-lb")

      And("I scale up the node WebServer of this deployment to 2 instances")
      scale("apache-lb", "WebServer", 2)

      Then("The deployment should contains 2 instances of node WebServer in state started")
      assertDeploymentHasNode("apache-lb", "WebServer", 2)

      Then("The deployment should contains 2 instances of node Java in state started")
      assertDeploymentHasNode("apache-lb", "Java", 2)

      And("The deployment should contains 2 instances of node Tomcat in state started")
      assertDeploymentHasNode("apache-lb", "Tomcat", 2)

      And("The deployment should contains 2 instances of node War in state started")
      assertDeploymentHasNode("apache-lb", "War", 2)

      And("A request on the application's url should return a response 200 OK")
      checkURL(url, 200, Set.empty, 5 minutes)

      When("I stop the manager daemon to test container state persistence")
      stopAgent("apache-lb")

      And("I start the manager daemon")
      startAgent("apache-lb")

      And("I scale down the node WebServer of this deployment to 1 instances")
      scale("apache-lb", "WebServer", 1)

      Then("The deployment should contains 1 instances of node WebServer in state started")
      assertDeploymentHasNode("apache-lb", "WebServer", 1)

      And("The deployment should contains 1 instances of node Java in state started")
      assertDeploymentHasNode("apache-lb", "Java", 1)

      And("The deployment should contains 1 instances of node Tomcat in state started")
      assertDeploymentHasNode("apache-lb", "Tomcat", 1)

      And("The deployment should contains 1 instances of node War in state started")
      assertDeploymentHasNode("apache-lb", "War", 1)

      And("A request on the application's url should return a response 200 OK with the expected content")
      checkURL(url, 200, Set.empty, 5 minutes, Some("Welcome to Fastconnect !"))

      When("I update the deployed war file")
      executeNodeOperation("apache-lb", Some("War"), "update_war_file", None, Some("custom"), Map("WAR_URL" -> "https://github.com/alien4cloud/alien4cloud-provider-int-test/raw/develop/src/test/resources/data/helloWorld.war"))

      And("A request on the application's url should return a response 200 OK with the updated content")
      checkURL(url, 200, Set.empty, 5 minutes, Some("Welcome to testDeployArtifactOverriddenTest !"))

      And("I should be able to undeploy it without error")
      launchUndeployment("apache-lb")
    }
  }
}
