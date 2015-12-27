package com.toscaruntime.compiler

import com.toscaruntime.compiler.Tokens._

class SyntaxAnalyzerSpec extends AbstractSpec {

  "Syntax analyzer" must {
    "be able to parse multiline text" in {
      val line1 = "#This is a long text which spans on"
      val line2 = "multiple lines"
      val longTextWithNewLine =
        s"""$line1
           |$line2""".stripMargin
      val parseResultWithNewLine = SyntaxAnalyzer.parse(SyntaxAnalyzer.definition,
        s"""$description_token: |
           |  $line1
           |  $line2""".stripMargin)
      TestUtil.printResult(parseResultWithNewLine)
      parseResultWithNewLine.successful must be(true)
      parseResultWithNewLine.get.description.get.value must be(longTextWithNewLine)

      val longText = s"$line1 $line2"
      val parseResult = SyntaxAnalyzer.parse(SyntaxAnalyzer.definition,
        s"""$description_token: >
           |  $line1
           |  $line2""".stripMargin)
      TestUtil.printResult(parseResult)
      parseResult.successful must be(true)
      parseResult.get.description.get.value must be(longText)
    }
  }

  "Syntax analyzer" must {
    "be able to handle list of primitive type" in {
      val topology =
        s"""
           |$node_types_token:
           |  test.node_type:
           |    $properties_token:
           |      list_prop:
           |        $type_token: list
           |        $entry_schema_token:
           |          $type_token: integer
           |$topology_template_token:
           |  $node_templates_token:
           |    test_node:
           |      $properties_token:
           |        list_prop: [1, 2]
           |    test_node_2:
           |      $properties_token:
           |        list_prop:
           |          - 1
           |          - 2
         """.stripMargin
      val parseResult = SyntaxAnalyzer.parse(SyntaxAnalyzer.definition, topology)
      TestUtil.printResult(parseResult)
      parseResult.successful must be(true)
    }
  }
}
