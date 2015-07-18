package com.mkv.tosca.compiler.model

import scala.util.parsing.input.Position

case class CompilationError(error: String, startPosition: Position, token: String)
