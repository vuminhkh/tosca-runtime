package com.toscaruntime.cli.command

import java.nio.file.{Files, Path, Paths}

import com.toscaruntime.cli.parser.Parsers
import com.toscaruntime.cli.util.{CompilationUtil, DeployUtil, TabulatorUtil}
import com.toscaruntime.cli.{Args, Attributes}
import com.toscaruntime.compiler.Compiler
import com.toscaruntime.constant.{ProviderConstant, RuntimeConstant}
import com.toscaruntime.util.FileUtil
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

  private val runOpt = "run"

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
    Space ~> ((token(createOpt) ~ (Space ~> token(StringBasic)) ~ deploymentCreateArgsParser) | (token(runOpt) ~ (Space ~> token(StringBasic))) | token(listOpt) | (token(deleteOpt) ~ (Space ~> token(StringBasic))) | token(cleanOpt)) +

  private lazy val deploymentActionsHelp = Help(commandName, (commandName, s"Actions on deployments, execute 'help $commandName' for more details"),
    s"""
       |$commandName $createOpt <deployment name> [$topologyPathOpt <topology path> | $csarOpt <csar name>:<csar version> ] $inputPathOpt <input path> ${Args.providerOpt} <provider name=${ProviderConstant.DOCKER}> ${Args.targetOpt} <provider target=${ProviderConstant.DEFAULT_TARGET}>
       |             Create the deployment from the topology for a particular provider's target
       |$commandName $listOpt
       |             List all created deployments
       |$commandName $deleteOpt <deployment name>
       |             Delete the deployment
       |$commandName $runOpt <deployment name>
       |             Run the deployment
       |$commandName $cleanOpt
       |             Clean up dangling deployments docker image (for administration purpose, to free disk space)
    """.stripMargin
  )

  def getTopologyPath(createArgs: Map[String, Any], repository: Path): Option[Path] = {
    if (createArgs.contains(topologyPathOpt) && createArgs.contains(csarOpt)) {
      println(s"Only one of $topologyPathOpt or $csarOpt is required to create a deployment")
      return None
    }
    if (createArgs.contains(topologyPathOpt)) {
      val topologyPath = createArgs(topologyPathOpt).asInstanceOf[String]
      if (!Files.exists(Paths.get(topologyPath))) {
        println(s"Topology file $topologyPath do not exist")
        return None
      }
    }
    if (createArgs.contains(csarOpt)) {
      return createArgs.get(csarOpt).flatMap {
        case (csarName: String, csarVersion: String) => Compiler.resolveDependency(csarName, csarVersion, repository)
      }
    }
    None
  }

  lazy val instance = Command(commandName, deploymentActionsHelp)(_ => deploymentsArgsParser) { (state, args) =>

    val client = state.attributes.get(Attributes.clientAttribute).get
    var fail = false
    args.head match {
      case "list" =>
        val images = client.listDeploymentImages()
        if (images.nonEmpty) {
          println(s"Found ${images.size} deployment image(s):")
          val headers = List("Deployment Id", "Created", "Image Id")
          val imagesData = images.map { image =>
            List(image.getContainerConfig.getLabels.get(RuntimeConstant.DEPLOYMENT_ID_LABEL), image.getCreated, image.getId)
          }
          println(TabulatorUtil.format(headers :: imagesData))
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
          val topologyIsZipped = Files.isRegularFile(topologyPath)
          var realTopologyPath = topologyPath
          if (topologyIsZipped) {
            realTopologyPath = FileUtil.createZipFileSystem(topologyPath)
          }
          try {
            val inputsPath = createArgs.get(inputPathOpt).map(inputPath => Paths.get(inputPath.asInstanceOf[String]))
            val compilationResult = Compiler.assembly(realTopologyPath, deploymentWorkDir, repository, inputsPath)
            if (compilationResult.isSuccessful) {
              val providerName = createArgs.getOrElse(Args.providerOpt, ProviderConstant.DOCKER).asInstanceOf[String]
              val providerTarget = createArgs.getOrElse(Args.targetOpt, ProviderConstant.DEFAULT_TARGET).asInstanceOf[String]
              val providerConf = state.attributes.get(Attributes.basedirAttribute).get
                .resolve("conf").resolve("providers")
                .resolve(providerName)
                .resolve(providerTarget)
              val bootstrapMode = createArgs.getOrElse(bootstrapOpt, false).asInstanceOf[Boolean]
              if (Files.exists(providerConf)) {
                client.createDeploymentImage(deploymentId, deploymentWorkDir, inputsPath, providerConf, bootstrapMode).awaitImageId()
                println(s"Deployment [$deploymentId] has been created, 'deployments run $deploymentId' to deploy it")
              } else {
                println(s"No configuration found for [$providerName], target [$providerTarget] at [$providerConf]")
                fail = true
              }
            } else {
              CompilationUtil.showErrors(compilationResult)
              fail = true
            }
          } finally {
            if (topologyIsZipped) {
              realTopologyPath.getFileSystem.close()
            }
          }
        }
      case "cleanDangling" =>
        client.cleanDanglingImages()
        println("Cleaned all dangling images")
      case ("delete", deploymentId: String) =>
        client.deleteDeploymentImage(deploymentId)
        println(s"Deleted deployment image [$deploymentId]")
      case ("run", deploymentId: String) =>
        val containerId = client.createDeploymentAgent(deploymentId).getId
        DeployUtil.waitForDeploymentAgent(client, deploymentId)
        client.deploy(deploymentId)
        println(s"Agent with id [$containerId] has been created for deployment [$deploymentId]")
        println(s"Execute 'agents log $deploymentId' to tail the log of deployment agent")
    }
    if (fail) state.fail else state
  }
}
