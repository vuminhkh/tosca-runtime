package com.mkv.tosca.runtime

import java.nio.file.Paths

import com.typesafe.config._

object ToscarApp {

  val Config = ConfigFactory.load()

  def workSpaceDir() = {
    Paths.get(Config.getString("toscar.runtime.workSpace"))
  }

  def deploymentsDir() = {
    workSpaceDir().resolve(Config.getString("toscar.runtime.deploymentFolder"))
  }
}
