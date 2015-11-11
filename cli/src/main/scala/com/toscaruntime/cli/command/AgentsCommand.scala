package com.toscaruntime.cli.command

import com.github.dockerjava.api.model.Filters
import com.toscaruntime.cli.Attributes
import com.toscaruntime.cli.util.DeployUtil
import sbt.complete.DefaultParsers._
import sbt.{Command, Help}

import scala.collection.JavaConverters._

/**
 * Command to handle list, delete, show information of an agent
 * @author Minh Khang VU
 */
object AgentsCommand {

  private val listOpt = "list"

  private val stopOpt = "stop"

  private val deleteOpt = "delete"

  private val deployOpt = "deploy"

  private val undeployOpt = "undeploy"

  private val infoOpt = "info"

  private lazy val agentsArgsParser = Space ~>
    (token(listOpt) |
      (token(stopOpt) ~ (Space ~> token(StringBasic))) |
      (token(deleteOpt) ~ (Space ~> token(StringBasic))) |
      (token(deployOpt) ~ (Space ~> token(StringBasic))) |
      (token(undeployOpt) ~ (Space ~> token(StringBasic))) |
      (token(infoOpt) ~ (Space ~> token(StringBasic)))) +

  private lazy val agentsActionsHelp = Help("agents", ("agents", "List, stop or delete agent, execute 'help agents' for more details"),
    """
      |agents [list | [stop|delete|deploy|undeploy|info] <agent name>]
      |list     : list all agents
      |stop     : stop agent, agent will not manage deployment anymore
      |deploy   : launch default deployment workflow
      |undeploy : launch default undeployment workflow
      |delete   : delete agent
    """.stripMargin
  )

  lazy val instance = Command("agents", agentsActionsHelp)(_ => agentsArgsParser) { (state, args) =>
    val dockerClient = state.attributes.get(Attributes.dockerDaemonAttribute).get.dockerClient
    args.head match {
      case "list" =>
        val filters = new Filters().withLabels("organization=toscaruntime")
        val containers = dockerClient.listContainersCmd().withShowAll(true).withFilters(filters).exec().asScala
        println("Found " + containers.size + " agents:")
        containers.foreach { container =>
          println(container.getNames.head + "\t" + container.getId + "\t" + container.getLabels.get("agentType") + "\t" + container.getStatus)
        }
      case ("info", agentName: String) =>
        DeployUtil.getDetails(dockerClient, agentName)
        println("Follow deployment with 'log -c '" + agentName + "'")
      case ("deploy", agentName: String) =>
        DeployUtil.deploy(dockerClient, agentName)
        println("Follow deployment with 'log -c '" + agentName + "'")
      case ("undeploy", agentName: String) =>
        DeployUtil.undeploy(dockerClient, agentName)
        println("Follow undeployment with 'log -c '" + agentName + "'")
      case ("stop", agentName: String) =>
        dockerClient.stopContainerCmd(agentName).exec()
        println("Stopped " + agentName)
      case ("delete", agentName: String) =>
        dockerClient.removeContainerCmd(agentName).exec()
        println("Deleted " + agentName)
    }
    state
  }
}
