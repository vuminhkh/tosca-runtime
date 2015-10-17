package com.mkv.tosca.cli.command

import java.nio.file.Paths

import com.mkv.tosca.cli.parser._
import com.mkv.tosca.compiler.Packager
import com.mkv.util.DockerUtil
import sbt.complete.DefaultParsers._
import sbt.{Command, Help}

/**
 * Handle the cli's package command which will package a deployment into a docker image
 *
 * @author Minh Khang VU
 */
object PackageCommand {

  private val dockerUrlOpt = "-u"

  private val dockerCertOpt = "-c"

  private val deploymentNameOpt = "-n"

  private val deploymentArchiveOpt = "-d"

  private val recipePathOpt = "-r"

  private val inputPathOpt = "-i"

  private val providerConfigPathOpt = "-p"

  private lazy val dockerUrlArg = token(dockerUrlOpt) ~ (Space ~> token(URIClass))

  private lazy val dockerCertPathArg = token(dockerCertOpt) ~ (Space ~> token(Parsers.filePathParser))

  private lazy val deploymentNameArg = token(deploymentNameOpt) ~ (Space ~> token(StringBasic))

  private lazy val deploymentArchiveArg = token(deploymentArchiveOpt) ~ (Space ~> token(Parsers.filePathParser))

  private lazy val recipePathArg = token(recipePathOpt) ~ (Space ~> token(Parsers.filePathParser))

  private lazy val inputPathArg = token(inputPathOpt) ~ (Space ~> token(Parsers.filePathParser))

  private lazy val providerConfigPathArg = token(providerConfigPathOpt) ~ (Space ~> token(Parsers.filePathParser))

  private lazy val packageArgsParser = Space ~> (deploymentArchiveArg | dockerCertPathArg | dockerUrlArg | deploymentNameArg | recipePathArg | inputPathArg | providerConfigPathArg) +

  private lazy val packageHelp = Help("package", ("package", "Package a deployable archive to a docker image"),
    """
      |package -d <deployment path> -u <docker daemon url> -c <certificate path>
      | or
      |package -n <deployment name> -r <recipe path> [-i <deployment input path>] -u <docker daemon url> -c <certificate path> -p <provider config path>
      |-d   : path to the deployment archive. If specified -n, -r, -i, -p will not be considered, all those configs will be loaded from the deployment archive.
      |-n   : name of the deployment
      |-r   : path to the recipe
      |-i   : path to the input for the deployment
      |-u   : url of the docker daemon
      |-c   : path to the the certificate to connect to the docker daemon
      |-p   : path to the provider's config
    """.stripMargin
  )

  lazy val instance = Command("package", packageHelp)(_ => packageArgsParser) { (state, args) =>
    val argsMap = args.toMap
    var fail = false
    var imageId = ""
    var imageName = ""
    if (!argsMap.contains(dockerUrlOpt) || !argsMap.contains(dockerCertOpt)) {
      println(dockerUrlOpt + " and " + dockerCertOpt + " are mandatory")
      fail = true
    }

    val dockerClient = DockerUtil.buildDockerClient(argsMap(dockerUrlOpt), argsMap(dockerCertOpt))
    try {
      if (!fail) {
        if (argsMap.contains(deploymentArchiveOpt)) {
          val image = Packager.createDockerImage(
            dockerClient,
            Paths.get(argsMap(deploymentArchiveOpt))
          )
          imageId = image._1
          imageName = image._2
        } else {
          if (!argsMap.contains(recipePathOpt) || !argsMap.contains(providerConfigPathOpt) || !argsMap.contains(deploymentNameOpt)) {
            println(recipePathOpt + ", " + providerConfigPathOpt + " and " + deploymentNameOpt + " are mandatory")
            fail = true
          } else {
            val recipePath = Paths.get(argsMap(recipePathOpt))
            val image = Packager.createDockerImage(
              dockerClient,
              argsMap(deploymentNameOpt),
              recipePath,
              argsMap.get(inputPathOpt).map(Paths.get(_)),
              Paths.get(argsMap(providerConfigPathOpt))
            )
            imageId = image._1
            imageName = image._2
          }
        }
      }
    } finally {
      dockerClient.close()
    }
    if (fail) {
      state.fail
    } else {
      println("Packaged deployment as docker image with name <" + imageName + "> and id <" + imageId + ">")
      state
    }
  }
}
