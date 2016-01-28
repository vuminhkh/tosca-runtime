package com.toscaruntime.compiler.tosca

import org.scalatest.{MustMatchers, WordSpec}

class ToscaPrimitiveTypesSpec extends WordSpec with MustMatchers {

  "Tosca primitive types" must {
    "be able to be parsed and compared" in {
      ToscaVersion("1.2").compare(ToscaVersion("1.1")) must be > 0
      ToscaVersion("1.2").compare(ToscaVersion("1.1.1")) must be > 0
      ToscaVersion("1.1").compare(ToscaVersion("1.1.1")) must be < 0
      ToscaVersion("1.2-SNAPSHOT").compare(ToscaVersion("1.1.1")) must be > 0
      ToscaVersion("1.124.2").compare(ToscaVersion("1.23.4")) must be > 0
      ToscaVersion("1.23-SNAPSHOT").compare(ToscaVersion("1.123-SNAPSHOT")) must be < 0
      ToscaVersion("1.2-alpha1").compare(ToscaVersion("1.2-alpha2")) must be < 0
      ToscaVersion("1.2").compare(ToscaVersion("1.2")) must be(0)

      ToscaSize("1 TiB").compare(ToscaSize("1 GiB")) must be > 0
      ToscaSize("0.5 TiB").compare(ToscaSize("512 GiB")) must be(0)
      ToscaSize("0.1 TiB").compare(ToscaSize("1 GiB")) must be > 0
      ToscaSize("1024 KiB").compare(ToscaSize("1 MiB")) must be(0)

      ToscaTime("1 d").compare(ToscaTime("24 h")) must be(0)
      ToscaTime("1 s").compare(ToscaTime("500 ms")) must be > 0

      ToscaFrequency("1 GHz").compare(ToscaFrequency("23 MHz")) must be > 0
      ToscaFrequency("1 GHz").compare(ToscaFrequency("2343 MHz")) must be < 0

      ToscaBoolean("false").value must not be None
      ToscaBoolean("false").value.get must be(false)
      ToscaBoolean("true").value must not be None
      ToscaBoolean("true").value.get must be(true)

      ToscaInteger("123Z").value must be(None)
      ToscaInteger("123").value must not be None
      ToscaInteger("123").value.get must be(123)

      ToscaTimestamp("2016-01-23T23:26:44.964+01:00").value must be(None)
      ToscaTimestamp("2016-01-23T23:26:44.964+0100").value must not be None
      ToscaTimestamp("2016-01-23T23:26:44.964+0100").compare(ToscaTimestamp("2016-01-23T22:26:44.964+0100")) must be > 0
    }
  }
}
