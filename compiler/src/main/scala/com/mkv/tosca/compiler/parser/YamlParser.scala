package com.mkv.tosca.compiler.parser

import com.mkv.tosca.compiler.model.ParsedValue

import scala.util.parsing.combinator.JavaTokenParsers

trait YamlParser extends JavaTokenParsers {

  override def skipWhitespace = false

  val keyPattern = """\p{Alnum}+[^:\[\]\{\}>]*""".r

  val nonQuotedTextValuePattern = """[^:\[\]\{\}>-].*""".r

  val nestedNonQuotedTextValuePattern = """[^,:\[\]\{\}>-]*""".r

  val quotedTextValuePattern = """".*"""".r

  val nullValuePattern = """(?:null|~)""".r

  val trueValueToken = """true"""

  val falseValueToken = """false"""

  val commentRegex = """\p{Blank}*(?:#.*)?"""

  val blankLineRegex = commentRegex + """\r?\n"""

  val newLineRegex = commentRegex + """\r?\n(?:""" + blankLineRegex + """)*"""

  val newLinePattern = newLineRegex.r

  val keyValueSeparatorPattern = """: +""".r

  val keyLongTextSeparatorPattern = (""":[ \t]*>""" + newLineRegex).r

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

  def textEntry(indentLevel: Int)(key: String): Parser[(ParsedValue[String], ParsedValue[String])] =
    (wrapTextWithPosition(key) ~ ((keyLongTextSeparatorPattern ~> longTextValue(indentLevel + 1)) | (keyValueSeparatorPattern ~> textValue)) <~ newLinePattern) ^^ entryParseResultHandler

  def genericTextEntry(indentLevel: Int): Parser[(ParsedValue[String], ParsedValue[String])] =
    (keyValue ~ ((keyLongTextSeparatorPattern ~> longTextValue(indentLevel + 1)) | (keyValueSeparatorPattern ~> textValue)) <~ newLinePattern) ^^ entryParseResultHandler

  def booleanEntry(indentLevel: Int)(key: String): Parser[(ParsedValue[String], ParsedValue[Boolean])] =
    (wrapTextWithPosition(key) ~ (keyValueSeparatorPattern ~> booleanValue) <~ newLinePattern) ^^ entryParseResultHandler

  def intEntry(indentLevel: Int)(key: String): Parser[(ParsedValue[String], ParsedValue[Int])] =
    (wrapTextWithPosition(key) ~ (keyValueSeparatorPattern ~> boundedIntValue) <~ newLinePattern) ^^ entryParseResultHandler

  def mapEntry[T](indentLevel: Int)(key: String)(mapEntryParser: (Int => Parser[(ParsedValue[String], T)])): Parser[(ParsedValue[String], Map[ParsedValue[String], T])] =
    (wrapTextWithPosition(key) ~ (keyComplexSeparatorPattern ~> map(mapEntryParser)(indentLevel + 1))) ^^ entryParseResultHandler

  def genericMapEntry[T](indentLevel: Int)(mapEntryParser: (Int => Parser[(ParsedValue[String], T)])): Parser[(ParsedValue[String], Map[ParsedValue[String], T])] =
    (keyValue ~ (keyComplexSeparatorPattern ~> map(mapEntryParser)(indentLevel + 1))) ^^ entryParseResultHandler

  def complexEntry[T](indentLevel: Int)(key: String)(complexParser: (Int => Parser[T])): Parser[(ParsedValue[String], T)] = {
    (wrapTextWithPosition(key) ~ (keyComplexSeparatorPattern ~> complexParser(indentLevel + 1))) ^^ entryParseResultHandler
  }

  def genericComplexEntry[T](indentLevel: Int)(complexParser: (Int => Parser[T])): Parser[(ParsedValue[String], T)] = {
    (keyValue ~ (keyComplexSeparatorPattern ~> complexParser(indentLevel + 1))) ^^ entryParseResultHandler
  }

  def listEntry[T](indentLevel: Int)(key: String)(listEntryParser: (Int => Parser[T])): Parser[(ParsedValue[String], List[T])] =
    (keyValue ~ (keyComplexSeparatorPattern ~> list(listEntryParser)(indentLevel + 1))) ^^ entryParseResultHandler

  def keyValue = positioned(keyPattern ^^ (k => ParsedValue(k)))

  def nullValue = positioned(nullValuePattern ^^ (_ => ParsedValue(null)))

  def intValue = positioned(decimalNumber ^^ (i => ParsedValue(i.toInt)))

  def boundedIntValue = positioned((intValue | "unbounded") ^^ {
    case "unbounded" => ParsedValue[Int](Int.MaxValue)
    case i: ParsedValue[Int] => i
  })

  def floatValue = positioned(floatingPointNumber ^^ (f => ParsedValue(f.toDouble)))

  def booleanValue = positioned((trueValueToken ^^ (_ => ParsedValue(true))) | (falseValueToken ^^ (_ => ParsedValue(false))))

  def textValue: Parser[ParsedValue[String]] =
    positioned(nullValuePattern ^^ (_ => ParsedValue[String](null))
      | quotedTextValuePattern ^^ (scalarText => ParsedValue(scalarText.substring(1, scalarText.length - 1)))
      | nonQuotedTextValuePattern ^^ (scalarText => ParsedValue(scalarText.trim)))

  def nestedTextValue: Parser[ParsedValue[String]] =
    positioned(nullValuePattern ^^ (_ => ParsedValue[String](null))
      | quotedTextValuePattern ^^ (scalarText => ParsedValue(scalarText.substring(1, scalarText.length - 1)))
      | nestedNonQuotedTextValuePattern ^^ (scalarText => ParsedValue(scalarText.trim)))

  def longTextValue(indentLevel: Int): Parser[ParsedValue[String]] =
    positioned((indentAtLeast(indentLevel) into (newIndentLength => textValue ~ opt(newLinePattern ~> rep1sep(indent(newIndentLength) ~> textValue, newLinePattern)))) ^^ {
      case (firstLine ~ None) => firstLine
      case (firstLine ~ Some(lineList)) => lineList.fold(firstLine)((existingText, nextLine) => ParsedValue(existingText + nextLine.value))
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