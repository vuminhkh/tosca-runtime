package com.toscaruntime.compiler.parser

import com.toscaruntime.compiler.tosca.ParsedValue

import scala.util.parsing.combinator.JavaTokenParsers

trait YamlParser extends JavaTokenParsers {

  override def skipWhitespace = false

  val keyPattern = """\p{Alnum}+[^:\[\]\{\}>\p{Blank},]*""".r

  val nestedListStartPattern = """\[ *""".r

  val nestedListEndPattern = """ *\]""".r

  val nestedMapStartPattern = """\{ *""".r

  val nestedMapEndPattern = """ *\}""".r

  val nestedEntrySeparator = """\s*,\s*""".r

  val nonQuotedTextValuePattern = """[^:\[\]\{\}>-].*""".r

  val nestedNonQuotedTextValuePattern = """[^,:\[\]\{\}>-]*""".r

  val quotedTextValuePattern = """"[^"]*"""".r

  val nullValuePattern = """(?:null|~)""".r

  val trueValueToken = """true"""

  val falseValueToken = """false"""

  val commentRegex = """\p{Blank}*(?:#.*)?"""

  val blankLineRegex = commentRegex + """\r?\n"""

  val newLineRegex = commentRegex + """\r?\n(?:""" + blankLineRegex + """)*"""

  val lineEndingPattern = ("""(?:""" + newLineRegex + """|""" + """\Z)""").r

  val keyValueSeparatorPattern = """: +""".r

  val keyLongTextSeparatorPattern = (""":[ \t]*>""" + newLineRegex).r

  val keyLongTextWithNewLineSeparatorPattern = (""":[ \t]*\|""" + newLineRegex).r

  val keyComplexSeparatorPattern = (""":[ \t]*""" + newLineRegex).r

  def listIndicator: Parser[Int] = """- +""".r ^^ (_.length)

  def listIndicator(listIndicatorLength: Int): Parser[Int] = ("- {" + (listIndicatorLength - 1) + "}").r ^^ (_.length)

  def indentAtLeast(numberOfWhitespaces: Int): Parser[Int] = ("^ {" + numberOfWhitespaces + ",}").r ^^ (_.length)

  def indent(numberOfWhitespaces: Int): Parser[Int] = ("^ {" + numberOfWhitespaces + "}").r ^^ (_.length)

  def internalMap[T](mapEntryParser: (Int => Parser[(ParsedValue[String], T)]))(indentLength: Int): Parser[Map[ParsedValue[String], T]] =
    (mapEntryParser(indentLength) ~ opt(rep1(indent(indentLength) ~> mapEntryParser(indentLength)))) ^^ {
      case (firstEntry ~ None) => Map(firstEntry)
      case (firstEntry ~ Some(entryList)) => ((List() :+ firstEntry) ++ entryList).toMap
    }

  def map[T](mapEntryParser: (Int => Parser[(ParsedValue[String], T)]))(minIndent: Int): Parser[Map[ParsedValue[String], T]] =
    indentAtLeast(minIndent) into (newIndentLength => internalMap(mapEntryParser)(newIndentLength))

  def nestedMap[T](mapEntryParser: Parser[(ParsedValue[String], T)]): Parser[Map[ParsedValue[String], T]] =
    (nestedMapStartPattern ~> repsep(mapEntryParser, nestedEntrySeparator) <~ nestedMapEndPattern) ^^ (_.toMap)

  def internalList[T](listEntryParser: (Int => Parser[T]))(indentLength: Int, listIndicatorLength: Int = 2): Parser[List[T]] = {
    val newListEntryLevel = indentLength + listIndicatorLength
    listEntryParser(newListEntryLevel) ~ opt(rep1(indent(indentLength) ~> listIndicator(listIndicatorLength) ~> listEntryParser(newListEntryLevel)))
  } ^^ {
    case (firstEntry ~ None) => List(firstEntry)
    case (firstEntry ~ Some(entryList)) => (List() :+ firstEntry) ++ entryList
  }

  def list[T](listEntryParser: (Int => Parser[T]))(indentLevel: Int): Parser[List[T]] =
    indentAtLeast(indentLevel) into { newIndentLength =>
      listIndicator into (listIndicatorLength => internalList(listEntryParser)(newIndentLength, listIndicatorLength))
    }

  def nestedList[T](listEntryParser: Parser[T]): Parser[List[T]] =
    (nestedListStartPattern ~> repsep(listEntryParser, nestedEntrySeparator) <~ nestedListEndPattern) ^^ (_.toList)

  private def textValueWithSeparator(indentLevel: Int) =
    ((keyLongTextSeparatorPattern ~> longTextValue(indentLevel + 1, withNewLine = false)) |
      (keyLongTextWithNewLineSeparatorPattern ~> longTextValue(indentLevel + 1, withNewLine = true)) |
      (keyValueSeparatorPattern ~> textValue)) <~ lineEndingPattern

  def textEntry(key: String)(indentLevel: Int): Parser[(ParsedValue[String], ParsedValue[String])] =
    textEntry(wrapTextWithPosition(key))(indentLevel)

  def textEntry(keyParser: Parser[ParsedValue[String]])(indentLevel: Int): Parser[(ParsedValue[String], ParsedValue[String])] =
    (keyParser ~ textValueWithSeparator(indentLevel)) ^^ entryParseResultHandler

  def nestedTextEntry(key: String): Parser[(ParsedValue[String], ParsedValue[String])] =
    nestedTextEntry(wrapTextWithPosition(key))

  def nestedTextEntry(keyParser: Parser[ParsedValue[String]]): Parser[(ParsedValue[String], ParsedValue[String])] =
    (keyParser ~ (keyValueSeparatorPattern ~> nestedTextValue)) ^^ entryParseResultHandler

  def booleanEntry(key: String): Parser[(ParsedValue[String], ParsedValue[Boolean])] =
    (wrapTextWithPosition(key) ~ (keyValueSeparatorPattern ~> booleanValue) <~ lineEndingPattern) ^^ entryParseResultHandler

  def intEntry(key: String): Parser[(ParsedValue[String], ParsedValue[Int])] =
    (wrapTextWithPosition(key) ~ (keyValueSeparatorPattern ~> boundedIntValue) <~ lineEndingPattern) ^^ entryParseResultHandler

  def mapEntry[T](keyParser: Parser[ParsedValue[String]])(mapEntryParser: (Int => Parser[(ParsedValue[String], T)]))(indentLevel: Int): Parser[(ParsedValue[String], Map[ParsedValue[String], T])] =
    (keyParser ~ (keyComplexSeparatorPattern ~> map(mapEntryParser)(indentLevel + 1))) ^^ entryParseResultHandler

  def mapEntry[T](key: String)(mapEntryParser: (Int => Parser[(ParsedValue[String], T)]))(indentLevel: Int): Parser[(ParsedValue[String], Map[ParsedValue[String], T])] =
    mapEntry(wrapTextWithPosition(key))(mapEntryParser)(indentLevel)

  def internalNestedMapEntry[T](keyParser: Parser[ParsedValue[String]])(mapEntryParser: Parser[(ParsedValue[String], T)]): Parser[(ParsedValue[String], Map[ParsedValue[String], T])] =
    (keyParser ~ (keyValueSeparatorPattern ~> nestedMap(mapEntryParser))) ^^ entryParseResultHandler

  def nestedMapEntry[T](key: String)(mapEntryParser: Parser[(ParsedValue[String], T)]): Parser[(ParsedValue[String], Map[ParsedValue[String], T])] =
    nestedMapEntry(wrapTextWithPosition(key))(mapEntryParser)

  def nestedMapEntry[T](keyParser: Parser[ParsedValue[String]])(mapEntryParser: Parser[(ParsedValue[String], T)]): Parser[(ParsedValue[String], Map[ParsedValue[String], T])] =
    internalNestedMapEntry(keyParser)(mapEntryParser) <~ lineEndingPattern

  def complexEntry[T](keyParser: Parser[ParsedValue[String]])(complexParser: (Int => Parser[T]))(indentLevel: Int): Parser[(ParsedValue[String], T)] = {
    (keyParser ~ (keyComplexSeparatorPattern ~> complexParser(indentLevel + 1))) ^^ entryParseResultHandler
  }

  def complexEntry[T](key: String)(complexParser: (Int => Parser[T]))(indentLevel: Int): Parser[(ParsedValue[String], T)] =
    complexEntry(wrapTextWithPosition(key))(complexParser)(indentLevel)

  def internalNestedComplexEntry[T](keyParser: Parser[ParsedValue[String]])(complexParser: Parser[T]): Parser[(ParsedValue[String], T)] =
    (keyParser ~ (keyValueSeparatorPattern ~> nestedMapStartPattern ~> complexParser <~ nestedMapEndPattern)) ^^ entryParseResultHandler

  def nestedComplexEntry[T](key: String)(complexParser: Parser[T]): Parser[(ParsedValue[String], T)] =
    nestedComplexEntry(wrapTextWithPosition(key))(complexParser)

  def nestedComplexEntry[T](keyParser: Parser[ParsedValue[String]])(complexParser: Parser[T]): Parser[(ParsedValue[String], T)] =
    internalNestedComplexEntry(keyParser)(complexParser) <~ lineEndingPattern

  def listEntry[T](keyParser: Parser[ParsedValue[String]])(listEntryParser: (Int => Parser[T]))(indentLevel: Int): Parser[(ParsedValue[String], List[T])] =
    (keyParser ~ (keyComplexSeparatorPattern ~> list(listEntryParser)(indentLevel + 1))) ^^ entryParseResultHandler

  def listEntry[T](key: String)(listEntryParser: (Int => Parser[T]))(indentLevel: Int): Parser[(ParsedValue[String], List[T])] =
    listEntry(wrapTextWithPosition(key))(listEntryParser)(indentLevel)

  def internalNestedListEntry[T](keyParser: Parser[ParsedValue[String]])(listEntryParser: Parser[T]): Parser[(ParsedValue[String], List[T])] =
    (keyParser ~ (keyValueSeparatorPattern ~> nestedList(listEntryParser))) ^^ entryParseResultHandler

  def nestedListEntry[T](key: String)(listEntryParser: Parser[T]): Parser[(ParsedValue[String], List[T])] =
    nestedListEntry(wrapTextWithPosition(key))(listEntryParser)

  def nestedListEntry[T](keyParser: Parser[ParsedValue[String]])(listEntryParser: Parser[T]): Parser[(ParsedValue[String], List[T])] =
    internalNestedListEntry(keyParser)(listEntryParser) <~ lineEndingPattern

  def keyValue = positioned(keyPattern ^^ (k => ParsedValue(k)))

  def intValue = positioned(decimalNumber ^^ (i => ParsedValue(i.toInt)))

  def boundedIntValue = positioned((intValue | "unbounded") ^^ {
    case "unbounded" => ParsedValue[Int](Int.MaxValue)
    case i: ParsedValue[Int] => i
  })

  def floatValue = positioned(floatingPointNumber ^^ (f => ParsedValue(f.toDouble)))

  def booleanValue = positioned((trueValueToken ^^ (_ => ParsedValue(true))) | (falseValueToken ^^ (_ => ParsedValue(false))))

  private def quotedTextValue: Parser[ParsedValue[String]] =
    nullValuePattern ^^ (_ => ParsedValue[String](null)) |
      quotedTextValuePattern ^^ (scalarText => ParsedValue(scalarText.substring(1, scalarText.length - 1)))

  def textValue: Parser[ParsedValue[String]] =
    positioned(quotedTextValue | nonQuotedTextValuePattern ^^ (scalarText => ParsedValue(scalarText.trim)))

  def nestedTextValue: Parser[ParsedValue[String]] =
    positioned(quotedTextValue | nestedNonQuotedTextValuePattern ^^ (scalarText => ParsedValue(scalarText.trim)))

  def longTextValue(indentLevel: Int, withNewLine: Boolean): Parser[ParsedValue[String]] =
    positioned((indentAtLeast(indentLevel) into (newIndentLength => textValue ~ opt(lineEndingPattern ~> rep1sep(indent(newIndentLength) ~> textValue, lineEndingPattern)))) ^^ {
      case (firstLine ~ None) => firstLine
      case (firstLine ~ Some(lineList)) => lineList.fold(firstLine)((existingText, nextLine) => ParsedValue(existingText + (if (withNewLine) "\n" else "") + nextLine.value))
    })

  def entryParseResultHandler[T](input: (ParsedValue[String] ~ T)) = {
    input match {
      case (key ~ value) => (key, value)
    }
  }

  def wrapValueWithPosition[T](input: T) = ParsedValue(input)

  def wrapParserWithPosition[T](parser: Parser[T]) = positioned(parser ^^ wrapValueWithPosition)

  def wrapTextWithPosition[T](text: String) = positioned(text ^^ wrapValueWithPosition)
}