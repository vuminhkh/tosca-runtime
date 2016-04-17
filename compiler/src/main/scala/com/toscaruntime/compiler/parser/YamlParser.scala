package com.toscaruntime.compiler.parser

import com.toscaruntime.compiler.tosca._

import scala.util.parsing.combinator.{JavaTokenParsers, PackratParsers}

trait YamlParser extends JavaTokenParsers with PackratParsers {

  override def skipWhitespace = false

  val keyPattern = regex( """\w+[^:\[\]\{\}>|\p{Blank},]*""".r).withFailureMessage("Expecting yaml key")

  val nestedListStartPattern = regex( """\[ *""".r).withFailureMessage("Expecting '[' to start an inline list")

  val nestedListEndPattern = regex( """ *\]""".r).withFailureMessage("Expecting ']' to end an inline list")

  val nestedMapStartPattern = regex( """\{ *""".r).withFailureMessage("Expecting '{' to start an inline complex object")

  val nestedMapEndPattern = regex( """ *\}""".r).withFailureMessage("Expecting '}' to end an inline complex object")

  val nestedEntrySeparator = regex( """\s*,\s*""".r).withFailureMessage("Expecting ',' to separate nested map or list entry")

  val nonQuotedTextValuePattern = regex( """[^\[\]\{\}\r\n#][^#\r\n]*""".r).withFailureMessage("Expecting non quoted text")

  val nestedNonQuotedTextValuePattern = regex( """[^,\[\]\{\}\r\n#]*""".r).withFailureMessage("Expecting nested non quoted text")

  val quotedTextValuePattern = regex( """"[^"\r\n]*"""".r).withFailureMessage("Expecting quoted text")

  val nullValuePattern = regex( """(?:null|~)""".r).withFailureMessage("Expecting null value")

  val trueValueToken = regex( """true""".r).withFailureMessage("Expecting true")

  val falseValueToken = regex( """false""".r).withFailureMessage("Expecting false")

  val commentRegex = """\p{Blank}*(?:#[^\r\n]*)?"""

  val endOfLineRegex = """(?:(?:\r?\n)|\Z)"""

  val blankLineRegex = commentRegex + endOfLineRegex

  val lineEndingRegex = s"""(?:$blankLineRegex)+"""

  val lineEndingPattern = regex(lineEndingRegex.r).withFailureMessage("Unexpected token, expecting new line or end of file")

  val keyValueSeparatorPattern = regex( """: """.r).withFailureMessage("Expecting ': ' to separate key and value")

  val keyLongTextSeparatorPattern = regex( """>[ \t]*\r?\n(?:\r?\n)*""".r).withFailureMessage("Expecting '>' to start a long text")

  val keyLongTextWithNewLineSeparatorPattern = regex( """\|[ \t]*\r?\n(?:\r?\n)*""".r).withFailureMessage("Expecting '|' to start a multilines text")

  val keyComplexSeparatorPattern = regex((""":""" + lineEndingRegex).r).withFailureMessage("Expecting ':' to start a complex object")

  def listIndicator = regex("""- """.r).withFailureMessage("Expecting '- ' for list entry")

  def indentAtLeast(numberOfWhitespaces: Int): Parser[Int] = regex(("^ {" + numberOfWhitespaces + ",}").r).withFailureMessage(s"Expecting at least $numberOfWhitespaces white space for indentation") ^^ (_.length)

  def indent(numberOfWhitespaces: Int): Parser[Int] = regex(("^ {" + numberOfWhitespaces + "}").r).withFailureMessage(s"Expecting exactly $numberOfWhitespaces white space for indentation") ^^ (_.length)

  def mapWithoutFirstIndentation[T](mapEntryParser: (Int => Parser[(ParsedValue[String], T)]))(indentLength: Int): Parser[Map[ParsedValue[String], T]] =
    (mapEntryParser(indentLength) ~ opt(rep1(indent(indentLength) ~> mapEntryParser(indentLength)))) ^^ {
      case (firstEntry ~ None) => Map(firstEntry)
      case (firstEntry ~ Some(entryList)) => ((List() :+ firstEntry) ++ entryList).toMap
    }

  def map[T](mapEntryParser: (Int => Parser[(ParsedValue[String], T)]))(minIndent: Int): Parser[Map[ParsedValue[String], T]] =
    (indentAtLeast(minIndent) into (newIndentLength => mapWithoutFirstIndentation(mapEntryParser)(newIndentLength))) | failure("Expecting complex object")

  def nestedMap[T](mapEntryParser: Parser[(ParsedValue[String], T)]): Parser[Map[ParsedValue[String], T]] =
    ((nestedMapStartPattern ~> repsep(mapEntryParser, nestedEntrySeparator) <~ nestedMapEndPattern) ^^ (_.toMap)) | failure("Expecting nested complex object")

  def listWithoutFirstIndentation[T](listEntryParser: (Int => Parser[T]))(indentLength: Int): Parser[List[T]] = {
    val newListEntryLevel = indentLength + 2
    listIndicator ~> listEntryParser(newListEntryLevel) ~ opt(rep1(indent(indentLength) ~> listIndicator ~> listEntryParser(newListEntryLevel)))
  } ^^ {
    case (firstEntry ~ None) => List(firstEntry)
    case (firstEntry ~ Some(entryList)) => (List() :+ firstEntry) ++ entryList
  }

  def list[T](listEntryParser: (Int => Parser[T]))(indentLevel: Int): Parser[List[T]] =
    (indentAtLeast(indentLevel) into { newIndentLength =>
      listWithoutFirstIndentation(listEntryParser)(newIndentLength)
    }) | failure("Expecting list")

  def nestedList[T](listEntryParser: Parser[T]): Parser[List[T]] =
    ((nestedListStartPattern ~> repsep(listEntryParser, nestedEntrySeparator) <~ nestedListEndPattern) ^^ (_.toList)) | failure("Expecting nested list")

  def textValueWithSeparator(indentLevel: Int) =
    (((keyLongTextSeparatorPattern ~> longTextValue(indentLevel + 1, withNewLine = false)) |
      (keyLongTextWithNewLineSeparatorPattern ~> longTextValue(indentLevel + 1, withNewLine = true)) |
      textValue) <~ lineEndingPattern) | failure("Expecting text value")

  def textEntry(key: String)(indentLevel: Int): Parser[(ParsedValue[String], ParsedValue[String])] =
    textEntry(wrapTextWithPosition(key))(indentLevel) | failure(s"Expecting text entry with key '$key'")

  def textEntry(keyParser: Parser[ParsedValue[String]])(indentLevel: Int): Parser[(ParsedValue[String], ParsedValue[String])] =
    (((keyParser <~ keyValueSeparatorPattern) ~ textValueWithSeparator(indentLevel)) ^^ entryParseResultHandler) | failure("Expecting text entry")

  def nestedTextEntry(key: String): Parser[(ParsedValue[String], ParsedValue[String])] =
    nestedTextEntry(wrapTextWithPosition(key)) | failure(s"Expecting nested text entry with key '$key'")

  def nestedTextEntry(keyParser: Parser[ParsedValue[String]]): Parser[(ParsedValue[String], ParsedValue[String])] =
    ((keyParser ~ (keyValueSeparatorPattern ~> nestedTextValue)) ^^ entryParseResultHandler) | failure("Expecting nested text entry")

  def booleanEntry(key: String): Parser[(ParsedValue[String], ParsedValue[Boolean])] =
    ((wrapTextWithPosition(key) ~ (keyValueSeparatorPattern ~> booleanValue) <~ lineEndingPattern) ^^ entryParseResultHandler) | failure(s"Expecting boolean entry with key $key")

  def intEntry(key: String): Parser[(ParsedValue[String], ParsedValue[Int])] =
    ((wrapTextWithPosition(key) ~ (keyValueSeparatorPattern ~> boundedIntValue) <~ lineEndingPattern) ^^ entryParseResultHandler) | failure(s"Expecting int entry with key $key")

  def mapEntry[T](keyParser: Parser[ParsedValue[String]])(mapEntryParser: (Int => Parser[(ParsedValue[String], T)]))(indentLevel: Int): Parser[(ParsedValue[String], Map[ParsedValue[String], T])] =
    ((keyParser ~ (keyComplexSeparatorPattern ~> map(mapEntryParser)(indentLevel + 1))) ^^ entryParseResultHandler) | failure("Expecting map entry")

  def mapEntry[T](key: String)(mapEntryParser: (Int => Parser[(ParsedValue[String], T)]))(indentLevel: Int): Parser[(ParsedValue[String], Map[ParsedValue[String], T])] =
    mapEntry(wrapTextWithPosition(key))(mapEntryParser)(indentLevel) | failure(s"Expecting map entry with key '$key'")

  def nestedMapEntry[T](keyParser: Parser[ParsedValue[String]])(mapEntryParser: Parser[(ParsedValue[String], T)]): Parser[(ParsedValue[String], Map[ParsedValue[String], T])] =
    (keyParser ~ (keyValueSeparatorPattern ~> nestedMap(mapEntryParser))) ^^ entryParseResultHandler

  def nestedMapEntryWithLineFeed[T](key: String)(mapEntryParser: Parser[(ParsedValue[String], T)]): Parser[(ParsedValue[String], Map[ParsedValue[String], T])] =
    nestedMapEntryWithLineFeed(wrapTextWithPosition(key))(mapEntryParser) | failure(s"Expecting nested map entry with key '$key'")

  def nestedMapEntryWithLineFeed[T](keyParser: Parser[ParsedValue[String]])(mapEntryParser: Parser[(ParsedValue[String], T)]): Parser[(ParsedValue[String], Map[ParsedValue[String], T])] =
    (nestedMapEntry(keyParser)(mapEntryParser) <~ lineEndingPattern) | failure("Expecting map entry")

  def complexEntry[T](keyParser: Parser[ParsedValue[String]])(complexParser: (Int => Parser[T]))(indentLevel: Int): Parser[(ParsedValue[String], T)] = {
    ((keyParser ~ (keyComplexSeparatorPattern ~> complexParser(indentLevel + 1))) ^^ entryParseResultHandler) | failure("Expecting complex entry")
  }

  def complexEntry[T](key: String)(complexParser: (Int => Parser[T]))(indentLevel: Int): Parser[(ParsedValue[String], T)] =
    complexEntry(wrapTextWithPosition(key))(complexParser)(indentLevel) | failure(s"Expecting complex entry with key '$key'")

  def nestedComplexEntry[T](keyParser: Parser[ParsedValue[String]])(complexParser: Parser[T]): Parser[(ParsedValue[String], T)] =
    (keyParser ~ (keyValueSeparatorPattern ~> nestedMapStartPattern ~> complexParser <~ nestedMapEndPattern)) ^^ entryParseResultHandler

  def nestedComplexEntryWithLineFeed[T](key: String)(complexParser: Parser[T]): Parser[(ParsedValue[String], T)] =
    nestedComplexEntryWithLineFeed(wrapTextWithPosition(key))(complexParser) | failure(s"Expecting nested complex entry with key '$key'")

  def nestedComplexEntryWithLineFeed[T](keyParser: Parser[ParsedValue[String]])(complexParser: Parser[T]): Parser[(ParsedValue[String], T)] =
    (nestedComplexEntry(keyParser)(complexParser) <~ lineEndingPattern) | failure("Expecting nested complex entry")

  def listEntry[T](keyParser: Parser[ParsedValue[String]])(listEntryParser: (Int => Parser[T]))(indentLevel: Int): Parser[(ParsedValue[String], List[T])] =
    ((keyParser ~ (keyComplexSeparatorPattern ~> list(listEntryParser)(indentLevel + 1))) ^^ entryParseResultHandler) | failure("Expecting list entry")

  def listEntry[T](key: String)(listEntryParser: (Int => Parser[T]))(indentLevel: Int): Parser[(ParsedValue[String], List[T])] =
    listEntry(wrapTextWithPosition(key))(listEntryParser)(indentLevel) | failure(s"Expecting list entry with key '$key'")

  def nestedListEntry[T](keyParser: Parser[ParsedValue[String]])(listEntryParser: Parser[T]): Parser[(ParsedValue[String], List[T])] =
    (keyParser ~ (keyValueSeparatorPattern ~> nestedList(listEntryParser))) ^^ entryParseResultHandler

  def nestedListEntryWithLineFeed[T](key: String)(listEntryParser: Parser[T]): Parser[(ParsedValue[String], List[T])] =
    nestedListEntryWithLineFeed(wrapTextWithPosition(key))(listEntryParser) | failure(s"Expecting nested list entry with key '$key'")

  def nestedListEntryWithLineFeed[T](keyParser: Parser[ParsedValue[String]])(listEntryParser: Parser[T]): Parser[(ParsedValue[String], List[T])] =
    (nestedListEntry(keyParser)(listEntryParser) <~ lineEndingPattern) | failure("Expecting nested list entry")

  def keyValue = positioned(keyPattern ^^ (k => ParsedValue(k))) | failure("Expecting key")

  def intValue = positioned(decimalNumber ^^ (i => ParsedValue(i.toInt))) | failure("Expecting int value")

  def boundedIntValue = positioned((intValue | "unbounded" | "UNBOUNDED") ^^ {
    case "unbounded" | "UNBOUNDED" => ParsedValue[Int](Int.MaxValue)
    case i: ParsedValue[Int] => i
  }) | failure("Expecting int value or unbounded")

  def floatValue = positioned(floatingPointNumber ^^ (f => ParsedValue(f.toDouble))) | failure("Expecting float value")

  def booleanValue = positioned((trueValueToken ^^ (_ => ParsedValue(true))) | (falseValueToken ^^ (_ => ParsedValue(false)))) | failure("Expecting boolean value")

  private def quotedTextValue: Parser[ParsedValue[String]] =
    nullValuePattern ^^ (_ => ParsedValue[String](null)) |
      quotedTextValuePattern ^^ (scalarText => ParsedValue(scalarText.substring(1, scalarText.length - 1)))

  def textValue: Parser[ParsedValue[String]] =
    positioned(quotedTextValue | nonQuotedTextValuePattern ^^ (scalarText => ParsedValue(scalarText.trim))) | failure("Expecting text value")

  def nestedTextValue: Parser[ParsedValue[String]] =
    positioned(quotedTextValue | nestedNonQuotedTextValuePattern ^^ (scalarText => ParsedValue(scalarText.trim))) | failure("Expecting nested text value")

  private def longTextLine = wrapParserWithPosition( """[^\n]+""".r)

  def longTextValue(indentLevel: Int, withNewLine: Boolean): Parser[ParsedValue[String]] =
    positioned((indentAtLeast(indentLevel) into (newIndentLength => longTextLine ~ opt(lineEndingPattern ~> rep1sep(indent(newIndentLength) ~> longTextLine, lineEndingPattern)))) ^^ {
      case (firstLine ~ None) => firstLine
      case (firstLine ~ Some(lineList)) => lineList.fold(firstLine) { (existingText, nextLine) =>
        val sep = if (withNewLine) "\n" else " "
        val concat = ParsedValue(existingText.value + sep + nextLine.value)
        concat.setPos(firstLine.pos)
        concat
      }
    }) | failure("Expecting long text " + (if (withNewLine) " multi-lines "))

  def entryParseResultHandler[T](input: (ParsedValue[String] ~ T)) = {
    input match {
      case (key ~ value) => (key, value)
    }
  }

  def wrapValueWithPosition[T](input: T) = ParsedValue(input)

  def wrapParserWithPosition[T](parser: Parser[T]) = positioned(parser ^^ wrapValueWithPosition)

  def wrapTextWithPosition[T](text: String) = positioned(text ^^ wrapValueWithPosition) | failure(s"Expecting token '$text'")

  def wrapMapEntryParserWithPosition(parser: Parser[(ParsedValue[String], Map[ParsedValue[String], FieldValue])]) = {
    parser ^^ {
      case (key: ParsedValue[String], value: Map[ParsedValue[String], FieldValue]) => (key, ComplexValue(value))
    }
  }

  def wrapListEntryParserWithPosition(parser: Parser[(ParsedValue[String], List[FieldValue])]) = {
    parser ^^ {
      case (key: ParsedValue[String], value: List[FieldValue]) => (key, ListValue(value))
    }
  }

  def wrapMapValue(parser: Parser[Map[ParsedValue[String], FieldValue]]) = parser ^^ { map => ComplexValue(map) }

  def wrapListValue(parser: Parser[List[FieldValue]]) = parser ^^ { list => ListValue(list) }

  def wrapScalarValue(parser: Parser[ParsedValue[String]]) = parser ^^ { value => ScalarValue(value) }

  def yamlNode(indentLevel: Int): PackratParser[FieldValue] = {
    (wrapMapValue(mapWithoutFirstIndentation(yamlEntry)(indentLevel)) |
      wrapListValue(listWithoutFirstIndentation(yamlNode)(indentLevel)) |
      wrapScalarValue(textValueWithSeparator(indentLevel)) |
      nestedYamlNodeWithLineFeed) | failure(s"Expecting yaml node")
  }

  def nestedYamlNode: PackratParser[FieldValue] = {
    (wrapMapValue(nestedMap(nestedYamlEntry)) |
      wrapListValue(nestedList(nestedYamlNode)) |
      wrapScalarValue(nestedTextValue)) | failure(s"Expecting nested yaml node")
  }

  def nestedYamlNodeWithLineFeed: PackratParser[FieldValue] = {
    (nestedYamlNode <~ lineEndingPattern) | failure(s"Expecting nested yaml node with line feed")
  }

  def yamlEntry(indentLevel: Int): PackratParser[(ParsedValue[String], FieldValue)] = {
    (yamlMapEntry(indentLevel) |
      yamlListEntry(indentLevel) |
      yamlTextEntry(indentLevel) |
      nestedYamlEntryWithLineFeed) | failure(s"Expecting a (key,value) yaml entry")
  }

  def nestedYamlEntry: PackratParser[(ParsedValue[String], FieldValue)] = {
    (nestedYamlMapEntry | nestedYamlListEntry | nestedYamlTextEntry(keyValue)) | failure(s"Expecting a nested (key,value) yaml entry")
  }

  def nestedYamlEntryWithLineFeed: PackratParser[(ParsedValue[String], FieldValue)] = {
    (nestedYamlEntry <~ lineEndingPattern) | failure(s"Expecting a nested (key,value) yaml entry with line feed")
  }

  def yamlMapEntry(keyParser: Parser[ParsedValue[String]])(indentLevel: Int): PackratParser[(ParsedValue[String], ComplexValue)] = {
    wrapMapEntryParserWithPosition(mapEntry(keyParser)(yamlEntry)(indentLevel)) | failure(s"Expecting a (key,value) yaml entry, value of type complex")
  }

  def yamlMapEntry(indentLevel: Int): PackratParser[(ParsedValue[String], ComplexValue)] = {
    yamlMapEntry(keyValue)(indentLevel) | failure(s"Expecting a (key,value) yaml entry, value of type complex")
  }

  def nestedYamlMapEntry: PackratParser[(ParsedValue[String], ComplexValue)] = {
    nestedYamlMapEntry(keyValue) | failure(s"Expecting a nested (key,value) yaml entry, value of type complex")
  }

  def nestedYamlMapEntry(keyParser: Parser[ParsedValue[String]]): PackratParser[(ParsedValue[String], ComplexValue)] = {
    wrapMapEntryParserWithPosition(nestedMapEntry(keyParser)(nestedYamlEntry)) | failure(s"Expecting a nested (key,value) yaml entry, value of type complex")
  }

  def nestedYamlMapEntryWithLineFeed: PackratParser[(ParsedValue[String], ComplexValue)] = {
    (nestedYamlMapEntryWithLineFeed(keyValue) <~ lineEndingPattern) | failure(s"Expecting a nested (key,value) yaml entry, value of type complex, with line feed")
  }

  def nestedYamlMapEntryWithLineFeed(keyParser: Parser[ParsedValue[String]]): PackratParser[(ParsedValue[String], ComplexValue)] = {
    (nestedYamlMapEntry(keyParser) <~ lineEndingPattern) | failure(s"Expecting a nested (key,value) yaml entry, value of type complex, with line feed")
  }

  def yamlListEntry(indentLevel: Int): PackratParser[(ParsedValue[String], ListValue)] = {
    yamlListEntry(keyValue)(indentLevel) | failure(s"Expecting a (key,value) yaml entry, value of type list")
  }

  def yamlListEntry(keyParser: Parser[ParsedValue[String]])(indentLevel: Int): PackratParser[(ParsedValue[String], ListValue)] = {
    wrapListEntryParserWithPosition(listEntry(keyParser)(yamlNode)(indentLevel)) | failure(s"Expecting a (key,value) yaml entry, value of type list")
  }

  def nestedYamlListEntry: PackratParser[(ParsedValue[String], ListValue)] = {
    nestedYamlListEntry(keyValue) | failure(s"Expecting a nested (key,value) yaml entry, value of type list")
  }

  def nestedYamlListEntry(keyParser: Parser[ParsedValue[String]]): PackratParser[(ParsedValue[String], ListValue)] = {
    wrapListEntryParserWithPosition(nestedListEntry(keyParser)(nestedYamlNode)) | failure(s"Expecting a nested (key,value) yaml entry, value of type list")
  }

  def nestedYamlListEntryWithLineFeed: PackratParser[(ParsedValue[String], ListValue)] = {
    nestedYamlListEntryWithLineFeed(keyValue) | failure(s"Expecting a nested (key,value) yaml entry, value of type list, with line feed")
  }

  def nestedYamlListEntryWithLineFeed(keyParser: Parser[ParsedValue[String]]): PackratParser[(ParsedValue[String], ListValue)] = {
    (nestedYamlListEntry(keyParser) <~ lineEndingPattern) | failure(s"Expecting a nested (key,value) yaml entry, value of type list, with line feed")
  }

  def yamlTextEntry(indentLevel: Int): PackratParser[(ParsedValue[String], ScalarValue)] = {
    yamlTextEntry(keyValue)(indentLevel) | failure(s"Expecting a yaml text entry")
  }

  def yamlTextEntry(keyParser: Parser[ParsedValue[String]])(indentLevel: Int): PackratParser[(ParsedValue[String], ScalarValue)] = {
    textEntry(keyParser)(indentLevel) ^^ {
      case (key, value: ParsedValue[String]) => (key, ScalarValue(value))
    } | failure(s"Expecting a yaml text entry")
  }

  def nestedYamlTextEntry: PackratParser[(ParsedValue[String], ScalarValue)] = {
    nestedYamlTextEntry(keyValue) | failure(s"Expecting a yaml text entry")
  }

  def nestedYamlTextEntry(keyParser: Parser[ParsedValue[String]]): PackratParser[(ParsedValue[String], ScalarValue)] = {
    nestedTextEntry(keyParser) ^^ {
      case (key, value: ParsedValue[String]) => (key, ScalarValue(value))
    } | failure(s"Expecting a nested yaml text entry")
  }
}