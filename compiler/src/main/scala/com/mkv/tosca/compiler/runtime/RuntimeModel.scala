package com.mkv.tosca.compiler.runtime

trait RuntimeType {
  def className: String

  def packageName: String

  def isAbstract: Boolean

  def superClass: Option[String]

  def methods: Seq[Method]
}

case class NodeType(className: String,
                    packageName: String,
                    isAbstract: Boolean,
                    superClass: Option[String],
                    methods: Seq[Method]) extends RuntimeType

case class RelationshipType(className: String,
                            packageName: String,
                            isAbstract: Boolean,
                            superClass: Option[String],
                            methods: Seq[Method]) extends RuntimeType

case class Function(name: String,
                    entity: String,
                    path: String)

case class Method(name: String,
                  scalarInputs: Map[String, String],
                  functionInputs: Map[String, Function],
                  implementation: Option[String])

case class Deployment(nodes: Seq[Node],
                      relationships: Seq[Relationship],
                      roots: Seq[Node])

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
                   var typeName: String) {
  def name = {
    source.name + "_" + target.name
  }
}