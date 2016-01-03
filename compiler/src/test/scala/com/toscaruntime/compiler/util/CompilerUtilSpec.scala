package com.toscaruntime.compiler.util

import com.toscaruntime.compiler.tosca.ParsedValue
import com.typesafe.scalalogging.LazyLogging
import org.scalatestplus.play.PlaySpec

class CompilerUtilSpec extends PlaySpec with LazyLogging {

  "Compiler util" must {
    "be able to serialize a map of ParsedValue string to json" in {
      val map = Map(
        ParsedValue("a") -> Map(ParsedValue("b") -> ParsedValue("c")),
        ParsedValue("d") -> List(ParsedValue("e"), ParsedValue("f"))
      )
      val json = CompilerUtil.serializeToJson(map)
      logger.info(s"Generated $json")
      json must be("""{"a" : {"b" : "c"}, "d" : ["e", "f"]}""")
    }
  }
}
