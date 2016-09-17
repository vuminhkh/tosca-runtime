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

  val providerOptParser = token(providerOpt) ~ (token("=") ~> (token(ProviderConstant.DOCKER) | token(ProviderConstant.OPENSTACK) | token(ProviderConstant.AWS)))

  val targetOpt = "--target"

  val targetOptParser = token(targetOpt) ~ (token("=") ~> token(StringBasic))

  def filterByOptionName(opts: Seq[_], optionName: String) = {
    opts.filter {
      case (`optionName`, _) => true
      case _ => false
    }
  }

  def getMapOption(opts: Seq[_], optionName: String) = {
    filterByOptionName(opts, optionName).map {
      case (`optionName`, (key: String, value: String)) => (key, value)
    }.toMap
  }

  def getTupleOption(opts: Seq[_], optionName: String) = {
    filterByOptionName(opts, optionName).map {
      case (`optionName`, (key: String, value: String)) => (key, value)
    }.headOption
  }

  def getStringOption(opts: Seq[_], optionName: String) = {
    filterByOptionName(opts, optionName).map {
      case (`optionName`, interface: String) => interface
    }.headOption
  }

  def getFlagOption(opts: Seq[_], optionName: String) = {
    opts.contains(optionName)
  }

  def getBooleanOption(opts: Seq[_], optionName: String) = {
    filterByOptionName(opts, optionName).map {
      case (`optionName`, flag: String) => flag.toBoolean
    }.headOption
  }
}
