package com.toscaruntime.util

import org.scalatest.{MustMatchers, WordSpec}

class TextFilterUtilSpec extends WordSpec with MustMatchers {

  "Text filter util" must {
    "be able to replace variable in text" in {
      TextFilterUtil.filter("""My name is ${person}""", Map("person" -> "Khang")) must be("""My name is Khang""")
      TextFilterUtil.filter("""My name is ${person}""", Map("notMatch" -> "Khang")) must be("""My name is ${person}""")
      TextFilterUtil.filter("""My name is Khang""", Map("person" -> "Khang")) must be("""My name is Khang""")
    }
  }
}
