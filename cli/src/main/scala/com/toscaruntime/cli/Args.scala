package com.toscaruntime.cli

import com.toscaruntime.constant.ProviderConstant
import sbt.complete.DefaultParsers._

/**
  * Common arguments for tosca runtime
  *
  * @author Minh Khang VU
  */
object Args {

  val providerOpt = "--provider"

  val providerOptParser = token(providerOpt) ~ (Space ~> (token(ProviderConstant.DOCKER) | token(ProviderConstant.OPENSTACK) | token(ProviderConstant.AWS)))

  val targetOpt = "--target"

  val targetOptParser = token(targetOpt) ~ (Space ~> token(StringBasic))
}
