package com.toscaruntime.test

import java.nio.file.{Files, Paths}

import com.typesafe.config.ConfigFactory
import org.yaml.snakeyaml.Yaml

object ConfigLoader {

  def loadConfig(provider: String) = {
    val providerConfigPath = Paths.get("test-config").resolve("conf").resolve("providers").resolve(provider).resolve("default").resolve("provider.conf")
    if (!Files.isRegularFile(providerConfigPath)) {
      throw new AssertionError(s"For integration test, please configure provider $provider at $providerConfigPath")
    }
    ConfigFactory.parseFile(providerConfigPath.toFile).root().unwrapped()
  }

  private val yamlParser = new Yaml()

  def loadInput(inputName: String) = {
    val inputPath = Paths.get("test-config").resolve("inputs").resolve(inputName + ".yaml")
    if (!Files.isRegularFile(inputPath)) {
      throw new AssertionError(s"For integration test, please configure input $inputName at $inputPath")
    }
    yamlParser.loadAs(Files.newInputStream(inputPath), classOf[java.util.Map[String, AnyRef]])
  }
}
