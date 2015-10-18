package com.mkv.tosca.cli.command

import com.mkv.tosca.cli.parser.Parsers
import com.mkv.util.DockerUtil
import sbt.complete.DefaultParsers._
import sbt.{Command, Help}

import scala.collection.JavaConverters._

/**
 * List all packaged deployments (docker images) available on the docker daemon
 *
 * @author Minh Khang VU
 */
object ListDeploymentCommand {

  private val dockerUrlOpt = "-u"

  private val dockerCertOpt = "-c"

  private lazy val dockerUrlArg = token(dockerUrlOpt) ~ (Space ~> token(URIClass))

  private lazy val dockerCertPathArg = token(dockerCertOpt) ~ (Space ~> token(Parsers.filePathParser))

  private lazy val listDeploymentArgsParser = Space ~> (dockerCertPathArg | dockerUrlArg) +

  private lazy val listDeploymentHelp = Help("deployments", ("deployments", "List all packaged deployments (docker images) available on the docker daemon"),
    """
      |deployments -u <docker daemon url> -c <certificate path>
      |-u   : url of the docker daemon
      |-c   : path to the the certificate to connect to the docker daemon
    """.stripMargin
  )

  lazy val instance = Command("deployments", listDeploymentHelp)(_ => listDeploymentArgsParser) { (state, args) =>
    val argsMap = args.toMap
    var fail = false
    if (!argsMap.contains(dockerUrlOpt)) {
      println(dockerUrlOpt + " are mandatory")
      fail = true
    } else {
      val dockerClient = DockerUtil.buildDockerClient(argsMap(dockerUrlOpt), argsMap.getOrElse(dockerCertOpt, null))
      // TODO How to filter dangling image more efficiently
      val images = dockerClient.listImagesCmd().withFilters("{\"label\":[\"organization=toscaruntime\"]}").exec().asScala
        .filter(image => image.getRepoTags != null && image.getRepoTags.nonEmpty && !image.getRepoTags()(0).equals("<none>:<none>"))
      println("Found " + images.size + " images:")
      images.foreach { image =>
        println(image.getId + "\t" + image.getRepoTags()(0))
      }
      try {
      } finally {
        dockerClient.close()
      }
    }
    if (fail) {
      state.fail
    } else {
      state
    }
  }
}
