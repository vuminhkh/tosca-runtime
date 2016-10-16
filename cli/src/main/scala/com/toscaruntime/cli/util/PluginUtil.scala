package com.toscaruntime.cli.util

import java.nio.file.{Files, Path}

import com.toscaruntime.util.ScalaFileUtil

object PluginUtil {

  def isProviderConfigValid(configPath: Path) = {
    Files.isDirectory(configPath) && ScalaFileUtil.listDirectories(configPath).forall(isProviderTargetConfigValid)
  }

  def isPluginConfigValid(configPath: Path) = {
    Files.isDirectory(configPath) && ScalaFileUtil.listDirectories(configPath).forall(isPluginTargetConfigValid)
  }

  def isProviderTargetConfigValid(configPath: Path) = {
    Files.isDirectory(configPath) && (Files.isRegularFile(configPath.resolve("provider.conf")) || Files.isRegularFile(configPath.resolve("auto_generated_provider.conf")))
  }

  def isPluginTargetConfigValid(configPath: Path) = {
    Files.isDirectory(configPath) && Files.isRegularFile(configPath.resolve("plugin.conf"))
  }
}
