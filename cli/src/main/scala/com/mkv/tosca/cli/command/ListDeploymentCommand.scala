package com.mkv.tosca.cli.command

import com.mkv.tosca.cli.Attributes
import sbt.{Command, Help}

import scala.collection.JavaConverters._

/**
 * List all packaged deployments (docker images) available on the docker daemon
 *
 * @author Minh Khang VU
 */
object ListDeploymentCommand {

  private lazy val listDeploymentHelp = Help("deployments", ("deployments", "List all packaged deployments (docker images) available on the docker daemon"), "")

  lazy val instance = Command.command("deployments", listDeploymentHelp) { state =>
    val dockerClient = state.attributes.get(Attributes.dockerDaemonAttribute).get
    // TODO How to filter dangling image more efficiently
    val images = dockerClient.listImagesCmd().withFilters("{\"label\":[\"organization=toscaruntime\"]}").exec().asScala
      .filter(image => image.getRepoTags != null && image.getRepoTags.nonEmpty && !image.getRepoTags()(0).equals("<none>:<none>"))
    println("Found " + images.size + " images:")
    images.foreach { image =>
      println(image.getId + "\t" + image.getRepoTags()(0))
    }
    state
  }
}
