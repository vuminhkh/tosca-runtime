package com.mkv.tosca.cli.parser

import com.mkv.tosca.cli.parser.completion.FileCompletion
import sbt.complete.DefaultParsers._
import sbt.complete._

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
