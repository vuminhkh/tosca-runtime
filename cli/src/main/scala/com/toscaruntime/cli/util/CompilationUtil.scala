package com.toscaruntime.cli.util

import com.toscaruntime.compiler.tosca.CompilationResult

object CompilationUtil {

  def showErrors(compilationResult: CompilationResult) = {
    compilationResult.errors.foreach {
      case (path, errors) =>
        println(s"Error at [${path.toString}]:")
        errors.foreach { error =>
          println(s"- Line [${error.startPosition.line}:${error.startPosition.column}] : ${error.error}")
        }
    }
  }
}
