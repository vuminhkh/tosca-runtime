package com.mkv.tosca.cli.command

import java.nio.file.Files

import com.google.common.collect.Lists
import com.mkv.tosca.cli.{Args, Attributes}
import com.mkv.tosca.compiler.{Compiler, Packager}
import com.mkv.tosca.util.DockerUtil
import sbt.complete.DefaultParsers._
import sbt.{Command, Help}

/**
 * Bootstrap docker infrastructure
 *
 * @author Minh Khang VU
 */
object BootStrapCommand {

  private lazy val bootstrapArgsParser = Space ~> Args.providerArg +

  private lazy val bootstrapHelp = Help("bootstrap", ("bootstrap", "Bootstrap docker infrastructure"),
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
      val dockerClient = state.attributes.get(Attributes.dockerDaemonAttribute).get.dockerClient
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
        val image = Packager.createDockerImage(
          dockerClient,
          "bootstrap_" + providerName,
          bootstrap = true,
          outputPath,
          if (Files.exists(bootstrapInputPath)) Some(bootstrapInputPath) else None,
          providerConfigurationPath
        )
        println("Packaged bootstrap configuration as docker image with name <" + image._2 + "> and id <" + image._1 + ">")
        val containerId = dockerClient.createContainerCmd(image._1).withName(image._2 + "_agent").exec.getId
        dockerClient.startContainerCmd(containerId).exec
        var retryCount = 0
        var webAppUp = false
        while (!webAppUp) {
          try {
            // Try to get the deployment information just to be sure that the web app is up
            DockerUtil.runCommand(dockerClient, containerId, Lists.newArrayList("curl", "-X", "GET", "http://0.0.0.0:9000/deployment"), null)
            webAppUp = true
          } catch {
            case t: Throwable =>
              retryCount += 1
              if (retryCount < 60) {
                println("Attempt " + retryCount + " to launch bootstrap, web app may not be up yet")
                Thread.sleep(1000L)
              } else {
                throw t
              }
          }
        }
        // TODO Use curl to launch deployment, may we have more elegant ways
        DockerUtil.runCommand(dockerClient, containerId, Lists.newArrayList("curl", "-X", "POST", "http://0.0.0.0:9000/deployment")).close()
        println("Launched bootstrap operation asynchronously, follow installation by performing 'log -c " + image._2 + "_agent'")
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
