package com.toscaruntime.it

import java.nio.file.Paths

import com.toscaruntime.exception.client.WorkflowExecutionFailureException
import com.toscaruntime.it.steps.CsarsSteps._
import org.scalatest.MustMatchers
import com.toscaruntime.it.TestConstant._
import com.toscaruntime.it.steps.AgentsSteps._
import com.toscaruntime.it.steps.DeploymentsSteps._

/**
  * Resume from error
  *
  * @author Minh Khang VU
  */
class ErrorResumeSpec extends AbstractSpec with MustMatchers {

  info("As a user I want to be able to develop / deploy my recipe in an incremental manner")

  feature("Incremental deployment: Update recipe, resume execution when error happens") {

    scenario("Error resume handling") {
      Given("I download and install all necessary types for this test")
      installNormativeTypesAndProviders()
      assertNoCompilationErrorsDetected(installCsar(customTestComponentsPath.resolve("sleepError")))

      And("A deployment image has been created for the error docker topology with bad input")
      createDeploymentImage("error", dockerProvider, standalone, Some(inputsPath.resolve(dockerProvider).resolve(standalone).resolve("error").resolve("bad_input.yaml"))) must be(true)

      When("I deploy it then it will fail")
      intercept[WorkflowExecutionFailureException](launchDeployment("error"))

      And("The node Error is in state starting")
      assertDeploymentHasNode("error", "Error", 1, NODE_STARTING)

      When("I resume the deployment")
      resumeDeployment("error")

      Then("The deployment will continue to fail")
      assertDeploymentHasBeenStoppedWithError("error")

      And("The node Error is in state starting")
      assertDeploymentHasNode("error", "Error", 1, NODE_STARTING)

      When("I update the deployment with less bad input")
      createDeploymentImage("error", dockerProvider, standalone, Some(inputsPath.resolve(dockerProvider).resolve(standalone).resolve("error").resolve("less_bad_input.yaml"))) must be(true)
      updateRecipe("error")
      restartAgent("error")

      And("I resume the deployment")
      resumeDeployment("error")

      Then("The deployment will continue to fail")
      assertDeploymentHasBeenStoppedWithError("error")

      And("The node Error is in state started")
      assertDeploymentHasNode("error", "Error", 1, NODE_STARTED)

      And("The relationship Error is in state establishing")
      assertDeploymentHasRelationship("error", "SourceConflict", "TargetConflict", 1, RELATIONSHIP_ESTABLISHING)

      When("I update the deployment with good input")
      createDeploymentImage("error", dockerProvider, standalone, Some(inputsPath.resolve(dockerProvider).resolve(standalone).resolve("error").resolve("good_input.yaml"))) must be(true)
      updateRecipe("error")
      restartAgent("error")

      And("I resume the deployment")
      resumeDeployment("error")

      Then("The deployment will finish")
      assertDeploymentSuccess("error")

      And("The relationship Error is in state established")
      assertDeploymentHasRelationship("error", "SourceConflict", "TargetConflict", 1, RELATIONSHIP_ESTABLISHED)

      And("I should be able to undeploy it without error")
      launchUndeployment("error")
    }
  }
}
