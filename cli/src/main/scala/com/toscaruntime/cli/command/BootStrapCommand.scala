package com.toscaruntime.cli.command

import java.nio.file.Files

import com.toscaruntime.cli.util.{CompilationUtil, DeployUtil}
import com.toscaruntime.cli.{Args, Attributes}
import com.toscaruntime.compiler.Compiler
import com.toscaruntime.constant.ProviderConstant
import com.toscaruntime.util.FileUtil
import sbt.complete.DefaultParsers._
import sbt.{Command, Help}

import scala.language.postfixOps

/**
  * Bootstrap docker infrastructure
  *
  * @author Minh Khang VU
  */
object BootStrapCommand {

  val commandName = "bootstrap"

  private lazy val bootstrapArgsParser = Space ~> (Args.providerArg | Args.targetArg) +

  private lazy val bootstrapHelp = Help(commandName, (commandName, s"Bootstrap docker infrastructure, execute 'help $commandName' for more details"),
    s"""
       |$commandName ${Args.providerOpt} <provider name=${ProviderConstant.OPENSTACK}> ${Args.targetOpt} <target=${ProviderConstant.DEFAULT_TARGET}>
       |${Args.providerOpt}   : name of the provider
       |${Args.targetOpt}     : target/configuration for the provider
    """.stripMargin
  )

  lazy val instance = Command(commandName, bootstrapHelp)(_ => bootstrapArgsParser) { (state, args) =>
    val argsMap = args.toMap
    var fail = false
    val client = state.attributes.get(Attributes.clientAttribute).get
    val basedir = state.attributes.get(Attributes.basedirAttribute).get
    val workDir = basedir.resolve("work")

    val providerName = argsMap.getOrElse(Args.providerOpt, ProviderConstant.OPENSTACK)
    val target = argsMap.getOrElse(Args.targetOpt, ProviderConstant.DEFAULT_TARGET)
    val bootstrapTopology = basedir.resolve("bootstrap").resolve(providerName).resolve(target).resolve("archive")
    val bootstrapInputPath = basedir.resolve("bootstrap").resolve(providerName).resolve(target).resolve("inputs.yml")
    val providerConfigurationPath = basedir.resolve("conf").resolve("providers").resolve(providerName).resolve(target)

    val deploymentWorkDir = workDir.resolve("bootstrap_" + providerName + "_" + target)
    if (Files.exists(deploymentWorkDir)) {
      FileUtil.delete(deploymentWorkDir)
    }
    Files.createDirectories(deploymentWorkDir)

    if (!Files.exists(bootstrapInputPath)) {
      println(s"WARNING: No input found at [$bootstrapInputPath] for provider [$providerName] and target [$target]")
      println(s"WARNING: It's either bad practice or the bootstrap operation will fail due to missing inputs")
    }
    if (!Files.exists(bootstrapTopology)) {
      println(s"Invalid provider [$providerName] or target [$target], no topology found at [$bootstrapTopology]")
      fail = true
    } else if (!Files.exists(providerConfigurationPath)) {
      println(s"Provider configuration is missing for [$providerName] or target [$target]")
      fail = true
    } else {
      val inputsPath = if (Files.exists(bootstrapInputPath)) Some(bootstrapInputPath) else None
      val compilationResult = Compiler.assembly(bootstrapTopology, deploymentWorkDir, basedir.resolve("repository"), inputsPath)
      if (compilationResult.isSuccessful) {
        val image = client.createBootstrapImage(
          providerName,
          deploymentWorkDir,
          inputsPath,
          providerConfigurationPath, target).awaitImageId()
        println(s"Packaged bootstrap configuration as docker image [$image]")
        val containerId = client.createBootstrapAgent(providerName, target).getId
        val logCallback = client.tailContainerLog(containerId, System.out)
        try {
          DeployUtil.waitForBootstrapAgent(client, providerName, target)
          val details = DeployUtil.bootstrap(client, providerName, target)
          DeployUtil.printDetails(s"Bootstrapped [$providerName]", details)
        } finally {
          logCallback.close()
        }
      } else {
        println("Failed to compile bootstrap topology")
        CompilationUtil.showErrors(compilationResult)
        fail = true
      }
    }
    if (fail) state.fail else state
  }

}
