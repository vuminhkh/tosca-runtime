package com.mkv.tosca.compiler.runtime_model

import java.nio.file.Path

case class Deployment(nodes: Seq[Node], workFlows: Seq[WorkFlow])

case class Node(name: String, operations: Seq[Operation], instances: Int)

case class Operation(name: String, inputs: Seq[OperationInput], implementation: Path)

trait OperationInput {
  val name: String
}

case class StaticValue(name: String, input: String) extends OperationInput

case class AttributeValue(name: String, node: String, attribute: String) extends OperationInput

case class WorkFlow(name: String, step: Step)

trait Step {
}

trait CompositeStep extends Step {
  val steps: Seq[Step]
}

case class Execution(nodeName: String, operationName: String) extends Step

case class Sequence(steps: Seq[Step]) extends CompositeStep

case class Concurrence(steps: Seq[Step]) extends CompositeStep