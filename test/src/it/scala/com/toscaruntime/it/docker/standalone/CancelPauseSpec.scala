package com.toscaruntime.it.docker.standalone

import java.lang.Thread._

import com.toscaruntime.cli.command.AgentsCommand
import com.toscaruntime.it.TestConstant._
import com.toscaruntime.it.steps.AgentsSteps._
import com.toscaruntime.it.steps.CsarsSteps._
import com.toscaruntime.it.steps.DeploymentsSteps._
import com.toscaruntime.it.{AbstractSpec, Context}
import org.scalatest.MustMatchers

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Cancel, pause execution
  */
class CancelPauseSpec extends AbstractSpec with MustMatchers {

  info("As a user I want to be able to cancel / pause with force a running execution")

  feature("Cancel execution with force") {
    scenario("Deploy a freezing application then cancel its deployment process") {
      Given("I download and install all necessary types for this test")
      assertNoCompilationErrorsDetected(installCsar(customTestComponentsPath.resolve("sleepError")))

      And("A deployment image has been created for sleep scenario")
      createDeploymentImage("sleep")

      When("I deploy it asynchronously")
      val finishFuture = AgentsCommand.deploy(Context.client, "sleep")._2

      And("I wait some seconds for the deployment to begin to freeze")
      sleep(15000)

      And("I cancel it with force")
      cancelDeployment("sleep", force = true)

      Then("The deployment should finish immediately")
      Await.result(finishFuture, 5 second)

      And("I should be able to undeploy it without error")
      launchUndeployment("sleep")
    }
  }

  feature("Pause execution with force") {
    scenario("Deploy a freezing application then interrupt its deployment process and update the recipe so it does not freeze anymore") {
      Given("I download and install all necessary types for this test")
      assertNoCompilationErrorsDetected(installCsar(customTestComponentsPath.resolve("sleepError")))

      And("A deployment image has been created for sleep scenario")
      createDeploymentImage("sleep")

      When("I deploy it asynchronously")
      AgentsCommand.deploy(Context.client, "sleep")._2

      And("I wait some seconds for the deployment to begin to freeze")
      sleep(15000)

      And("I pause it with force")
      pauseDeployment("sleep", force = true)

      Then("The deployment should reach status stopped immediately")
      assertDeploymentHasBeenStoppedWithoutError("sleep")

      When("I update the deployment with non freezing topology")
      createDeploymentImage("dont-sleep", dockerProvider, standalone, None, Some("sleep")) must be(true)
      updateRecipe("sleep")
      restartAgent("sleep")

      And("I resume the deployment")
      resumeDeployment("sleep")

      Then("The deployment should finish immediately")
      assertDeploymentFinished("sleep", 5 seconds)

      And("I should be able to undeploy it without error")
      launchUndeployment("sleep")
    }
  }
}
