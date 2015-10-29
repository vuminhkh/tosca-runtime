package com.mkv.tosca.cli

import java.nio.file.Path

import com.github.dockerjava.api.DockerClient
import sbt.AttributeKey

/**
 * Hold tosca runtime cli attributes keys
 *
 * @author Minh Khang VU
 */
object Attributes {

  val dockerDaemonAttribute: AttributeKey[DockerClient] = AttributeKey[DockerClient]("docker_daemon")

  val basedirAttribute: AttributeKey[Path] = AttributeKey[Path]("basedir")

}
