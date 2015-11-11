package com.toscaruntime.cli.command

import com.toscaruntime.cli.Attributes
import com.toscaruntime.cli.parser.Parsers
import com.toscaruntime.util.DockerUtil
import sbt.complete.DefaultParsers._
import sbt.{Command, Help}

/**
 * Register new docker daemon url
 *
 * @author Minh Khang VU
 */
object UseCommand {

  private val dockerUrlOpt = "-u"

  private val dockerCertOpt = "-c"

  private lazy val dockerUrlArg = token(dockerUrlOpt) ~ (Space ~> token(URIClass))

  private lazy val dockerCertPathArg = token(dockerCertOpt) ~ (Space ~> token(Parsers.filePathParser))

  private lazy val useArgsParser = Space ~> (dockerUrlArg | dockerCertPathArg) +

  private lazy val useHelp = Help("use", ("use", "Use the specified docker daemon url"),
    """
      |use -u <docker daemon url> -c <certificate path>
      |-u   : url of the docker daemon
      |-c   : path to the the certificate to connect to the docker daemon
    """.stripMargin
  )

  lazy val instance = Command("use", useHelp)(_ => useArgsParser) { (state, args) =>
    val argsMap = args.toMap
    var fail = false
    val dockerClientHolder = state.attributes.get(Attributes.dockerDaemonAttribute).get
    if (!argsMap.contains(dockerUrlOpt)) {
      println(dockerUrlOpt + " is mandatory")
      fail = true
    } else {
      dockerClientHolder.dockerClient.close()
      dockerClientHolder.dockerClient = DockerUtil.buildDockerClient(argsMap(dockerUrlOpt), argsMap.getOrElse(dockerCertOpt, null))
      println(argsMap(dockerUrlOpt) + " is using api version " + dockerClientHolder.dockerClient.versionCmd().exec().getApiVersion)

    }
    if (fail) {
      state.fail
    } else {
      println("Begin to use docker daemon at <" + argsMap(dockerUrlOpt) + ">" + " with api version <" + dockerClientHolder.dockerClient.versionCmd().exec().getApiVersion + ">")
      state
    }
  }
}
