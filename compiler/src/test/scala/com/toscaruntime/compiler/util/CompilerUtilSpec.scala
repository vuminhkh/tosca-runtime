package com.toscaruntime.compiler.util

import com.toscaruntime.compiler.tosca.{ComplexValue, ListValue, ParsedValue, ScalarValue}
import com.typesafe.scalalogging.LazyLogging
import org.scalatestplus.play.PlaySpec

class CompilerUtilSpec extends PlaySpec with LazyLogging {

  "Compiler util" must {
    "be able to serialize a map of ParsedValue string to json" in {
      val map = ComplexValue(Map(
        ParsedValue("a") -> ComplexValue(Map(ParsedValue("b") -> ScalarValue("c"))),
        ParsedValue("d") -> ListValue(List(ScalarValue("e"), ScalarValue("f")))
      ))
      val json = CompilerUtil.serializePropertyValueToJson(map)
      logger.info(s"Generated $json")
      json must be("""{"a" : {"b" : "c"}, "d" : ["e", "f"]}""")
    }
  }
}
