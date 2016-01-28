package com.toscaruntime.compiler.tosca

import com.toscaruntime.compiler.Tokens
import org.scalatest.{MustMatchers, WordSpec}

class ToscaModelsSpec extends WordSpec with MustMatchers {

  "Field definitions" must {
    "be able to validate values based on its type" in {
      FieldDefinition.isValidPrimitiveValue("1", FieldDefinition.INTEGER) must be(true)
      FieldDefinition.isValidPrimitiveValue("1", FieldDefinition.STRING) must be(true)
      FieldDefinition.isValidPrimitiveValue("1", FieldDefinition.SIZE) must be(false)
      FieldDefinition.isValidPrimitiveValue("1 GiB", FieldDefinition.SIZE) must be(true)
    }
  }

  "Properties constraints" must {
    "be able to validate values based on constraint" in {
      PropertyConstraint.isValueValid("1 TiB", FieldDefinition.SIZE, PropertyConstraint(ParsedValue(Tokens.greater_than_token), ParsedValue("512 MiB"))) must be(true)
      PropertyConstraint.isValueValid("1 TiB", FieldDefinition.SIZE, PropertyConstraint(ParsedValue(Tokens.less_than_token), ParsedValue("512 MiB"))) must be(false)
      PropertyConstraint.isValueValid("5", FieldDefinition.INTEGER, PropertyConstraint(ParsedValue(Tokens.greater_or_equal_token), ParsedValue("6"))) must be(false)
      PropertyConstraint.isValueValid("5", FieldDefinition.INTEGER, PropertyConstraint(ParsedValue(Tokens.less_or_equal_token), ParsedValue("6"))) must be(true)
      PropertyConstraint.isValueValid("5", FieldDefinition.INTEGER, PropertyConstraint(ParsedValue(Tokens.in_range_token), List(ParsedValue("6"), ParsedValue("12")))) must be(false)
      PropertyConstraint.isValueValid("7", FieldDefinition.INTEGER, PropertyConstraint(ParsedValue(Tokens.in_range_token), List(ParsedValue("6"), ParsedValue("12")))) must be(true)
      PropertyConstraint.isValueValid("7", FieldDefinition.STRING, PropertyConstraint(ParsedValue(Tokens.pattern_token), ParsedValue("""\d+"""))) must be(true)
      PropertyConstraint.isValueValid("7", FieldDefinition.STRING, PropertyConstraint(ParsedValue(Tokens.pattern_token), ParsedValue("""\s+"""))) must be(false)
      PropertyConstraint.isValueValid("7", FieldDefinition.STRING, PropertyConstraint(ParsedValue(Tokens.valid_values_token), List(ParsedValue("6"), ParsedValue("12")))) must be(false)
      PropertyConstraint.isValueValid("7", FieldDefinition.STRING, PropertyConstraint(ParsedValue(Tokens.valid_values_token), List(ParsedValue("6"), ParsedValue("7")))) must be(true)
      PropertyConstraint.isValueValid("text", FieldDefinition.STRING, PropertyConstraint(ParsedValue(Tokens.length_token), ParsedValue("6"))) must be(false)
      PropertyConstraint.isValueValid("text", FieldDefinition.STRING, PropertyConstraint(ParsedValue(Tokens.length_token), ParsedValue("4"))) must be(true)
      PropertyConstraint.isValueValid("text", FieldDefinition.STRING, PropertyConstraint(ParsedValue(Tokens.min_length_token), ParsedValue("2"))) must be(true)
      PropertyConstraint.isValueValid("text", FieldDefinition.STRING, PropertyConstraint(ParsedValue(Tokens.min_length_token), ParsedValue("5"))) must be(false)
      PropertyConstraint.isValueValid("text", FieldDefinition.STRING, PropertyConstraint(ParsedValue(Tokens.max_length_token), ParsedValue("6"))) must be(true)
      PropertyConstraint.isValueValid("text", FieldDefinition.STRING, PropertyConstraint(ParsedValue(Tokens.max_length_token), ParsedValue("3"))) must be(false)
      PropertyConstraint.isValueValid("text", FieldDefinition.STRING, PropertyConstraint(ParsedValue(Tokens.equal_token), ParsedValue("text"))) must be(true)
      PropertyConstraint.isValueValid("text", FieldDefinition.STRING, PropertyConstraint(ParsedValue(Tokens.equal_token), ParsedValue("another text"))) must be(false)
    }
  }
}
