package com.toscaruntime.compiler.tosca

import java.nio.file.Path

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
}

case class CompilationError(error: String, startPosition: Position, token: Option[String])
