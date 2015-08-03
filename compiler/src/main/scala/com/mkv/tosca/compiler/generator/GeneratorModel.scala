package com.mkv.tosca.compiler.generator

case class NodeType(className: String,
                    packageName: String,
                    isAbstract: Boolean,
                    superClass: String,
                    methods: Seq[Method])

case class Function(name: String,
                    entity: String,
                    path: String)

case class Method(name: String,
                  scalarInputs: Map[String, String],
                  functionInputs: Map[String, Function],
                  implementation: Option[String])