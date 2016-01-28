package com.toscaruntime.util

import java.util

import org.scalatest.{MustMatchers, WordSpec}

class JavaScalaConversionUtilSpec extends WordSpec with MustMatchers {

  "Java conversion util" must {
    "be able to convert a java map to scala map" in {
      val javaMap = new java.util.HashMap[String, AnyRef]
      javaMap.put("1", "b")
      javaMap.put("2", util.Arrays.asList("c", "d", "e"))
      val nestedMap = new java.util.HashMap[String, AnyRef]
      nestedMap.put("nested_a", "nested_b")
      nestedMap.put("nested_d", util.Arrays.asList("c", "d", "e"))
      javaMap.put("3", nestedMap)
      val scalaMap = JavaScalaConversionUtil.toScala(javaMap).asInstanceOf[Map[String, Any]]
      scalaMap must contain key "1"
      scalaMap("1") must be("b")
      scalaMap must contain key "2"
      scalaMap("2") must be(List("c", "d", "e"))
      scalaMap must contain key "3"
      scalaMap("3").asInstanceOf[Map[String, Any]] must contain key "nested_a"
      scalaMap("3").asInstanceOf[Map[String, Any]] must contain key "nested_d"
      scalaMap("3").asInstanceOf[Map[String, Any]]("nested_d") must be(List("c", "d", "e"))
      val reconvertedJavaMap = JavaScalaConversionUtil.toJava(scalaMap)
      reconvertedJavaMap must be(javaMap)
    }
  }
}
