package com.toscaruntime.compiler

import java.nio.file.Paths

import _root_.tosca.nodes.Root
import com.toscaruntime.compiler.tosca.ParsedValue
import com.toscaruntime.util.GitClient

class CompilerSpec extends AbstractSpec {

  "Compiler" must {
    "be able to compile normative types" in {
      val normativeTypesOutput = TestConstant.GIT_TEST_DATA_PATH.resolve("normative-types")
      GitClient.clone("https://github.com/alien4cloud/tosca-normative-types.git", normativeTypesOutput)
      val parseResult = Compiler.compile(normativeTypesOutput)
      parseResult.errors.foreach {
        case (path, errors) => errors.foreach { error =>
          logger.error("At [{}][{}.{}] is {}", Paths.get(path).getFileName, error.startPosition.line.toString, error.startPosition.column.toString, error.error)
        }
      }
      parseResult.isSuccessful must be(true)
      val definition = parseResult.csars.values.head.definitions.values.head
      definition.nodeTypes.get.contains(ParsedValue(classOf[Root].getName)) must be(true)
      definition.relationshipTypes.get.contains(ParsedValue(classOf[_root_.tosca.relationships.Root].getName)) must be(true)
      definition.relationshipTypes.get.contains(ParsedValue(classOf[_root_.tosca.relationships.HostedOn].getName)) must be(true)
      definition.relationshipTypes.get.contains(ParsedValue(classOf[_root_.tosca.relationships.AttachTo].getName)) must be(true)
      definition.relationshipTypes.get.contains(ParsedValue(classOf[_root_.tosca.relationships.Network].getName)) must be(true)
    }
  }
}
