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
    dependencies.values
      .filter(csar => CompilerUtil.isProviderTypes(csar.csarName))
      .map(csar => CompilerUtil.pluginNameFromCsarName(csar.csarName)).toList
  }

  def plugins = {
    dependencies.values
      .filter(csar => CompilerUtil.isPluginTypes(csar.csarName))
      .map(csar => CompilerUtil.pluginNameFromCsarName(csar.csarName)).toList
  }
}

case class CompilationError(error: String, startPosition: Position, token: Option[String])
