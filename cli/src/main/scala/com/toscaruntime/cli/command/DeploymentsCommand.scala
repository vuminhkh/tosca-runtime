package com.toscaruntime.cli.command

import com.toscaruntime.cli.Attributes
import sbt.complete.DefaultParsers._
import sbt.{Command, Help}

import scala.collection.JavaConverters._

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

    val dockerClient = state.attributes.get(Attributes.dockerDaemonAttribute).get.dockerClient
    args.head match {
      case "list" =>
        // TODO How to filter dangling image more efficiently
        val images = dockerClient.listImagesCmd().withFilters("{\"label\":[\"organization=toscaruntime\"]}").exec().asScala
          .filter(image => image.getRepoTags != null && image.getRepoTags.nonEmpty && !image.getRepoTags()(0).equals("<none>:<none>"))
        println("Found " + images.size + " deployments:")
        images.foreach { image =>
          println(image.getRepoTags()(0) + "\t" + image.getId)
        }
      case "clean" =>
        val images = dockerClient.listImagesCmd.withFilters("{\"dangling\":[\"true\"]}").withShowAll(true).exec.asScala
        println("Found " + images.size + " dangling images, cleaning them all")
        images.foreach { image =>
          dockerClient.removeImageCmd(image.getId).exec()
          println(image.getId)
        }
      case ("delete", deploymentName: String) =>
        dockerClient.removeImageCmd(deploymentName).exec()
        println(deploymentName)
    }
    state
  }
}
