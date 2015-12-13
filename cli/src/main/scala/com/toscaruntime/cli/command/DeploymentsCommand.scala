package com.toscaruntime.cli.command

import com.toscaruntime.cli.Attributes
import com.toscaruntime.cli.util.{DeployUtil, TabulatorUtil}
import com.toscaruntime.constant.RuntimeConstant
import sbt.complete.DefaultParsers._
import sbt.{Command, Help}

/**
  * List all packaged deployments (docker images) available on the docker daemon
  *
  * @author Minh Khang VU
  */
object DeploymentsCommand {

  private val listOpt = "list"

  private val runOpt = "run"

  private val deleteOpt = "delete"

  private val cleanOpt = "clean"

  private lazy val deploymentsArgsParser = Space ~> ((token(runOpt) ~ (Space ~> token(StringBasic))) | token(listOpt) | (token(deleteOpt) ~ (Space ~> token(StringBasic))) | token(cleanOpt)) +

  private lazy val deploymentActionsHelp = Help("deployments", ("deployments", "Actions on deployments, execute 'help deployments' for more details"),
    """
      |deployments [list | [delete|run] <deployment name> | clean]
      |list   : list all deployments
      |run    : create agent for the deployment and deploy it
      |delete : delete created deployment
      |clean  : clean up dangling deployments docker image (for administration purpose, to free disk space)
    """.stripMargin
  )

  lazy val instance = Command("deployments", deploymentActionsHelp)(_ => deploymentsArgsParser) { (state, args) =>

    val client = state.attributes.get(Attributes.clientAttribute).get
    args.head match {
      case "list" =>
        val images = client.listDeploymentImages()
        println("Found " + images.size + " deployment image(s):")
        val headers = List("Deployment Id", "Created", "Image Id")
        val imagesData = images.map { image =>
          List(image.getContainerConfig.getLabels.get(RuntimeConstant.DEPLOYMENT_ID_LABEL), image.getCreated, image.getId)
        }
        println(TabulatorUtil.format(headers :: imagesData))
      case "clean" =>
        client.cleanDanglingImages()
        println("Cleaned all dangling images")
      case ("delete", deploymentId: String) =>
        client.deleteDeploymentImage(deploymentId)
        println("Deleted deployment image " + deploymentId)
      case ("run", deploymentId: String) =>
        val containerId = client.createDeploymentAgent(deploymentId).getId
        DeployUtil.waitForDeploymentAgent(client, deploymentId)
        client.deploy(deploymentId)
        println(s"Agent with id $containerId has been created for deployment $deploymentId")
        println("Execute 'agents log " + deploymentId + "' to tail the log of deployment agent")
    }
    state
  }
}
