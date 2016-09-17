package com.toscaruntime.cli.command

import java.nio.file.{Files, Path, Paths}

import com.toscaruntime.cli.Args._
import com.toscaruntime.cli.parser.Parsers
import com.toscaruntime.cli.util.PluginUtil._
import com.toscaruntime.cli.util.{CompilationUtil, TabulatorUtil}
import com.toscaruntime.cli.{Args, Attributes}
import com.toscaruntime.compiler.Compiler
import com.toscaruntime.constant.{ProviderConstant, RuntimeConstant}
import com.toscaruntime.rest.client.ToscaRuntimeClient
import com.toscaruntime.util.{FileUtil, PathUtil}
import com.typesafe.scalalogging.LazyLogging
import sbt.complete.DefaultParsers._
import sbt.{Command, Help}

import scala.language.postfixOps

/**
  * Create / list / delete deployment image
  *
  * @author Minh Khang VU
  */
object DeploymentsCommand extends LazyLogging {

  private val commandName = "deployments"

  private val listCmd = "list"

  private val createCmd = "create"

  private val deleteCmd = "delete"

  private val cleanCmd = "cleanDangling"

  private val csarOpt = "--csar"

  private val inputPathOpt = "--input"

  private val bootstrapOpt = "--bootstrap"

  private lazy val csarPathOptParser = token(csarOpt) ~ (token("=") ~> (token(StringBasic) ~ (token(":") ~> token(StringBasic))))

  private lazy val inputsPathOptParser = token(inputPathOpt) ~ (token("=") ~> token(Parsers.filePathParser))

  private lazy val createCmdOptParser = Space ~> (csarPathOptParser | inputsPathOptParser | (token(bootstrapOpt) ~ (token("=") ~> token(Bool)))) *

  private lazy val createCmdParser = token(createCmd) ~ createCmdOptParser ~ (Space ~> token(StringBasic)) ~ ((Space ~> token(Parsers.filePathParser)) ?)

  private lazy val listCmdParser = token(listCmd)

  private lazy val deleteCmdParser = token(deleteCmd) ~ (Space ~> token(StringBasic))

  private lazy val cleanCmdParser = token(cleanCmd)

  private lazy val deploymentsCmdParser = Space ~>
    (createCmdParser |
      listCmdParser |
      deleteCmdParser |
      cleanCmdParser)

  private val synopsisToken = "SYNOPSIS"

  private lazy val deploymentActionsHelp = Help(commandName, (commandName, s"Create / list / delete deployment image, execute 'help $commandName' for more details"),
    f"""
       |$commandName <sub command> [OPTIONS] [deployment id] [ARGS]
       |
       |Sub commands:
       |
       |  $listCmd%-30s List all created deployments
       |
       |  $createCmd%-30s Create deployment image from the given topology on a particular provider's target
       |  $synopsisToken%-30s $createCmd [CREATE_OPTIONS] <deployment id> <topology path>
       |  CREATE_OPTIONS:
       |    ${csarOpt + "=<csar name>:<version>"}%-28s use an installed topology to create the deployment, if this option is set <topology path> argument is not necessary
       |    ${inputPathOpt + "=<input path>"}%-28s input for the deployment
       |    ${Args.targetOpt + "=<provider>:<target>"}%-28s the provider's configuration, default to ${ProviderConstant.DEFAULT_TARGET}, can be repeated multiple times for hybrid deployment
       |    ${bootstrapOpt + "=<true|false>"}%-28s enable bootstrap mode, in this mode the agent will use public ip to connect to created compute
       |
       |  $deleteCmd%-30s Delete the deployment
       |  $synopsisToken%-30s $deleteCmd <deployment id>
       |
       |  $cleanCmd%-30s Clean up dangling docker image, to free disk space
    """.stripMargin
  )

  private def getTopologyPath(topologyPathArg: Option[String], createArgs: Seq[(String, Any)], repository: Path): Option[Path] = {
    topologyPathArg.map { topologyPathText =>
      val topologyPath = Paths.get(topologyPathText)
      if (!Files.exists(topologyPath)) {
        println(s"Topology file ${topologyPath.toString} do not exist")
        return None
      } else {
        return Some(topologyPath)
      }
    }
    getTupleOption(createArgs, csarOpt).flatMap {
      case (csarName: String, csarVersion: String) => Compiler.getCsarToscaRecipePath(csarName, csarVersion, repository)
    }
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
    * @param providerBaseConf  the base dir for provider's configuration
    * @param pluginBaseConf    the base dir for plugin's configuration
    * @param bootstrapMode     enable bootstrap mode
    * @return true upon success false otherwise
    */
  def createDeploymentImage(topologyPath: Path,
                            inputsPath: Option[Path],
                            repository: Path,
                            deploymentWorkDir: Path,
                            deploymentId: String,
                            client: ToscaRuntimeClient,
                            providerBaseConf: Path,
                            pluginBaseConf: Path,
                            bootstrapMode: Option[Boolean]): Boolean = {
    if (Files.exists(deploymentWorkDir)) {
      FileUtil.delete(deploymentWorkDir)
    }
    Files.createDirectories(deploymentWorkDir)
    val compilationResult = Compiler.assembly(topologyPath, deploymentWorkDir, repository, inputsPath)
    if (compilationResult.isSuccessful) {
      val providerConfigPaths = compilationResult.providers.map(providerBaseConf.resolve)
      val pluginConfigPaths = compilationResult.plugins.map(pluginBaseConf.resolve)

      val missingProviderConfigs = providerConfigPaths.filter(!isProviderConfigValid(_))
      missingProviderConfigs.foreach(providerConfigPath => println(s"Missing provider configuration at $providerConfigPath"))

      val missingPluginConfigs = pluginConfigPaths.filter(!isPluginConfigValid(_))
      missingPluginConfigs.foreach(pluginConfigPath => println(s"Missing plugin configuration at $pluginConfigPath"))

      if (missingProviderConfigs.isEmpty && missingPluginConfigs.isEmpty) {
        // No provider's configuration missing
        client.createDeploymentImage(deploymentId, deploymentWorkDir, providerConfigPaths, pluginConfigPaths, bootstrapMode).awaitImageId()
        return true
      } else {
        return false
      }
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

  lazy val instance = Command(commandName, deploymentActionsHelp)(_ => deploymentsCmdParser) { (state, args) =>
    val client = state.attributes.get(Attributes.clientAttribute).get
    val basedir = state.attributes.get(Attributes.basedirAttribute).get
    var fail = false
    try {
      args match {
        case `listCmd` =>
          val images = listDeploymentImages(client)
          if (images.nonEmpty) {
            println(s"Found ${images.size} deployment image(s):")
            val headers = List("Deployment Id", "Created", "Image Id")
            println(TabulatorUtil.format(headers :: images))
          } else {
            println("No deployment found")
          }
        case (((`createCmd`, createOpts: Seq[(String, Any)]), deploymentId: String), topologyPathArg: Option[String]) =>
          val repository = basedir.resolve("repository")
          val workDir = basedir.resolve("work")
          val topologyPathOpt = getTopologyPath(topologyPathArg, createOpts, repository)
          if (topologyPathOpt.isEmpty) {
            println(s"Topology not found")
            fail = true
          } else {
            val topologyPath = topologyPathOpt.get
            val deploymentWorkDir = workDir.resolve(deploymentId)
            PathUtil.openAsDirectory(topologyPath, realTopologyPath => {
              val inputsPath = getStringOption(createOpts, inputPathOpt).map(Paths.get(_))
              val basedir = state.attributes.get(Attributes.basedirAttribute).get
              val bootstrapMode = getBooleanOption(createOpts, bootstrapOpt)
              val providerConfBase = basedir.resolve("conf").resolve("providers")
              val pluginConfBase = basedir.resolve("conf").resolve("plugins")
              println(s"Creating deployment image [$deploymentId], it might take some minutes ...")
              fail = !createDeploymentImage(
                realTopologyPath,
                inputsPath,
                repository,
                deploymentWorkDir,
                deploymentId,
                client,
                providerConfBase,
                pluginConfBase,
                bootstrapMode
              )
              if (!fail) println(s"Deployment image [$deploymentId] has been created, 'agents create $deploymentId' to start an agent to deploy it")
            })
          }
        case "cleanDangling" =>
          client.cleanDanglingImages()
          println("Cleaned all dangling images")
        case ("delete", deploymentId: String) =>
          deleteDeploymentImage(client, deploymentId)
          println(s"Deleted deployment image [$deploymentId]")
      }
    } catch {
      case e: Throwable =>
        println(s"Error ${e.getMessage}")
        logger.error("Command finished with error", e)
        fail = true
    }
    if (fail) state.fail else state
  }
}
