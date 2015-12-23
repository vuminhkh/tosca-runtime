package com.toscaruntime.compiler.tosca

import java.nio.file.Path
import java.text.DateFormat
import java.util.Locale

import scala.util.parsing.input.Positional

case class Csar(path: Path, definitions: Map[String, Definition]) {

  def csarName = {
    definitions.values.head.name.get.value
  }

  def csarVersion = {
    definitions.values.head.version.get.value
  }
}

case class ParsedValue[T](value: T) extends Positional

case class Definition(definitionVersion: Option[ParsedValue[String]],
                      name: Option[ParsedValue[String]],
                      version: Option[ParsedValue[String]],
                      imports: Option[List[ParsedValue[String]]],
                      author: Option[ParsedValue[String]],
                      description: Option[ParsedValue[String]],
                      nodeTypes: Option[Map[ParsedValue[String], NodeType]],
                      dataTypes: Option[Map[ParsedValue[String], DataType]],
                      capabilityTypes: Option[Map[ParsedValue[String], CapabilityType]],
                      relationshipTypes: Option[Map[ParsedValue[String], RelationshipType]],
                      artifactTypes: Option[Map[ParsedValue[String], ArtifactType]],
                      groupTypes: Option[Map[ParsedValue[String], GroupType]],
                      policyTypes: Option[Map[ParsedValue[String], PolicyType]],
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
  val properties: Option[Map[ParsedValue[String], FieldValue]]
}

trait RuntimeType extends Type {
  val attributes: Option[Map[ParsedValue[String], FieldValue]]
  val artifacts: Option[Map[ParsedValue[String], DeploymentArtifact]]
  val interfaces: Option[Map[ParsedValue[String], Interface]]
}

case class GroupType(name: ParsedValue[String],
                     derivedFrom: Option[ParsedValue[String]],
                     description: Option[ParsedValue[String]],
                     interfaces: Option[Map[ParsedValue[String], Interface]]) extends Positional

case class PolicyType(name: ParsedValue[String],
                      derivedFrom: Option[ParsedValue[String]],
                      description: Option[ParsedValue[String]]) extends Positional

case class DataType(name: ParsedValue[String],
                    isAbstract: ParsedValue[Boolean],
                    derivedFrom: Option[ParsedValue[String]],
                    description: Option[ParsedValue[String]],
                    properties: Option[Map[ParsedValue[String], FieldValue]]) extends Positional with Type

case class Requirement(name: ParsedValue[String],
                       targetNode: Option[ParsedValue[String]],
                       targetCapability: Option[ParsedValue[String]]) extends Positional

case class NodeTemplate(name: ParsedValue[String],
                        typeName: Option[ParsedValue[String]],
                        properties: Option[Map[ParsedValue[String], FieldValue]],
                        requirements: Option[List[Requirement]]) extends Positional

case class Output(name: ParsedValue[String],
                  description: Option[ParsedValue[String]],
                  value: Option[FieldValue]) extends Positional

case class TopologyTemplate(description: Option[ParsedValue[String]],
                            inputs: Option[Map[ParsedValue[String], PropertyDefinition]],
                            outputs: Option[Map[ParsedValue[String], Output]],
                            nodeTemplates: Option[Map[ParsedValue[String], NodeTemplate]]) extends Positional

case class DeploymentArtifact(name: ParsedValue[String],
                              typeName: ParsedValue[String]) extends Positional

case class NodeType(name: ParsedValue[String],
                    isAbstract: ParsedValue[Boolean],
                    derivedFrom: Option[ParsedValue[String]],
                    description: Option[ParsedValue[String]],
                    tags: Option[Map[ParsedValue[String], ParsedValue[String]]],
                    properties: Option[Map[ParsedValue[String], FieldValue]],
                    attributes: Option[Map[ParsedValue[String], FieldValue]],
                    requirements: Option[Map[ParsedValue[String], RequirementDefinition]],
                    capabilities: Option[Map[ParsedValue[String], CapabilityDefinition]],
                    artifacts: Option[Map[ParsedValue[String], DeploymentArtifact]],
                    interfaces: Option[Map[ParsedValue[String], Interface]]) extends Positional with RuntimeType

case class RelationshipType(name: ParsedValue[String],
                            isAbstract: ParsedValue[Boolean],
                            derivedFrom: Option[ParsedValue[String]],
                            description: Option[ParsedValue[String]],
                            properties: Option[Map[ParsedValue[String], PropertyDefinition]],
                            attributes: Option[Map[ParsedValue[String], FieldValue]],
                            validSources: Option[List[ParsedValue[String]]],
                            validTargets: Option[List[ParsedValue[String]]],
                            artifacts: Option[Map[ParsedValue[String], DeploymentArtifact]],
                            interfaces: Option[Map[ParsedValue[String], Interface]]) extends Positional with RuntimeType

case class CapabilityType(name: ParsedValue[String],
                          isAbstract: ParsedValue[Boolean],
                          derivedFrom: Option[ParsedValue[String]],
                          description: Option[ParsedValue[String]],
                          properties: Option[Map[ParsedValue[String], PropertyDefinition]]) extends Positional with Type

case class ScalarValue(value: String) extends FieldValue

object ScalarValue {

  def apply(parsedValue: ParsedValue[String]) = {
    val instance = new ScalarValue(parsedValue.value)
    instance.setPos(parsedValue.pos)
    instance
  }
}

trait FieldValue extends Positional

trait FieldDefinition extends FieldValue {
  val valueType: ParsedValue[String]
  val description: Option[ParsedValue[String]]
  val default: Option[ParsedValue[String]]
}

case class PropertyDefinition(valueType: ParsedValue[String],
                              required: ParsedValue[Boolean],
                              default: Option[ParsedValue[String]],
                              constraints: Option[List[PropertyConstraint]],
                              description: Option[ParsedValue[String]],
                              entrySchema: Option[PropertyDefinition]) extends FieldDefinition

case class AttributeDefinition(valueType: ParsedValue[String],
                               description: Option[ParsedValue[String]],
                               default: Option[ParsedValue[String]]) extends FieldDefinition

trait FilterDefinition extends Positional {
  val properties: Map[ParsedValue[String], List[PropertyConstraint]]
}

case class PropertiesFilter(properties: Map[ParsedValue[String], List[PropertyConstraint]]) extends FilterDefinition

case class NodeFilter(properties: Map[ParsedValue[String], List[PropertyConstraint]],
                      capabilities: Map[ParsedValue[String], FilterDefinition]) extends FilterDefinition

case class RequirementDefinition(name: ParsedValue[String],
                                 capabilityType: Option[ParsedValue[String]],
                                 relationshipType: Option[ParsedValue[String]],
                                 lowerBound: ParsedValue[Int],
                                 upperBound: ParsedValue[Int],
                                 description: Option[ParsedValue[String]]) extends Positional

case class CapabilityDefinition(capabilityType: Option[ParsedValue[String]],
                                upperBound: ParsedValue[Int],
                                properties: Option[Map[ParsedValue[String], PropertyDefinition]],
                                description: Option[ParsedValue[String]]) extends Positional

case class Interface(description: Option[ParsedValue[String]],
                     operations: Map[ParsedValue[String], Operation]) extends Positional

case class Operation(description: Option[ParsedValue[String]],
                     inputs: Option[Map[ParsedValue[String], FieldValue]],
                     implementation: Option[ParsedValue[String]]) extends Positional

case class Function(function: ParsedValue[String], paths: Seq[ParsedValue[String]]) extends FieldValue

case class CompositeFunction(function: ParsedValue[String], members: Seq[FieldValue]) extends FieldValue

case class PropertyConstraint(operator: ParsedValue[String], reference: Any) extends Positional

case class Version(version: String)

trait ScalarUnitType {
  val value: Double
  val unit: String
}

case class Size(value: Double, unit: String)

case class Frequency(value: Double, unit: String)

object Evaluator {
  def eval[T](value: String, propType: String): T = {
    (propType match {
      case FieldDefinition.STRING => value
      case FieldDefinition.INTEGER => value.toLong
      case FieldDefinition.FLOAT => value.toDouble
      case FieldDefinition.BOOLEAN => value.toBoolean
      case FieldDefinition.TIMESTAMP => DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, Locale.US).parse(value)
      case FieldDefinition.VERSION => Version(value)
      case FieldDefinition.SIZE =>
        val tokens = value.split("\\s+")
        Size(tokens(0).toDouble, tokens(1))
      case FieldDefinition.FREQUENCY =>
        val tokens = value.split("\\s+")
        Frequency(tokens(0).toDouble, tokens(1))
    }).asInstanceOf[T]
  }
}

object FieldDefinition {
  val STRING = "string"
  val INTEGER = "integer"
  val FLOAT = "float"
  val BOOLEAN = "boolean"
  val TIMESTAMP = "timestamp"
  val VERSION = "version"
  val SIZE = "scalar-unit.size"
  val FREQUENCY = "scalar-unit.frequency"

  def isTypePrimitive(propType: String) = {
    propType match {
      case STRING | INTEGER | FLOAT | BOOLEAN | TIMESTAMP | VERSION | SIZE | FREQUENCY => true
      case _ => false
    }
  }

  def isTypeComparable(propType: String) = {
    propType match {
      case INTEGER | FLOAT | BOOLEAN | TIMESTAMP | VERSION | SIZE | FREQUENCY => true
      case _ => false
    }
  }
}