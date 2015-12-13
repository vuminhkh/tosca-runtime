package com.toscaruntime.cli.command

import com.toscaruntime.cli.util.DeployUtil
import com.toscaruntime.cli.{Args, Attributes}
import sbt.complete.DefaultParsers._
import sbt.{Command, Help}

/**
  * Teardown bootstrap installation
  */
object TeardownCommand {

  private lazy val teardownArgsParser = Space ~> (Args.providerArg | Args.targetArg) +

  private lazy val teardownHelp = Help("teardown", ("teardown", "Tear down bootstrap installation, execute 'help teardown' for more details"),
    """
      |teardown -p <provider name> -t <target>
      |-p   : name of the provider
      |-t   : target/configuration for the provider
    """.stripMargin
  )

  lazy val instance = Command("teardown", teardownHelp)(_ => teardownArgsParser) { (state, args) =>
    val argsMap = args.toMap
    var fail = false
    val client = state.attributes.get(Attributes.clientAttribute).get
    if (!argsMap.contains(Args.providerOpt)) {
      println(Args.providerOpt + " is mandatory")
      fail = true
    } else {
      val providerName = argsMap(Args.providerOpt)
      val target = argsMap.getOrElse(Args.targetOpt, "default")
      val logCallback = client.tailBootstrapLog(providerName, target, System.out)
      try {
        val details = DeployUtil.teardown(client, providerName, target)
        client.deleteBootstrapAgent(providerName, target)
        client.deleteBootstrapImage(providerName, target)
        DeployUtil.printDetails("Bootstrap " + providerName, details)
      } finally {
        logCallback.close()
      }
    }
    if (fail) {
      state.fail
    } else {
      state
    }
  }
}
