package com.toscaruntime.cli

import java.nio.file.Path

import com.toscaruntime.rest.client.ToscaRuntimeClient
import com.typesafe.config.Config
import sbt.AttributeKey

/**
  * Hold tosca runtime cli attributes keys
  *
  * @author Minh Khang VU
  */
object Attributes {

  val clientAttribute: AttributeKey[ToscaRuntimeClient] = AttributeKey[ToscaRuntimeClient]("client")

  val basedirAttribute: AttributeKey[Path] = AttributeKey[Path]("basedir")

  val config: AttributeKey[Config] = AttributeKey[Config]("config")
}
