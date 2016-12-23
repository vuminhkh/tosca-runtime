package com.toscaruntime.cli.command

import java.io.FileNotFoundException
import java.nio.file.{Files, Path}

import com.toscaruntime.cli.util.PluginUtil._
import com.toscaruntime.cli.util.{AgentUtil, CompilationUtil}
import com.toscaruntime.cli.{Args, Attributes}
import com.toscaruntime.compiler.Compiler
import com.toscaruntime.constant.ProviderConstant
import com.toscaruntime.exception.compilation.CompilationException
import com.toscaruntime.rest.client.ToscaRuntimeClient
import com.toscaruntime.util.FileUtil
import com.typesafe.scalalogging.LazyLogging
import sbt.complete.DefaultParsers._
import sbt.{Command, Help}

import scala.language.postfixOps

/**
  * Bootstrap docker infrastructure
  *
  * @author Minh Khang VU
  */
object BootStrapCommand extends LazyLogging {

  val commandName = "bootstrap"

  private lazy val bootstrapCmdParser = Space ~> (Args.providerOptParser | Args.targetOptParser) +

  private lazy val bootstrapHelp = Help(commandName, (commandName, s"Bootstrap docker infrastructure, execute 'help $commandName' for more details"),
    f"""
       |$commandName ${Args.providerOpt}=<provider name> ${Args.targetOpt}=<target>
       |OPTIONS:
       |  ${Args.providerOpt}%-30s name of the provider, default value is ${ProviderConstant.OPENSTACK}
       |  ${Args.targetOpt}%-30s target/configuration for the provider, default values is ${ProviderConstant.DEFAULT_TARGET}
    """.stripMargin
  )

  def createBootstrapAgent(providerName: String,
                           fromImage: String,
                           target: String,
                           client: ToscaRuntimeClient,
                           workDir: Path,
                           bootstrapTopology: Path,
                           providerConfigPath: Path,
                           pluginConfigBasePath: Path,
                           repositoryPath: Path,
                           bootstrapInputPath: Option[Path]) = {
    val deploymentWorkDir = workDir.resolve("bootstrap_" + providerName + "_" + target)
    if (Files.exists(deploymentWorkDir)) {
      FileUtil.delete(deploymentWorkDir)
    }
    Files.createDirectories(deploymentWorkDir)
    if (!Files.exists(bootstrapTopology)) {
      println(s"Invalid provider [$providerName] or target [$target], no topology found at [$bootstrapTopology]")
      throw new FileNotFoundException(s"Invalid provider [$providerName] or target [$target], no topology found at [$bootstrapTopology]")
    } else if (!isProviderConfigValid(providerConfigPath)) {
      println(s"Provider configuration is missing for [$providerName], please check [$providerConfigPath]")
      throw new FileNotFoundException(s"Provider configuration is missing for [$providerName], please check [$providerConfigPath]")
    } else {
      val compilationResult = Compiler.assembly(bootstrapTopology, deploymentWorkDir, repositoryPath, bootstrapInputPath)
      if (compilationResult.isSuccessful) {
        val pluginConfigPaths = compilationResult.plugins.map(pluginConfigBasePath.resolve)
        val missingPluginConfigs = pluginConfigPaths.filter(!isPluginConfigValid(_))
        if (missingPluginConfigs.nonEmpty) {
          missingPluginConfigs.foreach(pluginConfigPath => println(s"Missing plugin configuration at $pluginConfigPath"))
          throw new FileNotFoundException("Missing plugin config " + missingPluginConfigs.mkString(", "))
        }
        val image = client.createBootstrapImage(
          providerName,
          fromImage,
          deploymentWorkDir,
          List(providerConfigPath),
          pluginConfigPaths,
          target
        ).awaitImageId()
        println(s"Packaged bootstrap configuration as docker image [$image]")
        client.createBootstrapAgent(providerName, target).getId
      } else {
        println("Failed to compile bootstrap topology")
        CompilationUtil.showErrors(compilationResult)
        throw new CompilationException("Compilation failed")
      }
    }
  }

  lazy val instance = Command(commandName, bootstrapHelp)(_ => bootstrapCmdParser) { (state, args) =>
    val argsMap = args.toMap
    var fail = false
    val client = state.attributes.get(Attributes.clientAttribute).get
    val basedir = state.attributes.get(Attributes.basedirAttribute).get
    val config = state.attributes.get(Attributes.config).get

    val providerName = argsMap.getOrElse(Args.providerOpt, ProviderConstant.OPENSTACK)
    val target = argsMap.getOrElse(Args.targetOpt, ProviderConstant.DEFAULT_TARGET)
    val bootstrapInputPath = basedir.resolve("bootstrap").resolve(providerName).resolve(target).resolve("inputs.yaml")
    if (!Files.exists(bootstrapInputPath)) {
      println(s"WARNING: No input found at [$bootstrapInputPath] for provider [$providerName] and target [$target]")
      println(s"WARNING: It's either bad practice or the bootstrap operation will fail due to missing inputs")
    }
    try {
      val providerConfigPath = basedir.resolve("conf").resolve("providers").resolve(providerName)
      val pluginConfBase = basedir.resolve("conf").resolve("plugins")
      val bootstrapTopology = basedir.resolve("bootstrap").resolve(providerName).resolve(target).resolve("archive")
      val workDir = basedir.resolve("work")
      val repositoryPath = basedir.resolve("repository")
      val fromImage = config.getString("deployer.image")
      val agentContainerId = createBootstrapAgent(providerName, fromImage, target, client, workDir, bootstrapTopology, providerConfigPath, pluginConfBase, repositoryPath, if (Files.exists(bootstrapInputPath)) Some(bootstrapInputPath) else None)
      val logCallback = client.tailContainerLog(agentContainerId, System.out)
      try {
        AgentUtil.waitForBootstrapAgent(client, providerName, target)
        val details = AgentUtil.bootstrap(client, providerName, target)
        AgentUtil.printDetails(s"Bootstrapped [$providerName]", details)
      } finally {
        logCallback.close()
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
