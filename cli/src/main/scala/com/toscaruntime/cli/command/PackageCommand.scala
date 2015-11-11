package com.toscaruntime.cli.command

import java.nio.file.Paths

import com.toscaruntime.cli.parser._
import com.toscaruntime.cli.Args
import com.toscaruntime.cli.parser.Parsers
import com.toscaruntime.cli.{Attributes, Args}
import com.toscaruntime.compiler.Packager
import sbt.complete.DefaultParsers._
import sbt.{Command, Help}

/**
 * Handle the cli's package command which will package a deployment into a docker image
 *
 * @author Minh Khang VU
 */
object PackageCommand {

  private val deploymentNameOpt = "-n"

  private val deploymentArchiveOpt = "-d"

  private val recipePathOpt = "-r"

  private val inputPathOpt = "-i"

  private lazy val deploymentNameArg = token(deploymentNameOpt) ~ (Space ~> token(StringBasic))

  private lazy val deploymentArchiveArg = token(deploymentArchiveOpt) ~ (Space ~> token(Parsers.filePathParser))

  private lazy val recipePathArg = token(recipePathOpt) ~ (Space ~> token(Parsers.filePathParser))

  private lazy val inputPathArg = token(inputPathOpt) ~ (Space ~> token(Parsers.filePathParser))

  private lazy val packageArgsParser = Space ~> (deploymentArchiveArg | deploymentNameArg | recipePathArg | inputPathArg | Args.providerArg) +

  private lazy val packageHelp = Help("package", ("package", "Package a deployable archive to a docker image, execute 'help package' for more details"),
    """
      |package -d <deployment path>
      | or
      |package -n <deployment name> -r <recipe path> [-i <deployment input path>] -p <provider name>
      |-d   : path to the deployment archive. If specified -n, -r, -i, -p will not be considered, all those configs will be loaded from the deployment archive.
      |-n   : name of the deployment
      |-r   : path to the recipe
      |-i   : path to the input for the deployment
      |-p   : name of the provider
    """.stripMargin
  )

  lazy val instance = Command("package", packageHelp)(_ => packageArgsParser) { (state, args) =>
    val argsMap = args.toMap
    var fail = false
    var imageId = ""
    var imageName = ""

    val dockerClientHolder = state.attributes.get(Attributes.dockerDaemonAttribute).get
    if (argsMap.contains(deploymentArchiveOpt)) {
      val image = Packager.createDockerImage(
        dockerClientHolder.dockerClient,
        Paths.get(argsMap(deploymentArchiveOpt))
      )
      imageId = image._1
      imageName = image._2
    } else {
      if (!argsMap.contains(recipePathOpt) || !argsMap.contains(Args.providerOpt) || !argsMap.contains(deploymentNameOpt)) {
        println(recipePathOpt + ", " + Args.providerOpt + " and " + deploymentNameOpt + " are mandatory")
        fail = true
      } else {
        val recipePath = Paths.get(argsMap(recipePathOpt))
        val image = Packager.createDockerImage(
          dockerClientHolder.dockerClient,
          argsMap(deploymentNameOpt),
          false,
          recipePath,
          argsMap.get(inputPathOpt).map(Paths.get(_)),
          state.attributes.get(Attributes.basedirAttribute).get.resolve("conf").resolve("providers").resolve(argsMap(Args.providerOpt)).resolve("default")
        )
        imageId = image._1
        imageName = image._2
      }
    }
    if (fail) {
      state.fail
    } else {
      println("Packaged deployment as docker image with name <" + imageName + "> and id <" + imageId + ">")
      println("To deploy the package 'deploy -i " + imageName + "'")
      state
    }
  }
}
