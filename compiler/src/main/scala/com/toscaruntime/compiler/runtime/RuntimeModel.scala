package com.toscaruntime.compiler.runtime

trait RuntimeType {
  val className: String

  val packageName: String

  val isAbstract: Boolean

  val superClass: Option[String]

  val methods: Seq[Method]

  val csarName: String
}

case class NodeType(className: String,
                    packageName: String,
                    isAbstract: Boolean,
                    superClass: Option[String],
                    methods: Seq[Method],
                    csarName: String) extends RuntimeType

case class RelationshipType(className: String,
                            packageName: String,
                            isAbstract: Boolean,
                            superClass: Option[String],
                            methods: Seq[Method],
                            csarName: String) extends RuntimeType

case class Function(name: String,
                    entity: String,
                    path: String)

case class CompositeFunction(name: String,
                             members: Seq[Any])

case class Method(name: String,
                  scalarInputs: Map[String, String],
                  functionInputs: Map[String, Function],
                  implementation: Option[String])

case class Deployment(nodes: Seq[Node],
                      relationships: Seq[Relationship],
                      roots: Seq[Node],
                      // One of String, Function or CompositeFunction
                      outputs: Map[String, Any],
                      topologyCsarName: String)

case class Input(name: String)

class Node(var name: String,
           var typeName: String,
           var scalarProperties: Map[String, String],
           var inputProperties: Map[String, Input],
           var parent: Option[Node] = None,
           var children: Seq[Node] = Seq.empty,
           var dependencies: Seq[Node] = Seq.empty,
           var instanceCount: Int = 1)

class Relationship(var source: Node,
                   var target: Node,
                   var typeName: String)