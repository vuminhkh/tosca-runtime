package com.toscaruntime.tosca

import java.text.SimpleDateFormat
import java.util.Date

import com.toscaruntime.exception.UnexpectedException

import scala.annotation.tailrec
import scala.util.Try

trait ToscaPrimitiveType {
  val valueAsText: String
  val value: Option[_]

  def isValid = value.isDefined
}

trait ToscaComparableType[T] extends ToscaPrimitiveType with Ordered[ToscaComparableType[T]] {

  val value: Option[T]

  def compare(that: ToscaComparableType[T]): Int = {
    if (value.isEmpty) {
      // should check that the value is valid before performing compare
      throw new UnexpectedException(s"Value $valueAsText is not valid for ${this.getClass.getSimpleName}")
    }
    if (that.value.isEmpty) {
      // should check that the value is valid before performing compare
      throw new UnexpectedException(s"Value ${that.valueAsText} is not valid for ${that.getClass.getSimpleName}")
    }
    compareParsedValue(value.get, that.value.get)
  }

  def compareParsedValue(left: T, right: T): Int
}

case class ToscaInteger(valueAsText: String) extends ToscaComparableType[Long] {

  override val value = Try(valueAsText.toLong).toOption

  override def compareParsedValue(left: Long, right: Long): Int = left.compare(right)
}

case class ToscaFloat(valueAsText: String) extends ToscaComparableType[BigDecimal] {

  override val value = Try(BigDecimal(valueAsText)).toOption

  override def compareParsedValue(left: BigDecimal, right: BigDecimal): Int = left.compare(right)
}

case class ToscaString(valueAsText: String) extends ToscaPrimitiveType {

  override val value = Some(valueAsText)
}

case class ToscaBoolean(valueAsText: String) extends ToscaPrimitiveType {

  val value = Try(valueAsText.toBoolean).toOption
}

case class ToscaVersion(valueAsText: String) extends ToscaComparableType[String] {

  val versionPattern = """\d+(?:\.\d+)*(?:[\.-]\w+)*"""

  override val value = if (valueAsText.matches(versionPattern)) Some(valueAsText) else None

  private def splitString(string: String, index: Int) = (string.substring(0, index), string.substring(index + 1, string.length))

  private def splitPart(version: String) = {
    val pointIndex = version.indexOf('.')
    if (pointIndex > 0) {
      splitString(version, pointIndex)
    } else {
      val dashIndex = version.indexOf('-')
      if (dashIndex > 0) {
        splitString(version, dashIndex)
      } else (version, "")
    }
  }

  private def comparePart(leftPart: String, rightPart: String): Int = {
    val leftPartInt = Try(leftPart.toInt)
    val rightPartInt = Try(rightPart.toInt)
    leftPartInt.flatMap(leftInt =>
      rightPartInt.map { rightInt =>
        leftInt.compareTo(rightInt)
      }).getOrElse(leftPart.compareTo(rightPart))
  }

  @tailrec
  final override def compareParsedValue(left: String, right: String): Int = {
    val leftComparablePart = splitPart(left)
    val rightComparablePart = splitPart(right)
    val currentPartResult = comparePart(leftComparablePart._1, rightComparablePart._1)
    if (currentPartResult == 0 && (leftComparablePart._2.nonEmpty || rightComparablePart._2.nonEmpty)) {
      compareParsedValue(leftComparablePart._2, rightComparablePart._2)
    } else currentPartResult
  }
}

case class ToscaTimestamp(valueAsText: String) extends ToscaComparableType[Date] {

  val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

  override val value = Try(dateFormat.parse(valueAsText)).toOption

  override def compareParsedValue(left: Date, right: Date): Int = left.compareTo(right)
}

case class ToscaUnit(unit: String, multiplier: BigDecimal)

abstract class ToscaUnitType(valueAsText: String, units: List[ToscaUnit]) extends ToscaComparableType[BigDecimal] {

  val unitsMap: Map[String, ToscaUnit] = units.map(unit => (unit.unit, unit)).toMap

  private var parsedValue = parseUnitType(valueAsText)

  override val value: Option[BigDecimal] = parsedValue.flatMap {
    case (valuePart: String, unit: ToscaUnit) => Try(BigDecimal(valuePart) * unit.multiplier).toOption
  }

  var base: Option[BigDecimal] = parsedValue.map {
    case (valuePart, _) => BigDecimal(valuePart)
  }

  var unit: Option[ToscaUnit] = parsedValue.map {
    case (_, unitPart) => unitPart
  }

  private def parseUnitType(valueAsText: String): Option[(String, ToscaUnit)] = {
    val tokens = valueAsText.split("""\s""")
    if (tokens.length == 2 && tokens(0).nonEmpty && tokens(1).nonEmpty) {
      unitsMap.get(tokens(1).toUpperCase()).map((tokens(0), _))
    } else None
  }

  def convertToUnit[T <: ToscaUnitType](newUnitAsText: String) = {
    val newUnit = unitsMap(newUnitAsText.toUpperCase())
    val newBase = value.get / newUnit.multiplier
    unit = Some(newUnit)
    base = Some(newBase)
    parsedValue = Some((newBase.toString, newUnit))
    this
  }

  override def compareParsedValue(left: BigDecimal, right: BigDecimal): Int = left.compare(right)
}

case class ToscaSize(valueAsText: String) extends ToscaUnitType(valueAsText, List(
  ToscaUnit("B", 1L),
  ToscaUnit("KB", 1000L),
  ToscaUnit("KIB", 1024L),
  ToscaUnit("MB", 1000L * 1000L),
  ToscaUnit("MIB", 1024L * 1024L),
  ToscaUnit("GB", 1000L * 1000L * 1000L),
  ToscaUnit("GIB", 1024L * 1024L * 1024L),
  ToscaUnit("TB", 1000L * 1000L * 1000L * 1000L),
  ToscaUnit("TIB", 1024L * 1024L * 1024L * 1024L)
))

case class ToscaTime(valueAsText: String) extends ToscaUnitType(valueAsText, List(
  ToscaUnit("D", 60L * 60L * 24L),
  ToscaUnit("H", 60L * 60L),
  ToscaUnit("M", 60L),
  ToscaUnit("S", 1L),
  ToscaUnit("MS", Math.pow(10, -3)),
  ToscaUnit("US", Math.pow(10, -6)),
  ToscaUnit("NS", Math.pow(10, -9))
))

case class ToscaFrequency(valueAsText: String) extends ToscaUnitType(valueAsText, List(
  ToscaUnit("HZ", 1L),
  ToscaUnit("KHZ", 1000L),
  ToscaUnit("MHZ", 1000L * 1000L),
  ToscaUnit("GHZ", 1000L * 1000L * 1000L)
))