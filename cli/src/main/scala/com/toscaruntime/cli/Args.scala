package com.toscaruntime.cli

import sbt.complete.DefaultParsers._

/**
 * Common arguments for tosca runtime
 *
 * @author Minh Khang VU
 */
object Args {

  val providerOpt = "-p"
  val providerArg = token(providerOpt) ~ (Space ~> (token("docker") | token("openstack")))

}
