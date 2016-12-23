package com.toscaruntime.cli.command

import java.nio.file.{Files, Path, Paths}

import com.github.dockerjava.core.DockerClientConfig
import com.toscaruntime.cli.Args._
import com.toscaruntime.cli.parser.Parsers
import com.toscaruntime.cli.{Args, Attributes}
import com.toscaruntime.rest.client.ToscaRuntimeClient
import com.toscaruntime.util.{DockerDaemonConfig, DockerUtil, FileUtil, ScalaFileUtil}
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import com.typesafe.scalalogging.LazyLogging
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
object DaemonCommand extends LazyLogging {

  val commandName = "daemon"

  val useDefaultCommand = "use-default"

  val useCommand = "use"

  val infoCommand = "info"

  private val dockerHostOpt = "--host"

  private val dockerCertOpt = "--cert"

  private lazy val dockerUrlArg = token(dockerHostOpt) ~ (token("=") ~> token(URIClass))

  private lazy val dockerCertPathArg = token(dockerCertOpt) ~ (token("=") ~> token(Parsers.filePathParser))

  private lazy val useArgsParser = token(useCommand) ~ (Space ~> (dockerUrlArg | dockerCertPathArg) +)

  private lazy val useDefaultArgsParser = token(useDefaultCommand)

  private lazy val infoArgsParser = token(infoCommand)

  private lazy val daemonCmdParser = Space ~> (infoArgsParser | useDefaultArgsParser | useArgsParser)

  private lazy val daemonHelp = Help(commandName, (commandName, "Operation on the docker daemon"),
    f"""
       |$commandName <sub command> [OPTIONS]
       |
       |Sub commands:
       |  $infoCommand%-30s Show information about the currently used docker daemon
       |
       |  $useDefaultCommand%-30s Use the predefined default docker daemon of the local machine
       |
       |  $useCommand%-30s Use the given docker daemon
       |  $synopsisToken%-30s $useCommand [USE_OPTIONS]
       |  USE_OPTIONS:
       |    ${dockerHostOpt + "=<docker host>"}%-28s the docker host such as tcp://localhost:2376
       |    ${dockerCertOpt + "=<docker certificate path>"}%-28s path to the certificate used to connect to the daemon if SSL is enabled
    """.stripMargin
  )

  private def showDaemonInfo(config: DockerDaemonConfig, client: ToscaRuntimeClient) = {
    println(s"Currently used daemon")
    println(s" - Host [${config.getHost}]")
    println(s" - TLS verify [${config.getTlsVerify}]")
    println(s" - Certificate path [${config.getCertPath}]")
    println(s" - Api version [${client.dockerVersion}]")
  }

  lazy val instance = Command(commandName, daemonHelp)(_ => daemonCmdParser) { (state, args) =>
    val basedir = state.attributes.get(Attributes.basedirAttribute).get
    val client = state.attributes.get(Attributes.clientAttribute).get
    var fail = false
    try {
      args match {
        case `useDefaultCommand` =>
          val defaultConfig = DockerUtil.getDefaultDockerDaemonConfig
          client.switchConnection(defaultConfig)
          switchConfiguration(defaultConfig, basedir)
          showDaemonInfo(defaultConfig, client)
        case `infoCommand` =>
          showDaemonInfo(DockerUtil.getDockerDaemonConfig(getConfiguration(basedir).asJava), client)
        case (`useCommand`, options: Seq[_]) =>
          val defaultConfig = DockerUtil.getDefaultDockerDaemonConfig
          val host = Args.getStringOption(options, dockerHostOpt).getOrElse(defaultConfig.getHost)
          val cert = Args.getStringOption(options, dockerCertOpt).getOrElse(defaultConfig.getCertPath)
          val tlsVerify = if (cert != null) "1" else null
          // TODO manage extra properties to configure the docker client
          val daemonConfig = new DockerDaemonConfig(host, tlsVerify, cert)
          switchConnection(client, daemonConfig, basedir)
          switchConfiguration(daemonConfig, basedir)
          showDaemonInfo(daemonConfig, client)
      }
    } catch {
      case e: Throwable =>
        println(s"Error ${e.getMessage}, see log for stack trace")
        logger.error("Command finished with error", e)
        fail = true
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
      ScalaFileUtil.copyRecursive(dockerConfigPath.resolve("provider.conf"), defaultDockerProviderConfigPath.resolve("auto_generated_provider.conf"))
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
