package com.toscaruntime.compiler.tosca

import java.nio.file.Path

import com.toscaruntime.compiler.util.CompilerUtil

import scala.util.parsing.input.Position

case class CompilationResult(
                              path: Path,
                              csar: Csar,
                              dependencies: Map[String, Csar] = Map.empty,
                              errors: Map[String, List[CompilationError]] = Map.empty
                            ) {
  def isSuccessful = {
    errors.isEmpty
  }

  def providers = {
    dependencies
      .filter(dependencyEntry => CompilerUtil.isProviderTypes(dependencyEntry._1))
      .values.map(csar => CompilerUtil.pluginNameFromCsarName(csar.csarName)).toList
  }

  def plugins = {
    dependencies
      .filter(dependencyEntry => CompilerUtil.isPluginTypes(dependencyEntry._1))
      .values.map(csar => CompilerUtil.pluginNameFromCsarName(csar.csarName)).toList
  }
}

case class CompilationError(error: String, startPosition: Position, token: Option[String])
