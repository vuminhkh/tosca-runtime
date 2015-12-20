package com.toscaruntime.compiler

import com.typesafe.scalalogging.LazyLogging

object TestUtil extends LazyLogging {

  def printResult(result: SyntaxAnalyzer.ParseResult[_]) = {
    result match {
      case success: SyntaxAnalyzer.Success[_] => logger.info("Parsing success {}", success)
      case error: SyntaxAnalyzer.Error => logger.info("Parsing error, message {}, line {} column {}", error.msg, error.next.pos.line.toString, error.next.pos.column.toString)
      case failure: SyntaxAnalyzer.Failure => logger.info("Parsing failure, message {}, line {} column {}", failure.msg, failure.next.pos.line.toString, failure.next.pos.column.toString)
    }
  }
}
