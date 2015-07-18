package com.mkv.tosca.compiler.model

import java.nio.file.Path
import java.text.DateFormat
import java.util.Locale

import scala.util.parsing.input.Positional

case class Csar(definitions: Map[Path, Definition], path: Path)

case class ParsedValue[T](value: T) extends Positional

case class Definition(definitionVersion: Option[ParsedValue[String]],
                      name: Option[ParsedValue[String]],
                      version: Option[ParsedValue[String]],
                      author: Option[ParsedValue[String]],
                      description: Option[ParsedValue[String]],
                      nodeTypes: Option[Map[ParsedValue[String], NodeType]],
                      capabilityTypes: Option[Map[ParsedValue[String], CapabilityType]],
                      relationshipTypes: Option[Map[ParsedValue[String], RelationshipType]],
                      artifactTypes: Option[Map[ParsedValue[String], ArtifactType]],
                      topologyTemplate: Option[TopologyTemplate]) extends Positional

case class ArtifactType(name: ParsedValue[String],
                        derivedFrom: Option[ParsedValue[String]],
                        description: Option[ParsedValue[String]],
                        fileExtension: Option[List[ParsedValue[String]]]) extends Positional

trait Type {
  val name: ParsedValue[String]
  val description: Option[ParsedValue[String]]
  val isAbstract: ParsedValue[Boolean]
  val derivedFrom: Option[ParsedValue[String]]
  val properties: Option[Map[ParsedValue[String], PropertyDefinition]]
}

trait RuntimeType extends Type {
  val artifacts: Option[Map[ParsedValue[String], ParsedValue[String]]]
  val interfaces: Option[Map[ParsedValue[String], Interface]]
}

case class Input(name: ParsedValue[String]) extends Positional

case class Requirement(name: ParsedValue[String],
                       targetNode: Option[ParsedValue[String]],
                       targetCapability: Option[ParsedValue[String]]) extends Positional

case class NodeTemplate(name: ParsedValue[String],
                        typeName: Option[ParsedValue[String]],
                        properties: Option[Map[ParsedValue[String], Any]],
                        requirements: Option[List[Requirement]]) extends Positional

case class TopologyTemplate(description: Option[ParsedValue[String]],
                            inputs: Option[Map[ParsedValue[String], PropertyDefinition]],
                            nodeTemplates: Option[Map[ParsedValue[String], NodeTemplate]]) extends Positional

case class NodeType(name: ParsedValue[String],
                    isAbstract: ParsedValue[Boolean],
                    derivedFrom: Option[ParsedValue[String]],
                    description: Option[ParsedValue[String]],
                    tags: Option[Map[ParsedValue[String], ParsedValue[String]]],
                    properties: Option[Map[ParsedValue[String], PropertyDefinition]],
                    attributes: Option[Map[ParsedValue[String], AttributeDefinition]],
                    requirements: Option[Map[ParsedValue[String], RequirementDefinition]],
                    capabilities: Option[Map[ParsedValue[String], CapabilityDefinition]],
                    artifacts: Option[Map[ParsedValue[String], ParsedValue[String]]],
                    interfaces: Option[Map[ParsedValue[String], Interface]]) extends Positional with RuntimeType

case class RelationshipType(name: ParsedValue[String],
                            isAbstract: ParsedValue[Boolean],
                            derivedFrom: Option[ParsedValue[String]],
                            description: Option[ParsedValue[String]],
                            properties: Option[Map[ParsedValue[String], PropertyDefinition]],
                            validSources: Option[List[ParsedValue[String]]],
                            validTargets: Option[List[ParsedValue[String]]],
                            artifacts: Option[Map[ParsedValue[String], ParsedValue[String]]],
                            interfaces: Option[Map[ParsedValue[String], Interface]]) extends Positional with RuntimeType

case class CapabilityType(name: ParsedValue[String],
                          isAbstract: ParsedValue[Boolean],
                          derivedFrom: Option[ParsedValue[String]],
                          description: Option[ParsedValue[String]],
                          properties: Option[Map[ParsedValue[String], PropertyDefinition]]) extends Positional with Type

trait Field {
  val valueType: ParsedValue[String]
  val description: Option[ParsedValue[String]]
  val default: Option[ParsedValue[String]]
}

case class PropertyDefinition(valueType: ParsedValue[String],
                              required: ParsedValue[Boolean],
                              default: Option[ParsedValue[String]],
                              constraints: Option[List[PropertyConstraint]],
                              description: Option[ParsedValue[String]]) extends Positional with Field

case class AttributeDefinition(valueType: ParsedValue[String],
                               description: Option[ParsedValue[String]],
                               default: Option[ParsedValue[String]]) extends Positional with Field

case class RequirementDefinition(capabilityType: Option[ParsedValue[String]],
                                 relationshipType: Option[ParsedValue[String]],
                                 lowerBound: ParsedValue[Int],
                                 upperBound: ParsedValue[Int]) extends Positional

case class CapabilityDefinition(capabilityType: Option[ParsedValue[String]],
                                upperBound: ParsedValue[Int],
                                properties: Option[Map[ParsedValue[String], PropertyDefinition]]) extends Positional

case class Interface(description: Option[ParsedValue[String]],
                     operations: Map[ParsedValue[String], Operation]) extends Positional

case class Operation(description: Option[ParsedValue[String]],
                     inputs: Option[Map[ParsedValue[String], Any]],
                     implementation: Option[ParsedValue[String]]) extends Positional

case class Function(function: ParsedValue[String],
                    entity: ParsedValue[String],
                    path: ParsedValue[String]) extends Positional

case class PropertyConstraint(operator: ParsedValue[String], reference: Any) extends Positional

case class Version(version: String)

object Evaluator {
  def eval[T](value: String, propType: String): T = {
    (propType match {
      case Field.STRING => value
      case Field.INTEGER => value.toLong
      case Field.FLOAT => value.toDouble
      case Field.BOOLEAN => value.toBoolean
      case Field.TIMESTAMP => DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.US).parse(value)
      case Field.VERSION => Version(value)
    }).asInstanceOf[T]
  }
}

object Field {
  val STRING = "string"
  val INTEGER = "integer"
  val FLOAT = "float"
  val BOOLEAN = "boolean"
  val TIMESTAMP = "timestamp"
  val VERSION = "version"

  def isTypeValid(propType: String) = {
    propType == STRING || propType == INTEGER || propType == FLOAT || propType == BOOLEAN || propType == TIMESTAMP || propType == VERSION
  }

  def isTypeComparable(propType: String) = {
    propType == INTEGER || propType == FLOAT || propType == TIMESTAMP || propType == VERSION
  }
}