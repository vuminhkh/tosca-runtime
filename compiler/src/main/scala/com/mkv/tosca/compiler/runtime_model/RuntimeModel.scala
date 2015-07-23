package com.mkv.tosca.compiler.runtime_model

import java.nio.file.Path

case class Deployment(nodes: Seq[Node], relationships: Seq[Relationship], workFlows: Seq[WorkFlow])

case class Node(name: String, parent: Node, children: Seq[Node], operations: Seq[Operation], instances: Int)

case class Relationship(source: Node, target: Node, operations: Seq[Operation])

case class Operation(name: String, inputs: Seq[OperationInput], implementation: Path)

trait OperationInput {
  val name: String
}

case class StaticValue(name: String, input: String) extends OperationInput

case class AttributeValue(name: String, node: String, attribute: String) extends OperationInput

case class WorkFlow(name: String, task: Task)

trait Task {
}

trait CompositeTask extends Task {
  val tasks: Seq[Task]
}

case class Execution(nodeName: String, operationName: String) extends Task

case class Sequence(tasks: Seq[Task]) extends CompositeTask

case class Concurrence(tasks: Seq[Task]) extends CompositeTask