package com.toscaruntime.cli.command

import java.nio.file.{Files, Path, Paths, StandardCopyOption}

import com.github.dockerjava.core.DockerClientConfig
import com.toscaruntime.cli.Attributes
import com.toscaruntime.cli.parser.Parsers
import com.toscaruntime.rest.client.ToscaRuntimeClient
import com.toscaruntime.util.{DockerDaemonConfig, DockerUtil, FileUtil}
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

  private val dockerHostOpt = "--host"

  private val dockerCertOpt = "--cert"

  private lazy val dockerUrlArg = token(dockerHostOpt) ~ (token("=") ~> token(URIClass))

  private lazy val dockerCertPathArg = token(dockerCertOpt) ~ (token("=") ~> token(Parsers.filePathParser))

  private lazy val useArgsParser = Space ~> (dockerUrlArg | dockerCertPathArg) +

  private lazy val useHelp = Help(commandName, (commandName, "Use the specified docker daemon"),
    s"""
       |$commandName $dockerHostOpt=<docker daemon host> $dockerCertOpt=<certificate path>
       |$dockerHostOpt%-30s of the docker daemon
       |$dockerCertOpt%-30s (optional if SSL is not enable) path to the the certificate to connect to the docker daemon
    """.stripMargin
  )

  private lazy val useDefaultHelp = Help(useDefaultCommandName, (useDefaultCommandName, "Use the default docker daemon url"),
    s"""Use the default docker daemon configuration, this order will be respected to detect the configuration:
        |1. Read from DOCKER_HOST, DOCKER_TLS_VERIFY, DOCKER_CERT_PATH
        |2. If not found, assign default values based on OS ${DockerUtil.getDefaultDockerDaemonConfig.getHost}
     """.stripMargin
  )

  lazy val useDefaultInstance = Command.command(useDefaultCommandName, useDefaultHelp) { state =>
    val basedir = state.attributes.get(Attributes.basedirAttribute).get
    val defaultConfig = DockerUtil.getDefaultDockerDaemonConfig
    val client = state.attributes.get(Attributes.clientAttribute).get
    client.switchConnection(defaultConfig)
    switchConfiguration(defaultConfig, basedir)
    println(s"Begin to use docker daemon at [${defaultConfig.getHost}] with api version [${client.dockerVersion}]")
    state
  }

  lazy val instance = Command(commandName, useHelp)(_ => useArgsParser) { (state, args) =>
    val argsMap = args.toMap
    var fail = false
    val client = state.attributes.get(Attributes.clientAttribute).get
    if (!argsMap.contains(dockerHostOpt)) {
      println(s"$dockerHostOpt is mandatory")
      fail = true
    } else {
      val basedir = state.attributes.get(Attributes.basedirAttribute).get
      val host = argsMap(dockerHostOpt)
      val cert = argsMap.getOrElse(dockerCertOpt, null)
      val tlsVerify = if (cert != null) "1" else null
      val daemonConfig = new DockerDaemonConfig(host, tlsVerify, cert)
      switchConnection(client, daemonConfig, basedir)
      switchConfiguration(daemonConfig, basedir)
      println(s"Begin to use docker daemon at [${argsMap(dockerHostOpt)}] with api version [${client.dockerVersion}]")
    }
    if (fail) state.fail else state
  }

  def getDaemonConfigPath(basedir: Path) = {
    basedir.resolve("conf").resolve("daemon")
  }

  def switchConnection(client: ToscaRuntimeClient, daemonConfig: DockerDaemonConfig, basedir: Path) = {
    client.switchConnection(daemonConfig)
    switchConfiguration(daemonConfig, basedir)
  }

  def switchConfiguration(daemonConfig: DockerDaemonConfig, basedir: Path) = {
    val dockerConfigPath = getDaemonConfigPath(basedir)
    if (!Files.exists(dockerConfigPath)) {
      Files.createDirectories(dockerConfigPath)
    }
    if (StringUtils.isNotBlank(daemonConfig.getCertPath)) {
      copyCertificates(Paths.get(daemonConfig.getCertPath), dockerConfigPath.resolve("cert"))
    }
    var config =
      s"""# Attention this file is auto-generated and might be overwritten when configuration changes
          |${DockerClientConfig.DOCKER_HOST}="${daemonConfig.getHost}"""".stripMargin
    if (StringUtils.isNotBlank(daemonConfig.getCertPath) && StringUtils.isNotBlank(daemonConfig.getTlsVerify)) {
      config +=
        s"""
           |${DockerClientConfig.DOCKER_TLS_VERIFY}="${daemonConfig.getTlsVerify}"
           |${DockerClientConfig.DOCKER_CERT_PATH}=$${com.toscaruntime.target.dir}"/cert"""".stripMargin
    }
    FileUtil.writeTextFile(config, dockerConfigPath.resolve("provider.conf"))

    // Try to auto-generate docker provider config
    val defaultDockerProviderConfigPath = basedir.resolve("conf").resolve("providers").resolve("docker").resolve("default")
    if (!Files.exists(defaultDockerProviderConfigPath.resolve("provider.conf"))) {
      FileUtil.copy(dockerConfigPath.resolve("provider.conf"), defaultDockerProviderConfigPath.resolve("auto_generated_provider.conf"), StandardCopyOption.REPLACE_EXISTING)
      if (StringUtils.isNotBlank(daemonConfig.getCertPath)) copyCertificates(Paths.get(daemonConfig.getCertPath), defaultDockerProviderConfigPath.resolve("cert"))
    }
    dockerConfigPath
  }

  private def copyCertificates(certPath: Path, output: Path): Unit = {
    println(s"Copy certificates from [$certPath] to [$output]")
    if (Files.exists(output)) {
      FileUtil.delete(output)
    }
    Files.createDirectories(output)
    Files.copy(certPath.resolve("key.pem"), output.resolve("key.pem"))
    Files.copy(certPath.resolve("cert.pem"), output.resolve("cert.pem"))
    Files.copy(certPath.resolve("ca.pem"), output.resolve("ca.pem"))
  }

  def getConfiguration(basedir: Path) = {
    val dockerConfigPath = getDaemonConfigPath(basedir)
    val dockerConfigFilePath = dockerConfigPath.resolve("provider.conf")
    if (Files.exists(dockerConfigFilePath)) {
      println(s"Found existing configuration at [$dockerConfigPath]")
      val providerConfig = ConfigFactory.parseFile(dockerConfigFilePath.toFile).resolveWith(
        ConfigFactory.empty().withValue("com.toscaruntime.target.dir", ConfigValueFactory.fromAnyRef(dockerConfigPath.toString))
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
