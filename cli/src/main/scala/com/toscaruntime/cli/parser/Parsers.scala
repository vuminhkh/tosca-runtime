package com.toscaruntime.cli.parser

import com.toscaruntime.cli.parser.completion.FileCompletion
import sbt.complete.DefaultParsers._
import sbt.complete._
import scala.language.postfixOps

/**
 * Custom parsers for cli command
 *
 * @author Minh Khang VU
 */
object Parsers {

  lazy val nonQuotedFileCharacterClass = charClass({ c: Char =>
    (c != DQuoteChar) && !c.isWhitespace && c != '?' && c != '%' && c != '*' && c != ':' && c != '|' && c != '<' && c != '>'
  }, "non-double-quote-file-name character")

  lazy val nonQuotedFilePath = (nonQuotedFileCharacterClass +).map(chars => new String(chars.toArray))

  lazy val filePathParser: Parser[String] = (StringVerbatim | StringEscapable | nonQuotedFilePath).examples(new FileCompletion())
}
