package com.toscaruntime.it.docker.bootstrap

import com.toscaruntime.cli.command.DaemonCommand
import com.toscaruntime.it.TestConstant._
import com.toscaruntime.it.steps.AgentsSteps._
import com.toscaruntime.it.steps.CsarsSteps._
import com.toscaruntime.it.steps.DeploymentsSteps
import com.toscaruntime.it.steps.DeploymentsSteps._
import com.toscaruntime.it.util.URLChecker._
import com.toscaruntime.it.{AbstractSpec, Context}
import com.toscaruntime.util.DockerDaemonConfig
import org.scalatest.MustMatchers

import scala.concurrent.duration.DurationInt

class WordpressSpec extends AbstractSpec with MustMatchers {

  info("Test deployment of a topology wordpress with docker in mode bootstrap")

  feature("Deployment of wordpress with boostrap") {
    scenario("Standard deployment") {
      Given("I download and install all necessary csars for wordpress deployment")
      downloadZipFileAndExtract("https://github.com/vuminhkh/samples/archive/master.zip", tempPath)
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("apache")))
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("mysql")))
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("php")))
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("wordpress")))
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("samples-master").resolve("topology-wordpress")))

      And("I bootstrap a swarm cluster on openstack")
      val daemonURL = DeploymentsSteps.bootstrap()

      And("I use this new daemon url to manage deployments")
      DaemonCommand.switchConnection(Context.client, new DockerDaemonConfig(daemonURL, null, null), testConfigPath)

      And("A deployment image has been created for the wordpress docker topology")
      createDeploymentImage("wordpress") must be(true)

      When("I deploy it")
      launchDeployment("wordpress")

      Then("I should have an output for the wordpress's public url")
      val url = assertDeploymentHasOutput("wordpress", "wordpress_url")

      And("A request on the application's url should return a response 200 OK")
      checkURL(url, 200, Set.empty, 5 minutes)

      And("I should be able to undeploy it without error")
      launchUndeployment("wordpress")

      And("I should be able to switch to the local connection")
      DaemonCommand.switchConnection(Context.client, Context.dockerConfig, testConfigPath)

      And("I should be able to teardown the bootstrap")
      teardown()
    }
  }
}
