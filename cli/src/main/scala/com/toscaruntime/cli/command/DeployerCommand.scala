package com.toscaruntime.cli.command

import java.util.stream.Collectors

import com.toscaruntime.cli.Args._
import com.toscaruntime.cli.Attributes
import com.toscaruntime.cli.util.TabulatorUtil
import com.toscaruntime.rest.client.ToscaRuntimeClient
import com.toscaruntime.util.FileUtil
import com.typesafe.config.{ConfigRenderOptions, ConfigValueFactory}
import com.typesafe.scalalogging.LazyLogging
import sbt.complete.DefaultParsers._
import sbt.{Command, Help}

import scala.collection.JavaConverters._
import scala.language.postfixOps

/**
  * Create / List / Delete deployer. A deployer is a base image, which can be used to create deployment image for a particular deployment. <br>
  * Image inheritance is in this order : deployment image FROM deployer image, deployer image FROM user defined docker image. <br>
  * Using this command user can then customize docker image that he uses to create deployment image. <br>
  */
object DeployerCommand extends LazyLogging {

  private val commandName = "deployer"

  private val useCmd = "use"

  private val listCmd = "list"

  private val createCmd = "create"

  private val deleteCmd = "delete"

  private val fromOtp = "--from"

  private lazy val fromOptParser = token(fromOtp) ~ (token("=") ~> token(StringBasic))

  private lazy val createCmdOptParser = (Space ~> fromOptParser) *

  private lazy val createCmdParser = token(createCmd) ~ createCmdOptParser ~ (Space ~> token(StringBasic))

  private lazy val useCmdParser = token(useCmd) ~ (Space ~> token(StringBasic))

  private lazy val listCmdParser = token(listCmd)

  private lazy val deleteCmdParser = token(deleteCmd) ~ (Space ~> token(StringBasic))

  private lazy val deployersCmdParser = Space ~> (createCmdParser | listCmdParser | deleteCmdParser | useCmdParser)

  private lazy val deployerActionsHelp = Help(commandName, (commandName, s"Create / list / delete deployer image, execute 'help $commandName' for more details"),
    f"""
       |$commandName <sub command> [OPTIONS] [deployer tag]
       |
       |Sub commands:
       |
       |  $listCmd%-30s List all deployer images
       |
       |  $useCmdParser%-30s Set the configuration to use the given tag as deployer image
       |  $synopsisToken%-30s $createCmd <deployer tag>
       |
       |  $createCmd%-30s Create deployer image from the given base image with the given tag name
       |  $synopsisToken%-30s $createCmd [CREATE_OPTIONS] <deployer tag>
       |  CREATE_OPTIONS:
       |    ${fromOtp + "=<base image>"}%-28s the base image for the deployer image (must have Java installed), toscaruntime library will be copied to this base image
       |
       |  $deleteCmd%-30s Delete the deployer image
       |  $synopsisToken%-30s $deleteCmd <deployment id>
    """.stripMargin
  )

  /**
    * List deployer images
    *
    * @param client the client to perform the request
    * @return list of deployment images
    */
  def listDeployerImages(client: ToscaRuntimeClient) = {
    val images = client.listDeployerImages()
    images.map { image =>
      List(image.getRepoTags.stream().collect(Collectors.joining(", ")), image.getCreated, image.getId)
    }
  }

  lazy val instance = Command(commandName, deployerActionsHelp)(_ => deployersCmdParser) { (state, args) =>
    val client = state.attributes.get(Attributes.clientAttribute).get
    val basedir = state.attributes.get(Attributes.basedirAttribute).get
    val config = state.attributes.get(Attributes.config).get
    var fail = false
    var newState = state
    try {
      args match {
        case `listCmd` =>
          val images = listDeployerImages(client)
          if (images.nonEmpty) {
            println(s"Found ${images.size} deployer image(s):")
            val headers = List("Tags", "Created", "Image Id")
            println(TabulatorUtil.format(headers :: images))
          } else {
            println("No deployer found")
          }
        case (`useCmd`, tag: String) =>
          if (client.listDeployerImages().flatMap(_.getRepoTags.asScala).toSet.contains(tag)) {
            val newConfig = config.withValue("deployer.image", ConfigValueFactory.fromAnyRef(tag))
            newState = state.put(Attributes.config, newConfig)
            val cliConfig = basedir.resolve("conf").resolve("toscaruntime.conf")
            FileUtil.writeTextFile(newConfig.root.render(ConfigRenderOptions.defaults().setOriginComments(false).setJson(false)), cliConfig)
            println(s"Switched to new deployer image $tag and saved to toscaruntime config")
          } else {
            println(s"Deployer image $tag cannot be found")
            fail = true
          }
        case ((`createCmd`, createOpts: Seq[(String, Any)]), tag: String) =>
          val fromBaseImage = getStringOption(createOpts, fromOtp).getOrElse(config.getString("deployer.baseImage"))
          val deployerPackagePath = basedir.resolve("images").resolve("deployer")
          val imageId = client.createDeployerImage(deployerPackagePath, fromBaseImage, tag).awaitImageId()
          println(s"Deployer image [$imageId] created with tag [$tag] from [$fromBaseImage]")
        case (`deleteCmd`, tag: String) =>
          client.deleteDeployerImage(tag)
          println(s"Deployer image [$tag] has been deleted")
      }
    } catch {
      case e: Throwable =>
        println(s"Error ${e.getMessage}, see log for stack trace")
        logger.error("Command finished with error", e)
        fail = true
    }
    if (fail) state.fail else newState
  }
}
