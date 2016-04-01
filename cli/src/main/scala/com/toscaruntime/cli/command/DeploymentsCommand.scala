package com.toscaruntime.cli.command

import java.nio.file.{Files, Path, Paths}

import com.toscaruntime.cli.parser.Parsers
import com.toscaruntime.cli.util.{CompilationUtil, TabulatorUtil}
import com.toscaruntime.cli.{Args, Attributes}
import com.toscaruntime.compiler.Compiler
import com.toscaruntime.constant.{ProviderConstant, RuntimeConstant}
import com.toscaruntime.rest.client.ToscaRuntimeClient
import com.toscaruntime.util.{FileUtil, PathUtil}
import sbt.complete.DefaultParsers._
import sbt.{Command, Help}

import scala.language.postfixOps

/**
  * List all packaged deployments (docker images) available on the docker daemon
  *
  * @author Minh Khang VU
  */
object DeploymentsCommand {

  private val commandName = "deployments"

  private val listOpt = "list"

  private val createOpt = "create"

  private val deleteOpt = "delete"

  private val cleanOpt = "cleanDangling"

  private val topologyPathOpt = "-r"

  private val csarOpt = "-c"

  private val inputPathOpt = "-i"

  private val bootstrapOpt = "-b"

  private lazy val topologyPathArg = token(topologyPathOpt) ~ (Space ~> token(Parsers.filePathParser))

  private lazy val csarArg = token(csarOpt) ~ (Space ~> (token(StringBasic) ~ (token(":") ~> token(StringBasic))))

  private lazy val deploymentInputsPathArg = token(inputPathOpt) ~ (Space ~> token(Parsers.filePathParser))

  private lazy val deploymentCreateArgsParser = Space ~> (topologyPathArg | csarArg | deploymentInputsPathArg | Args.providerArg | Args.targetArg | (token(bootstrapOpt) ~ (Space ~> token(Bool)))) +

  private lazy val deploymentsArgsParser =
    Space ~> ((token(createOpt) ~ (Space ~> token(StringBasic)) ~ deploymentCreateArgsParser) | token(listOpt) | (token(deleteOpt) ~ (Space ~> token(StringBasic))) | token(cleanOpt)) +

  private lazy val deploymentActionsHelp = Help(commandName, (commandName, s"Actions on deployments, execute 'help $commandName' for more details"),
    s"""
       |$commandName $createOpt <deployment name> [$topologyPathOpt <topology path> | $csarOpt <csar name>:<csar version> ] $inputPathOpt <input path> ${Args.providerOpt} <provider name=${ProviderConstant.DOCKER}> ${Args.targetOpt} <provider target=${ProviderConstant.DEFAULT_TARGET}>
       |             Create the deployment from the topology for a particular provider's target
       |$commandName $listOpt
       |             List all created deployments
       |$commandName $deleteOpt <deployment name>
       |             Delete the deployment
       |$commandName $cleanOpt
       |             Clean up dangling deployments docker image (for administration purpose, to free disk space)
    """.stripMargin
  )

  private def getTopologyPath(createArgs: Map[String, Any], repository: Path): Option[Path] = {
    if (createArgs.contains(topologyPathOpt) && createArgs.contains(csarOpt)) {
      println(s"Only one of $topologyPathOpt or $csarOpt is required to create a deployment")
      return None
    }
    if (createArgs.contains(topologyPathOpt)) {
      val topologyPath = Paths.get(createArgs(topologyPathOpt).asInstanceOf[String])
      if (!Files.exists(topologyPath)) {
        println(s"Topology file ${topologyPath.toString} do not exist")
        return None
      } else {
        return Some(topologyPath)
      }
    }
    if (createArgs.contains(csarOpt)) {
      return createArgs.get(csarOpt).flatMap {
        case (csarName: String, csarVersion: String) => Compiler.getCsarToscaRecipePath(csarName, csarVersion, repository)
      }
    }
    None
  }

  /**
    * Create deployment image
    *
    * @param topologyPath      path to the topology
    * @param inputsPath        path to the inputs if exists
    * @param repository        path to the csar repository
    * @param deploymentWorkDir path to the output
    * @param deploymentId      id of the deployment image
    * @param client            toscaruntime client
    * @param bootstrapMode     enable bootstrap mode
    * @return true upon success false otherwise
    */
  def createDeploymentImage(topologyPath: Path,
                            inputsPath: Option[Path],
                            repository: Path,
                            deploymentWorkDir: Path,
                            deploymentId: String,
                            client: ToscaRuntimeClient,
                            providerConf: Path,
                            bootstrapMode: Option[Boolean]): Boolean = {
    val compilationResult = Compiler.assembly(topologyPath, deploymentWorkDir, repository, inputsPath)
    if (compilationResult.isSuccessful) {
      client.createDeploymentImage(deploymentId, deploymentWorkDir, inputsPath, providerConf, bootstrapMode).awaitImageId()
    } else {
      CompilationUtil.showErrors(compilationResult)
      return false
    }
    true
  }

  /**
    * List deployment images
    *
    * @param client the client to perform the request
    * @return list of deployment images
    */
  def listDeploymentImages(client: ToscaRuntimeClient) = {
    val images = client.listDeploymentImages()
    images.map { image =>
      List(image.getContainerConfig.getLabels.get(RuntimeConstant.DEPLOYMENT_ID_LABEL), image.getCreated, image.getId)
    }
  }

  /**
    * Delete the deployment image
    *
    * @param client       the client to perform the request
    * @param deploymentId the deployment to delete
    */
  def deleteDeploymentImage(client: ToscaRuntimeClient, deploymentId: String) = {
    // This is necessary to ensure that we test what the CLI uses
    client.deleteDeploymentImage(deploymentId)
  }

  lazy val instance = Command(commandName, deploymentActionsHelp)(_ => deploymentsArgsParser) { (state, args) =>

    val client = state.attributes.get(Attributes.clientAttribute).get
    var fail = false
    args.head match {
      case "list" =>
        val images = listDeploymentImages(client)
        if (images.nonEmpty) {
          println(s"Found ${images.size} deployment image(s):")
          val headers = List("Deployment Id", "Created", "Image Id")
          println(TabulatorUtil.format(headers :: images))
        } else {
          println("No deployment found")
        }
      case (("create", deploymentId: String), createOpts: Seq[(String, _)]) =>
        val basedir = state.attributes.get(Attributes.basedirAttribute).get
        val repository = basedir.resolve("repository")
        val workDir = basedir.resolve("work")
        val createArgs = createOpts.toMap
        val topologyPathOpt = getTopologyPath(createArgs, repository)
        if (topologyPathOpt.isEmpty) {
          println(s"Topology not found")
          fail = true
        } else {
          val topologyPath = topologyPathOpt.get
          val deploymentWorkDir = workDir.resolve(deploymentId)
          if (Files.exists(deploymentWorkDir)) {
            FileUtil.delete(deploymentWorkDir)
          }
          Files.createDirectories(deploymentWorkDir)
          PathUtil.openAsDirectory(topologyPath, realTopologyPath => {
            val inputsPath = createArgs.get(inputPathOpt).map(inputPath => Paths.get(inputPath.asInstanceOf[String]))
            val providerName = createArgs.getOrElse(Args.providerOpt, ProviderConstant.DOCKER).asInstanceOf[String]
            val providerTarget = createArgs.getOrElse(Args.targetOpt, ProviderConstant.DEFAULT_TARGET).asInstanceOf[String]
            val basedir = state.attributes.get(Attributes.basedirAttribute).get
            val bootstrapMode = createArgs.get(bootstrapOpt).asInstanceOf[Option[Boolean]]
            val providerConf = basedir.resolve("conf").resolve("providers").resolve(providerName).resolve(providerTarget)
            if (Files.exists(providerConf)) {
              println(s"Creating deployment image [$deploymentId], it'll take some minutes ...")
              fail = !createDeploymentImage(
                realTopologyPath,
                inputsPath,
                repository,
                deploymentWorkDir,
                deploymentId,
                client,
                providerConf,
                bootstrapMode
              )
              if (!fail) println(s"Deployment image [$deploymentId] has been created, 'agents create $deploymentId' to start an agent to deploy it")
            } else {
              println(s"No configuration found for [$providerName], target [$providerTarget] at [$providerConf]")
              fail = true
            }
          })
        }
      case "cleanDangling" =>
        client.cleanDanglingImages()
        println("Cleaned all dangling images")
      case ("delete", deploymentId: String) =>
        deleteDeploymentImage(client, deploymentId)
        println(s"Deleted deployment image [$deploymentId]")
    }
    if (fail) state.fail else state
  }
}
