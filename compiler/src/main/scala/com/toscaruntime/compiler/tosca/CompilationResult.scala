package com.toscaruntime.compiler.tosca

import java.nio.file.Path

import scala.util.parsing.input.Position

case class CompilationResult(compilationPath: Path, csars: Map[String, Csar], errors: Map[String, List[CompilationError]]) {
  def isSuccessful = {
    errors.isEmpty
  }
}

case class CompilationError(error: String, startPosition: Position, token: Option[String])
