package com.toscaruntime.it

import com.toscaruntime.it.TestConstant._
import com.toscaruntime.it.steps.AgentsSteps._
import com.toscaruntime.it.steps.CsarsSteps._
import com.toscaruntime.it.steps.DeploymentsSteps._
import org.scalatest.MustMatchers

class AgentsSpec extends AbstractSpec with MustMatchers {

  info("As a user I want to be able to create/ list / delete agents")

  feature("Create/ List / Delete agent and view agent information") {

    scenario("Test with simple deployment") {

      Given("I download and install all necessary types for a simple single compute deployment")
      downloadZipFileAndExtract("https://github.com/vuminhkh/tosca-normative-types/archive/master.zip", tempPath)
      assertNoCompilationErrorsDetected(installCsar(tempPath.resolve("tosca-normative-types-master")))
      installProvider(dockerProvider)

      And("I create deployment image for the docker single compute topology")
      createDeploymentImage("single-compute") must be(true)

      When("I create the agent")
      createAgent("single-compute") must not be empty

      And("I list available agents on the daemon")
      val agentList = listAgents()

      Then("I should have the agent single-compute in status started")
      assertAgentsListContain(agentList, "single-compute", "Up")

      When("I stop the agent")
      stopAgent("single-compute")

      Then("I should have the agent single-compute in status stopped")
      assertAgentsListContain(listAgents(), "single-compute", "Exited")

      When("I start the agent")
      startAgent("single-compute")

      Then("I should have the agent single-compute in status started")
      assertAgentsListContain(listAgents(), "single-compute", "Up")

      When("I launch install workflow on the agent")
      launchInstallWorkflow("single-compute")

      Then("I should have a started node with name 'Server'")
      assertDeploymentHasNode("single-compute", "Server", 1)

      And("I should have an established relationship from 'Software' to 'Server'")
      assertDeploymentHasRelationship("single-compute", "Software", "Server", 1)

      And("I should have an output for the compute's public ip address")
      assertDeploymentHasOutput("single-compute", "compute_public_ip_address")

      When("I launch undeploy workflow on the agent")
      launchUninstallWorkflow("single-compute")

      Then("I should have a node with name 'Server' with no instance")
      assertDeploymentHasNode("single-compute", "Server", 0)

      And("I should have a relationship from 'Software' to 'Server' with no instance")
      assertDeploymentHasRelationship("single-compute", "Software", "Server", 0)

      When("I delete the agent")
      deleteAgent("single-compute")

      Then("I should not have the agent in the agent list any more")
      assertAgentsListNotContain(listAgents(), "single-compute")
    }
  }
}
