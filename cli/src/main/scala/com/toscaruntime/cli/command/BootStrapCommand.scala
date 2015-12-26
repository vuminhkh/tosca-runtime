package com.toscaruntime.cli.command

import java.nio.file.Files

import com.toscaruntime.cli.util.{CompilationUtil, DeployUtil}
import com.toscaruntime.cli.{Args, Attributes}
import com.toscaruntime.compiler.Compiler
import sbt.complete.DefaultParsers._
import sbt.{Command, Help}

/**
  * Bootstrap docker infrastructure
  *
  * @author Minh Khang VU
  */
object BootStrapCommand {

  private lazy val bootstrapArgsParser = Space ~> (Args.providerArg | Args.targetArg) +

  private lazy val bootstrapHelp = Help("bootstrap", ("bootstrap", "Bootstrap docker infrastructure, execute 'help bootstrap' for more details"),
    """
      |bootstrap -p <provider name> -t <target>
      |-p   : name of the provider
      |-t   : target/configuration for the provider
    """.stripMargin
  )

  lazy val instance = Command("bootstrap", bootstrapHelp)(_ => bootstrapArgsParser) { (state, args) =>
    val argsMap = args.toMap
    var fail = false
    if (!argsMap.contains(Args.providerOpt)) {
      println(Args.providerOpt + " is mandatory")
      fail = true
    } else {
      val client = state.attributes.get(Attributes.clientAttribute).get
      val basedir = state.attributes.get(Attributes.basedirAttribute).get
      val outputPath = Files.createTempDirectory("toscaruntime")
      val providerName = argsMap.get(Args.providerOpt).get
      val target = argsMap.getOrElse(Args.targetOpt, "default")
      val bootstrapTopology = basedir.resolve("bootstrap").resolve(providerName).resolve(target).resolve("archive")
      val bootstrapInputPath = basedir.resolve("bootstrap").resolve(providerName).resolve(target).resolve("inputs.yml")
      val compilationResult = Compiler.assembly(bootstrapTopology, outputPath, basedir.resolve("repository"))
      if (compilationResult.isSuccessful) {
        val providerConfigurationPath = basedir.resolve("conf").resolve("providers").resolve(providerName).resolve(target)
        val image = client.createBootstrapImage(
          providerName,
          outputPath,
          if (Files.exists(bootstrapInputPath)) Some(bootstrapInputPath) else None,
          providerConfigurationPath, target).awaitImageId()
        println("Packaged bootstrap configuration as docker image <" + image + ">")
        val containerId = client.createBootstrapAgent(providerName, target).getId
        val logCallback = client.tailContainerLog(containerId, System.out)
        try {
          DeployUtil.waitForBootstrapAgent(client, providerName, target)
          val details = DeployUtil.bootstrap(client, providerName, target)
          DeployUtil.printDetails("Bootstrap " + providerName, details)
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
