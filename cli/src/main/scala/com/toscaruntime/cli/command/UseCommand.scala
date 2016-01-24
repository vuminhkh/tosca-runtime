package com.toscaruntime.cli.command

import java.nio.file.{Files, Path, Paths}

import com.toscaruntime.cli.Attributes
import com.toscaruntime.cli.parser.Parsers
import com.toscaruntime.util.{DockerUtil, FileUtil}
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import org.apache.commons.lang.StringUtils
import sbt.complete.DefaultParsers._
import sbt.{Command, Help}

import scala.collection.JavaConverters._
import scala.language.postfixOps

/**
  * Register new docker daemon url
  *
  * @author Minh Khang VU
  */
object UseCommand {

  val commandName = "use"

  val useDefaultCommandName = "use-default"

  private val dockerUrlOpt = "-u"

  private val dockerCertOpt = "-c"

  private lazy val dockerUrlArg = token(dockerUrlOpt) ~ (Space ~> token(URIClass))

  private lazy val dockerCertPathArg = token(dockerCertOpt) ~ (Space ~> token(Parsers.filePathParser))

  private lazy val useArgsParser = Space ~> (dockerUrlArg | dockerCertPathArg) +

  private lazy val useHelp = Help(commandName, (commandName, "Use the specified docker daemon url"),
    s"""
       |$commandName $dockerUrlOpt <docker daemon url> $dockerCertOpt <certificate path>
       |$dockerUrlOpt     : url of the docker daemon
       |$dockerCertOpt    : (optional if https is not used) path to the the certificate to connect to the docker daemon
    """.stripMargin
  )

  private lazy val useDefaultHelp = Help(useDefaultCommandName, (useDefaultCommandName, "Use the default docker daemon url"),
    s"""Use the default docker daemon configuration, this order will be respected to detect the configuration:
        |1. Read from DOCKER_HOST, DOCKER_TLS_VERIFY, DOCKER_CERT_PATH
        |2. If not found, read from system properties ${DockerUtil.DOCKER_URL_KEY} and ${DockerUtil.DOCKER_CERT_PATH_KEY}
        |3. If not found, assign default values based on OS:
        |    - Mac, Windows => ${DockerUtil.DEFAULT_DOCKER_URL_FOR_MAC_WINDOWS}
        |    - Others => ${DockerUtil.DEFAULT_DOCKER_URL_FOR_LINUX}
     """.stripMargin
  )

  lazy val useDefaultInstance = Command.command(useDefaultCommandName, useDefaultHelp) { state =>
    val basedir = state.attributes.get(Attributes.basedirAttribute).get
    val defaultConfig = DockerUtil.getDefaultDockerDaemonConfig
    val client = state.attributes.get(Attributes.clientAttribute).get
    client.switchConnection(defaultConfig.getUrl, defaultConfig.getCertPath)
    switchConfiguration(defaultConfig.getUrl, defaultConfig.getCertPath, basedir)
    println(s"Begin to use docker daemon at [${defaultConfig.getUrl}] with api version [${client.dockerVersion}]")
    state
  }

  lazy val instance = Command(commandName, useHelp)(_ => useArgsParser) { (state, args) =>
    val argsMap = args.toMap
    var fail = false
    val client = state.attributes.get(Attributes.clientAttribute).get
    if (!argsMap.contains(dockerUrlOpt)) {
      println(s"$dockerUrlOpt is mandatory")
      fail = true
    } else {
      val basedir = state.attributes.get(Attributes.basedirAttribute).get
      val url = argsMap(dockerUrlOpt)
      val cert = argsMap.getOrElse(dockerCertOpt, null)
      client.switchConnection(url, cert)
      switchConfiguration(url, cert, basedir)
      println(s"Begin to use docker daemon at [${argsMap(dockerUrlOpt)}] with api version [${client.dockerVersion}]")
    }
    if (fail) state.fail else state
  }

  def switchConfiguration(url: String, cert: String, basedir: Path) = {
    val dockerConfigPath = basedir.resolve("conf").resolve("providers").resolve("docker").resolve("default")
    val dockerCertPath = dockerConfigPath.resolve("cert")
    if (Files.exists(dockerCertPath)) {
      FileUtil.delete(dockerCertPath)
    }
    if (!Files.exists(dockerConfigPath)) {
      Files.createDirectories(dockerConfigPath)
    }
    if (StringUtils.isNotBlank(cert)) {
      Files.createDirectories(dockerCertPath)
      val certPath = Paths.get(cert)
      println(s"Copy certificates from [$certPath] to [$dockerCertPath]")
      Files.copy(certPath.resolve("key.pem"), dockerCertPath.resolve("key.pem"))
      Files.copy(certPath.resolve("cert.pem"), dockerCertPath.resolve("cert.pem"))
      Files.copy(certPath.resolve("ca.pem"), dockerCertPath.resolve("ca.pem"))
    }
    var config =
      s"""# Attention this file is auto-generated and might be overwritten when configuration changes
          |docker.io.url="$url"""".stripMargin
    if (StringUtils.isNotBlank(cert)) {
      config +=
        s"""
           |docker.io.dockerCertPath=$${com.toscaruntime.provider.dir}"/cert"""".stripMargin
    }
    FileUtil.writeTextFile(config, dockerConfigPath.resolve("provider.conf"))
  }

  def getConfiguration(basedir: Path) = {
    val dockerConfigPath = basedir.resolve("conf").resolve("providers").resolve("docker").resolve("default")
    val dockerConfigFilePath = dockerConfigPath.resolve("provider.conf")
    if (Files.exists(dockerConfigFilePath)) {
      println(s"Found existing configuration at [$dockerConfigPath]")
      val providerConfig = ConfigFactory.parseFile(dockerConfigFilePath.toFile).resolveWith(
        ConfigFactory.empty().withValue("com.toscaruntime.provider.dir", ConfigValueFactory.fromAnyRef(dockerConfigPath.toString))
      )
      val config = providerConfig.entrySet().asScala.map { entry =>
        (entry.getKey, entry.getValue.unwrapped().asInstanceOf[String])
      }.toMap
      config
    } else {
      Map.empty[String, String]
    }
  }
}
