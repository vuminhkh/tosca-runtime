package com.toscaruntime.compiler.runtime

trait RuntimeType {
  val className: String

  val packageName: String

  val isAbstract: Boolean

  val superClass: Option[String]

  val methods: Seq[Method]

  val csarName: String

  val deploymentArtifacts: Map[String, String]

  val propertiesDefinitions: Map[String, Value]

  val attributesDefinitions: Map[String, Value]
}

case class NodeType(className: String,
                    packageName: String,
                    isAbstract: Boolean,
                    superClass: Option[String],
                    methods: Seq[Method],
                    csarName: String,
                    deploymentArtifacts: Map[String, String],
                    // One of StaticValue, Function or CompositeFunction
                    propertiesDefinitions: Map[String, Value],
                    // One of StaticValue, Function or CompositeFunction
                    attributesDefinitions: Map[String, Value]) extends RuntimeType

case class RelationshipType(className: String,
                            packageName: String,
                            isAbstract: Boolean,
                            superClass: Option[String],
                            methods: Seq[Method],
                            csarName: String,
                            deploymentArtifacts: Map[String, String],
                            // One of StaticValue, Function or CompositeFunction
                            propertiesDefinitions: Map[String, Value],
                            // One of StaticValue, Function or CompositeFunction
                            attributesDefinitions: Map[String, Value]) extends RuntimeType

trait Value

trait StaticValue extends Value {
  val value: String
}

case class Function(name: String,
                    paths: Seq[String]) extends Value

case class CompositeFunction(name: String,
                             // One of ScalarValue, Function or CompositeFunction
                             members: Seq[Value]) extends Value

case class ScalarValue(value: String) extends StaticValue

case class ListValue(value: String) extends StaticValue

case class ComplexValue(value: String) extends StaticValue

case class Method(name: String,
                  // One of StaticValue, Function or CompositeFunction
                  inputs: Map[String, Value],
                  implementation: Option[String])

case class Deployment(nodes: Seq[Node],
                      relationships: Seq[Relationship],
                      roots: Seq[Node],
                      // One of StaticValue, Function or CompositeFunction
                      outputs: Map[String, Value],
                      topologyCsarName: String)

case class Input(name: String)

class Node(var name: String,
           var typeName: String,
           var properties: Map[String, Value],
           // Map of capability key to capability properties (string to value map)
           var capabilityProperties: Map[String, Map[String, Value]],
           var host: Option[Node] = None,
           var parent: Option[Node] = None,
           var children: Seq[Node] = Seq.empty,
           var dependencies: Seq[Node] = Seq.empty)

class Relationship(var source: Node,
                   var target: Node,
                   var typeName: String,
                   var properties: Map[String, Value])