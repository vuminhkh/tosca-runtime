package com.toscaruntime.compiler.tosca

import com.toscaruntime.compiler.Tokens
import com.toscaruntime.tosca._

import scala.util.parsing.input.Positional

case class Csar(path: String, definitions: Map[String, Definition]) {

  def csarName = definitions.values.head.name.get.value

  def csarVersion = definitions.values.head.version.get.value

  def csarId = csarName + ":" + csarVersion
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
                       properties: Option[Map[ParsedValue[String], EvaluableFieldValue]],
                       targetNode: Option[ParsedValue[String]],
                       targetCapability: Option[ParsedValue[String]],
                       relationshipType: Option[ParsedValue[String]]) extends Positional

case class Capability(name: ParsedValue[String],
                      properties: Map[ParsedValue[String], EvaluableFieldValue]) extends Positional

case class NodeTemplate(name: ParsedValue[String],
                        typeName: Option[ParsedValue[String]],
                        properties: Option[Map[ParsedValue[String], EvaluableFieldValue]],
                        requirements: Option[List[Requirement]],
                        capabilities: Option[Map[ParsedValue[String], Capability]]) extends Positional

case class Output(name: ParsedValue[String],
                  description: Option[ParsedValue[String]],
                  value: Option[EvaluableFieldValue]) extends Positional

case class TopologyTemplate(description: Option[ParsedValue[String]],
                            inputs: Option[Map[ParsedValue[String], PropertyDefinition]],
                            outputs: Option[Map[ParsedValue[String], Output]],
                            nodeTemplates: Option[Map[ParsedValue[String], NodeTemplate]]) extends Positional

case class DeploymentArtifact(ref: ParsedValue[String],
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
                            properties: Option[Map[ParsedValue[String], FieldValue]],
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

case class ScalarValue(value: String) extends PropertyValue[String]

object ScalarValue {

  def apply(parsedValue: ParsedValue[String]): ScalarValue = {
    val scalarValue = ScalarValue(parsedValue.value)
    scalarValue.setPos(parsedValue.pos)
    scalarValue
  }
}

case class ListValue(value: List[FieldValue]) extends PropertyValue[List[FieldValue]]

case class ComplexValue(value: Map[ParsedValue[String], FieldValue]) extends PropertyValue[Map[ParsedValue[String], FieldValue]]

trait FieldValue extends Positional

trait EvaluableFieldValue extends FieldValue

trait PropertyValue[T] extends EvaluableFieldValue {
  val value: T
}

trait FieldDefinition extends FieldValue {
  val valueType: ParsedValue[String]
  val required: ParsedValue[Boolean]
  val default: Option[EvaluableFieldValue]
  val constraints: Option[List[PropertyConstraint]]
  val description: Option[ParsedValue[String]]
  val entrySchema: Option[PropertyDefinition]
}

case class PropertyDefinition(valueType: ParsedValue[String],
                              required: ParsedValue[Boolean],
                              default: Option[EvaluableFieldValue],
                              constraints: Option[List[PropertyConstraint]],
                              description: Option[ParsedValue[String]],
                              entrySchema: Option[PropertyDefinition]) extends FieldDefinition

case class AttributeDefinition(valueType: ParsedValue[String],
                               required: ParsedValue[Boolean],
                               default: Option[EvaluableFieldValue],
                               constraints: Option[List[PropertyConstraint]],
                               description: Option[ParsedValue[String]],
                               entrySchema: Option[PropertyDefinition]) extends FieldDefinition

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
                                 description: Option[ParsedValue[String]],
                                 nodeFilter: Option[NodeFilter]) extends Positional

case class CapabilityDefinition(capabilityType: Option[ParsedValue[String]],
                                upperBound: ParsedValue[Int],
                                properties: Option[Map[ParsedValue[String], PropertyDefinition]],
                                description: Option[ParsedValue[String]]) extends Positional

case class Interface(description: Option[ParsedValue[String]],
                     operations: Map[ParsedValue[String], Operation]) extends Positional

case class Operation(description: Option[ParsedValue[String]],
                     inputs: Option[Map[ParsedValue[String], FieldValue]],
                     implementation: Option[ParsedValue[String]]) extends Positional

case class Function(function: ParsedValue[String], paths: Seq[ParsedValue[String]]) extends EvaluableFieldValue

case class CompositeFunction(function: ParsedValue[String], members: Seq[FieldValue]) extends EvaluableFieldValue

// reference can be a ParsedValue[String] or a List[ParsedValue[String]]
case class PropertyConstraint(operator: ParsedValue[String], reference: Any) extends Positional

object PropertyConstraint {

  def isValueValid(value: String, valueType: String, constraint: PropertyConstraint): Boolean = {
    val parsedValue = FieldDefinition.toToscaPrimitiveType(value, valueType)
    if (!parsedValue.isValid) {
      return false
    }
    constraint.operator.value match {
      case Tokens.equal_token =>
        if (!constraint.reference.isInstanceOf[ParsedValue[String]]) {
          false
        } else {
          val reference = FieldDefinition.toToscaPrimitiveType(constraint.reference.asInstanceOf[ParsedValue[String]].value, valueType)
          if (!reference.isValid) {
            false
          } else {
            parsedValue.value.get == reference.value.get
          }
        }
      case Tokens.valid_values_token =>
        if (!constraint.reference.isInstanceOf[List[ParsedValue[String]]]) {
          false
        } else {
          val listReference = constraint.reference.asInstanceOf[List[ParsedValue[String]]].map {
            case ParsedValue(referenceValue) => FieldDefinition.toToscaPrimitiveType(referenceValue, valueType)
          }
          if (listReference.exists(!_.isValid)) {
            false
          } else {
            listReference.exists(parsedValue.value.get == _.value.get)
          }
        }
      case Tokens.greater_than_token | Tokens.greater_or_equal_token | Tokens.less_than_token | Tokens.less_or_equal_token =>
        if (!FieldDefinition.isComparableType(valueType) || !constraint.reference.isInstanceOf[ParsedValue[String]]) {
          false
        } else {
          val toBeCompared = parsedValue.asInstanceOf[ToscaComparableType[Any]]
          val reference = FieldDefinition.toToscaPrimitiveType(constraint.reference.asInstanceOf[ParsedValue[String]].value, valueType).asInstanceOf[ToscaComparableType[Any]]
          if (!reference.isValid) {
            false
          } else {
            constraint.operator.value match {
              case Tokens.greater_than_token => toBeCompared > reference
              case Tokens.greater_or_equal_token => toBeCompared >= reference
              case Tokens.less_than_token => toBeCompared < reference
              case Tokens.less_or_equal_token => toBeCompared <= reference
            }
          }
        }
      case Tokens.in_range_token =>
        if (!FieldDefinition.isComparableType(valueType) || !constraint.reference.isInstanceOf[List[ParsedValue[String]]]) {
          false
        } else {
          val toBeCompared = parsedValue.asInstanceOf[ToscaComparableType[Any]]
          val listReference = constraint.reference.asInstanceOf[List[ParsedValue[String]]].map {
            case ParsedValue(referenceValue) => FieldDefinition.toToscaPrimitiveType(referenceValue, valueType).asInstanceOf[ToscaComparableType[Any]]
          }
          if (listReference.exists(!_.isValid)) {
            false
          } else {
            val minReference = listReference.head
            val maxReference = listReference.last
            minReference < toBeCompared && toBeCompared < maxReference
          }
        }
      case Tokens.length_token | Tokens.min_length_token | Tokens.max_length_token =>
        if (valueType != FieldDefinition.STRING) {
          false
        } else {
          val toBeCompared = parsedValue.asInstanceOf[ToscaString].value.get
          val referenceOpt = ToscaInteger(constraint.reference.asInstanceOf[ParsedValue[String]].value)
          if (!referenceOpt.isValid) {
            false
          } else {
            val reference = referenceOpt.value.get
            constraint.operator.value match {
              case Tokens.length_token => toBeCompared.length == reference
              case Tokens.min_length_token => toBeCompared.length >= reference
              case Tokens.max_length_token => toBeCompared.length <= reference
            }
          }
        }
      case Tokens.pattern_token =>
        if (valueType != FieldDefinition.STRING) {
          false
        } else {
          val toBeCompared = parsedValue.asInstanceOf[ToscaString].value.get
          val referenceOpt = ToscaString(constraint.reference.asInstanceOf[ParsedValue[String]].value)
          if (!referenceOpt.isValid) {
            false
          } else {
            val reference = referenceOpt.value.get
            toBeCompared.matches(reference)
          }
        }
    }
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
  val TIME = "scalar-unit.time"
  val FREQUENCY = "scalar-unit.frequency"
  val LIST = "list"
  val MAP = "map"

  /**
    * Check if given type is native, native types are primitives + list and map
    *
    * @param valueType type to check
    * @return true if it's tosca native type (not complex data type)
    */
  def isToscaNativeType(valueType: String) = {
    isToscaPrimitiveType(valueType) || valueType == LIST || valueType == MAP
  }

  /**
    * Check if given type is primitive
    *
    * @param valueType type to check
    * @return true if it's tosca native type (not complex data type)
    */
  def isToscaPrimitiveType(valueType: String) = {
    valueType match {
      case STRING | INTEGER | FLOAT | BOOLEAN | TIMESTAMP | VERSION | SIZE | TIME | FREQUENCY => true
      case _ => false
    }
  }

  /**
    * Type whom values can be compared
    *
    * @param valueType type to check
    * @return true if it's of type whom values can be compared
    */
  def isComparableType(valueType: String) = {
    valueType match {
      case INTEGER | FLOAT | BOOLEAN | TIMESTAMP | VERSION | SIZE | FREQUENCY | TIME => true
      case _ => false
    }
  }

  def isValidPrimitiveValue(valueAsText: String, valueType: String) = {
    toToscaPrimitiveType(valueAsText, valueType).isValid
  }

  def toToscaPrimitiveType(valueAsText: String, valueType: String) = {
    valueType match {
      case STRING => ToscaString(valueAsText)
      case INTEGER => ToscaInteger(valueAsText)
      case BOOLEAN => ToscaBoolean(valueAsText)
      case FLOAT => ToscaFloat(valueAsText)
      case TIMESTAMP => ToscaTimestamp(valueAsText)
      case TIME => ToscaTime(valueAsText)
      case SIZE => ToscaSize(valueAsText)
      case FREQUENCY => ToscaFrequency(valueAsText)
      case VERSION => ToscaVersion(valueAsText)
    }
  }
}