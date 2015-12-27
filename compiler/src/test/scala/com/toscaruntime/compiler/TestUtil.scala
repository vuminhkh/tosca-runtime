package com.toscaruntime.compiler

import com.typesafe.scalalogging.LazyLogging

object TestUtil extends LazyLogging {

  def printResult(result: SyntaxAnalyzer.ParseResult[_]) = {
    result match {
      case success: SyntaxAnalyzer.Success[_] => logger.info("Parsing success {}", success)
      case error: SyntaxAnalyzer.Error =>
        logger.error("Parsing error {}", error)
      case failure: SyntaxAnalyzer.Failure =>
        logger.error("Parsing failure {}", failure)
    }
  }
}
