package com.toscaruntime.cli.command

import com.toscaruntime.cli.Attributes
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

  private val deleteOpt = "delete"

  private val cleanOpt = "clean"

  private lazy val deploymentsArgsParser = Space ~> (token(listOpt) | (token(deleteOpt) ~ (Space ~> token(StringBasic))) | token(cleanOpt)) +

  private lazy val deploymentActionsHelp = Help("deployments", ("deployments", "Actions on deployments, execute 'help deployments' for more details"),
    """
      |deployments [list | delete <deployment name> | clean]
      |list   : list all deployments
      |delete : delete created deployment
      |clean  : clean up dangling deployments (for administrator purpose)
    """.stripMargin
  )

  lazy val instance = Command("deployments", deploymentActionsHelp)(_ => deploymentsArgsParser) { (state, args) =>

    val client = state.attributes.get(Attributes.clientAttribute).get
    args.head match {
      case "list" =>
        val images = client.listImages()
        println("Found " + images.size + " deployments:")
        images.foreach { image =>
          println(image.getContainerConfig.getLabels.get(RuntimeConstant.DEPLOYMENT_ID_LABEL) + "\t\t" + image.getCreated + "\t\t" + image.getId)
        }
      case "clean" =>
        client.cleanDanglingImages()
        println("Cleaned all dangling images")
      case ("delete", deploymentId: String) =>
        client.deleteImage(deploymentId)
        println("Deleted " + deploymentId)
    }
    state
  }
}
