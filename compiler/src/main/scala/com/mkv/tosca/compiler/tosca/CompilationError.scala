package com.mkv.tosca.compiler.tosca

import scala.util.parsing.input.Position

case class CompilationError(error: String, startPosition: Position, token: String)
