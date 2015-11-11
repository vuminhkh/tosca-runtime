package com.toscaruntime.cli

import java.nio.file.Path

import sbt.AttributeKey

/**
 * Hold tosca runtime cli attributes keys
 *
 * @author Minh Khang VU
 */
object Attributes {

  val dockerDaemonAttribute: AttributeKey[DockerClientHolder] = AttributeKey[DockerClientHolder]("docker_daemon")

  val basedirAttribute: AttributeKey[Path] = AttributeKey[Path]("basedir")

}
