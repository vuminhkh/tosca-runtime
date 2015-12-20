package com.toscaruntime.cli.command

import java.nio.file.{Files, Path, Paths}

import com.toscaruntime.cli.Attributes
import com.toscaruntime.cli.parser.Parsers
import com.toscaruntime.util.FileUtil
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import org.apache.commons.lang.StringUtils
import sbt.complete.DefaultParsers._
import sbt.{Command, Help}

import scala.collection.JavaConverters._

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
    val client = state.attributes.get(Attributes.clientAttribute).get
    if (!argsMap.contains(dockerUrlOpt)) {
      println(dockerUrlOpt + " is mandatory")
      fail = true
    } else {
      val basedir = state.attributes.get(Attributes.basedirAttribute).get
      val url = argsMap(dockerUrlOpt)
      val cert = argsMap.getOrElse(dockerCertOpt, null)
      client.switchConnection(url, cert)
      switchConfiguration(url, cert, basedir)
      println(argsMap(dockerUrlOpt) + " is using api version " + client.dockerVersion)
    }
    if (fail) {
      state.fail
    } else {
      println("Begin to use docker daemon at <" + argsMap(dockerUrlOpt) + ">" + " with api version <" + client.dockerVersion + ">")
      state
    }
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
      println("Copy certificates from <" + certPath + "> to <" + dockerCertPath + ">")
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
      println("Found existing configuration at <" + dockerConfigPath + ">")
      val providerConfig = ConfigFactory.parseFile(dockerConfigFilePath.toFile).resolveWith(
        ConfigFactory.empty().withValue("com.toscaruntime.provider.dir", ConfigValueFactory.fromAnyRef(dockerConfigPath.toString))
      )
      val config = providerConfig.entrySet().asScala.map { entry =>
        (entry.getKey, entry.getValue.unwrapped().asInstanceOf[String])
      }.toMap
      println("Existing Config <" + config + ">")
      config
    } else {
      Map.empty[String, String]
    }
  }
}
