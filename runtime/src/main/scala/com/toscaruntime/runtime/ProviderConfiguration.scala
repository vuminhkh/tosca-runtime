package com.toscaruntime.runtime

/**
  * Configuration object for a provider which contains configurations for all of its target
  *
  * @param name    provider's name
  * @param targets list of targets
  */
case class ProviderConfiguration(name: String, targets: List[TargetConfiguration])

/**
  * Target's configuration
  *
  * @param name          target's name
  * @param configuration target's properties
  */
case class TargetConfiguration(name: String, configuration: java.util.Map[String, AnyRef])

/**
  * Configuration object for a plugin which contains configurations for all of its target
  *
  * @param name    plugin's name
  * @param targets list of targets
  */
case class PluginConfiguration(name: String, targets: List[TargetConfiguration])
