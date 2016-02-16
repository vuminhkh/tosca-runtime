package com.toscaruntime.cli.command

import com.toscaruntime.cli.util.AgentUtil
import com.toscaruntime.cli.{Args, Attributes}
import com.toscaruntime.constant.ProviderConstant
import sbt.complete.DefaultParsers._
import sbt.{Command, Help}

import scala.language.postfixOps

/**
  * Teardown bootstrap installation
  */
object TeardownCommand {

  val commandName = "teardown"

  private lazy val teardownArgsParser = Space ~> (Args.providerArg | Args.targetArg) +

  private lazy val teardownHelp = Help(commandName, (commandName, s"Tear down bootstrap installation, execute 'help $commandName' for more details"),
    s"""
       |$commandName ${Args.providerOpt} <provider name=${ProviderConstant.OPENSTACK}> ${Args.targetOpt} <target=${ProviderConstant.DEFAULT_TARGET}>
       |${Args.providerOpt}   : name of the provider
       |${Args.targetOpt}     : target/configuration for the provider
    """.stripMargin
  )

  lazy val instance = Command("teardown", teardownHelp)(_ => teardownArgsParser) { (state, args) =>
    val argsMap = args.toMap
    val client = state.attributes.get(Attributes.clientAttribute).get
    val providerName = argsMap.getOrElse(Args.providerOpt, ProviderConstant.OPENSTACK)
    val target = argsMap.getOrElse(Args.targetOpt, ProviderConstant.DEFAULT_TARGET)
    val logCallback = client.tailBootstrapLog(providerName, target, System.out)
    try {
      val details = AgentUtil.teardown(client, providerName, target)
      client.deleteBootstrapAgent(providerName, target)
      client.deleteBootstrapImage(providerName, target)
      AgentUtil.printDetails(s"Bootstrap $providerName", details)
    } finally {
      logCallback.close()
    }
    state
  }
}
