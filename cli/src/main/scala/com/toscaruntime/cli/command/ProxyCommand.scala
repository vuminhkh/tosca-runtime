package com.toscaruntime.cli.command

import java.util.stream.Collectors

import com.toscaruntime.cli.Args._
import com.toscaruntime.cli.Attributes
import com.toscaruntime.cli.util.TabulatorUtil
import com.toscaruntime.rest.client.ToscaRuntimeClient
import com.typesafe.scalalogging.LazyLogging
import sbt.complete.DefaultParsers._
import sbt.{Command, Help}

import scala.language.postfixOps

/**
  * Create / List / Delete proxy images. This commands help to create proxy image from user defined docker image. <br>
  */
object ProxyCommand extends LazyLogging {

  private val commandName = "proxy"

  private val listCmd = "list"

  private val createCmd = "create"

  private val deleteCmd = "delete"

  private val fromOtp = "--from"

  private lazy val fromOptParser = token(fromOtp) ~ (token("=") ~> token(StringBasic))

  private lazy val createCmdOptParser = (Space ~> fromOptParser) *

  private lazy val createCmdParser = token(createCmd) ~ createCmdOptParser ~ (Space ~> token(StringBasic))

  private lazy val listCmdParser = token(listCmd)

  private lazy val deleteCmdParser = token(deleteCmd) ~ (Space ~> token(StringBasic))

  private lazy val proxysCmdParser = Space ~> (createCmdParser | listCmdParser | deleteCmdParser)

  private lazy val proxyActionsHelp = Help(commandName, (commandName, s"Create / list / delete proxy image, execute 'help $commandName' for more details"),
    f"""
       |$commandName <sub command> [OPTIONS] [proxy tag]
       |
       |Sub commands:
       |
       |  $listCmd%-30s List all proxy images
       |
       |  $createCmd%-30s Create proxy image from the given base image with the given tag name
       |  $synopsisToken%-30s $createCmd [CREATE_OPTIONS] <proxy tag>
       |  CREATE_OPTIONS:
       |    ${fromOtp + "=<base image>"}%-28s the base image for the proxy image (must have Java installed), toscaruntime library will be copied to this base image
       |
       |  $deleteCmd%-30s Delete the proxy image
       |  $synopsisToken%-30s $deleteCmd <deployment id>
    """.stripMargin
  )

  /**
    * List proxy images
    *
    * @param client the client to perform the request
    * @return list of proxy images
    */
  def listproxyImages(client: ToscaRuntimeClient) = {
    val images = client.listProxyImages()
    images.map { image =>
      List(image.getRepoTags.stream().collect(Collectors.joining(", ")), image.getCreated, image.getId)
    }
  }

  lazy val instance = Command(commandName, proxyActionsHelp)(_ => proxysCmdParser) { (state, args) =>
    val client = state.attributes.get(Attributes.clientAttribute).get
    val basedir = state.attributes.get(Attributes.basedirAttribute).get
    val config = state.attributes.get(Attributes.config).get
    var fail = false
    try {
      args match {
        case `listCmd` =>
          val images = listproxyImages(client)
          if (images.nonEmpty) {
            println(s"Found ${images.size} proxy image(s):")
            val headers = List("Tags", "Created", "Image Id")
            println(TabulatorUtil.format(headers :: images))
          } else {
            println("No proxy found")
          }
        case ((`createCmd`, createOpts: Seq[(String, Any)]), tag: String) =>
          val fromBaseImage = getStringOption(createOpts, fromOtp).getOrElse(config.getString("proxy.baseImage"))
          val proxyPackagePath = basedir.resolve("images").resolve("proxy")
          val imageId = client.createProxyImage(proxyPackagePath, fromBaseImage, tag).awaitImageId()
          println(s"Proxy image [$imageId] created with tag [$tag] from [$fromBaseImage]")
        case (`deleteCmd`, tag: String) =>
          client.deleteProxyImage(tag)
          println(s"proxy image [$tag] has been deleted")
      }
    } catch {
      case e: Throwable =>
        println(s"Error ${e.getMessage}, see log for stack trace")
        logger.error("Command finished with error", e)
        fail = true
    }
    if (fail) state.fail else state
  }
}
