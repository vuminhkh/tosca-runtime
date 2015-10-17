package com.mkv.tosca.cli.command

import com.mkv.tosca.cli.parser.Parsers
import com.mkv.util.DockerUtil
import sbt.complete.DefaultParsers._
import sbt.{Command, Help}

/**
 * @author Minh Khang VU
 */
object DeployCommand {

  private val dockerUrlOpt = "-u"

  private val dockerCertOpt = "-c"

  private val imageIdOpt = "-i"

  private lazy val imageIdArg = token(imageIdOpt) ~ (Space ~> token(StringBasic))

  private lazy val dockerUrlArg = token(dockerUrlOpt) ~ (Space ~> token(URIClass))

  private lazy val dockerCertPathArg = token(dockerCertOpt) ~ (Space ~> token(Parsers.filePathParser))

  private lazy val imageIdArgsParser = Space ~> (imageIdArg | dockerCertPathArg | dockerUrlArg) +

  private lazy val deployHelp = Help("deploy", ("deploy", "Deploy built deployment agent"),
    """
      |deploy -i <docker image of the agent> -u <docker daemon url> -c <certificate path>
      |-i   : image id of the agent docker's image
      |-u   : url of the docker daemon
      |-c   : path to the the certificate to connect to the docker daemon
    """.stripMargin
  )

  lazy val instance = Command("deploy", deployHelp)(_ => imageIdArgsParser) { (state, args) =>
    val argsMap = args.toMap
    var fail = false
    var containerId = ""
    var ipAddress = ""
    if (!argsMap.contains(dockerUrlOpt) || !argsMap.contains(dockerCertOpt) || !argsMap.contains(imageIdOpt)) {
      println(imageIdOpt + "," + dockerUrlOpt + " and " + dockerCertOpt + " are mandatory")
      fail = true
    } else {
      val dockerClient = DockerUtil.buildDockerClient(argsMap(dockerUrlOpt), argsMap(dockerCertOpt))
      try {
        containerId = dockerClient.createContainerCmd(argsMap(imageIdOpt)).exec.getId
        dockerClient.startContainerCmd(containerId).exec
        ipAddress = dockerClient.inspectContainerCmd(containerId).exec.getNetworkSettings.getIpAddress
      } finally {
        dockerClient.close()
      }
    }
    if (fail) {
      state.fail
    } else {
      println("Started deployment agent <" + containerId + "> with ip address <" + ipAddress + ">")
      state
    }
  }
}
