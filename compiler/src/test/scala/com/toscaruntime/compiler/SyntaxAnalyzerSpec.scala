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
        """node_types:
          |  test.node_type:
          |    properties:
          |      list_prop:
          |        type: list
          |        entry_schema:
          |          type: integer
          |topology_template:
          |  node_templates:
          |    test_node:
          |      properties:
          |        list_prop:
          |          - 1
          |          - 2
          |    test_node2:
          |      properties:
          |        list_prop: [1, 2, 3]
          |          """.stripMargin
      val parseResult = SyntaxAnalyzer.parse(SyntaxAnalyzer.definition, topology)
      TestUtil.printResult(parseResult)
      parseResult.successful must be(true)
    }
  }

  "Syntax analyzer" must {
    "be able to handle map of primitive type" in {
      val topology =
        """node_types:
          |  test.node_type:
          |    properties:
          |      simple_prop:
          |        type: integer
          |      map_prop:
          |        type: map
          |        entry_schema:
          |          type: string
          |topology_template:
          |  node_templates:
          |    test_node:
          |      properties:
          |        map_prop:
          |          a: b
          |          c: d
          |    test_node2:
          |      properties:
          |        simple_prop: 3
          |        map_prop: { a: b, c: d }
        """.stripMargin
      val parseResult = SyntaxAnalyzer.parse(SyntaxAnalyzer.definition, topology)
      TestUtil.printResult(parseResult)
      parseResult.successful must be(true)
    }
  }

  "Syntax analyzer" must {
    "be able to handle complex data type" in {
      val topology =
        """
          |data_types:
          |  test.data_type:
          |    properties:
          |      int_prop:
          |        type: integer
          |      map_prop:
          |        type: map
          |        entry_schema:
          |          type: string
          |        default:
          |          a: b
          |          c: d
          |      list_of_map_prop:
          |        type: list
          |        entry_schema:
          |          type: map
          |      list_prop:
          |        type: list
          |        entry_schema:
          |          type: test.data_type
          |
          |node_types:
          |  test.node_type:
          |    properties:
          |      data_prop:
          |        type: test.data_type
          |
          |topology_template:
          |  node_templates:
          |    test_node:
          |      type: test.node_type
          |      properties:
          |        data_prop:
          |          int_prop: 1
          |          map_prop: { d: [e, g], k: {i: j, h: u} }
          |          nested_list_prop: [{a: b, c: d}, {x: y}]
          |          list_of_map_prop:
          |            - x: y
          |              z: t
          |            - titi: toto
          |              tata: toctoc
          |          list_prop:
          |            - data1:
          |                int_prop: 2
          |                map_prop: { f: g }
          |            - data2:
          |                int_prop: 3
          |                map_prop: { h: i }
        """.stripMargin
      val parseResult = SyntaxAnalyzer.parse(SyntaxAnalyzer.definition, topology)
      TestUtil.printResult(parseResult)
      parseResult.successful must be(true)
    }
  }
}
