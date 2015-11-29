package com.toscaruntime.cli.command

import java.nio.file.Files

import com.toscaruntime.cli.util.DeployUtil
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

  private lazy val bootstrapArgsParser = Space ~> Args.providerArg +

  private lazy val bootstrapHelp = Help("bootstrap", ("bootstrap", "Bootstrap docker infrastructure, execute 'help bootstrap' for more details"),
    """
      |bootstrap -p <provider name>
      |-p   : name of the provider
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
      val bootstrapTopology = basedir.resolve("bootstrap").resolve(providerName).resolve("default").resolve("archive")
      val bootstrapInputPath = basedir.resolve("bootstrap").resolve(providerName).resolve("default").resolve("inputs.yml")
      val dockerComponentCsar = basedir.resolve("bootstrap").resolve("common").resolve("docker")
      val compilationSuccessful = Compiler.compile(
        bootstrapTopology,
        List(dockerComponentCsar),
        basedir.resolve("providers").resolve(providerName),
        basedir.resolve("sdk"),
        outputPath
      )
      if (compilationSuccessful) {
        val providerConfigurationPath = basedir.resolve("conf").resolve("providers").resolve(argsMap(Args.providerOpt)).resolve("default")
        val image = client.createBootstrapImage(
          providerName,
          outputPath,
          if (Files.exists(bootstrapInputPath)) Some(bootstrapInputPath) else None,
          providerConfigurationPath).awaitImageId()
        println("Packaged bootstrap configuration as docker image <" + image + ">")
        val containerId = client.createBootstrapAgent(providerName).getId
        val logCallback = client.tailContainerLog(containerId, System.out)
        try {
          DeployUtil.waitForBootstrapAgent(client, providerName)
          val details = DeployUtil.bootstrap(client, providerName)
          DeployUtil.printDetails("Bootstrap " + providerName, details)
        } finally {
          logCallback.close()
        }
      } else {
        println("Failed to compile bootstrap topology")
        fail = true
      }
    }
    if (fail) {
      state.fail
    } else {
      state
    }
  }

}
