package com.toscaruntime.cli

import com.toscaruntime.constant.ProviderConstant
import sbt.complete.DefaultParsers._

/**
  * Common arguments for tosca runtime
  *
  * @author Minh Khang VU
  */
object Args {

  val providerOpt = "-p"

  val providerArg = token(providerOpt) ~ (Space ~> (token(ProviderConstant.DOCKER) | token(ProviderConstant.OPENSTACK)))

  val targetOpt = "-t"

  val targetArg = token(targetOpt) ~ (Space ~> token(StringBasic))
}
