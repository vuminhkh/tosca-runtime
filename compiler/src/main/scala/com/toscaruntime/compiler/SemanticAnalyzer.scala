package com.toscaruntime.compiler

import java.nio.file.{Files, Path}

import com.toscaruntime.compiler.tosca._
import com.toscaruntime.compiler.util.CompilerUtil
import com.toscaruntime.util.FileUtil

import scala.collection.mutable.ListBuffer
import scala.util.parsing.input.Positional

object SemanticAnalyzer {

  def analyzeScalarValue(scalar: ScalarValue, valueType: FieldDefinition, csarPath: List[Csar]): List[CompilationError] = {
    val compilationErrors = ListBuffer[CompilationError]()
    if (!FieldDefinition.isValidPrimitiveValue(scalar.value, valueType.valueType.value)) {
      compilationErrors += CompilationError(s"Value [${scalar.value}] is not valid for type [${valueType.valueType.value}]", scalar.pos, Some(scalar.value))
    } else {
      valueType.constraints.foreach { constraints =>
        constraints.foreach { constraint =>
          val referenceAsText = constraint.reference match {
            case list: List[ParsedValue[String]] => "[" + list.map(_.value).mkString(", ") + "]"
            case text: ParsedValue[String] => text.value
          }
          if (!PropertyConstraint.isValueValid(scalar.value, valueType.valueType.value, constraint)) {
            compilationErrors += CompilationError(s"Value [${scalar.value}] is not valid for constraint [${constraint.operator.value} $referenceAsText]", scalar.pos, Some(scalar.value))
          }
        }
      }
    }
    compilationErrors.toList
  }

  def analyzeListValue(list: ListValue, valueType: FieldDefinition, csarPath: List[Csar]): List[CompilationError] = {
    val compilationErrors = ListBuffer[CompilationError]()
    if (FieldDefinition.LIST != valueType.valueType.value) {
      val listValueAsText = CompilerUtil.serializePropertyValueToJson(list)
      compilationErrors += CompilationError(s"Value [$listValueAsText] is not valid for type [$valueType]", list.pos, Some(listValueAsText))
    } else {
      compilationErrors ++= valueType.entrySchema.map { entryDefinition =>
        list.value.flatMap(analyzeValue(_, entryDefinition, csarPath))
      }.getOrElse(List.empty)
    }
    compilationErrors.toList
  }

  def analyzeMapValue(mapValue: ComplexValue, valueType: FieldDefinition, csarPath: List[Csar]): List[CompilationError] = {
    valueType.entrySchema.map { entryDefinition =>
      mapValue.value.values.flatMap(analyzeValue(_, entryDefinition, csarPath))
    }.getOrElse(List.empty).toList
  }

  def analyzeDataTypeValue(complex: ComplexValue, propertiesDefinitionsOpt: Option[Map[ParsedValue[String], FieldValue]], csarPath: List[Csar]): List[CompilationError] = {
    val compilationErrors = ListBuffer[CompilationError]()
    val propertiesDefinitions = propertiesDefinitionsOpt.getOrElse(Map.empty)
    complex.value.foreach {
      case (propertyName: ParsedValue[String], propertyValue: FieldValue) =>
        if (propertiesDefinitions.isEmpty) {
          compilationErrors += CompilationError(s"Property with name ${propertyName.value} is not expected, not any property has been defined for this type", propertyName.pos, Some(propertyName.value))
        } else {
          val propertyDefinition = propertiesDefinitions.get(propertyName)
          if (propertyDefinition.isEmpty) {
            compilationErrors += CompilationError(s"Property with name ${propertyName.value} is not expected, expecting one of [${propertiesDefinitions.keys.map(_.value).mkString(", ")}]", propertyName.pos, Some(propertyName.value))
          } else if (!propertyDefinition.get.isInstanceOf[FieldDefinition]) {
            compilationErrors += CompilationError(s"Property with name ${propertyName.value} is not expected, its value has been fixed or a function has been declared for this property", propertyName.pos, Some(propertyName.value))
          } else {
            compilationErrors ++= analyzeValue(propertyValue, propertyDefinition.get.asInstanceOf[FieldDefinition], csarPath)
          }
        }
      case _ => None
    }
    propertiesDefinitions.filter {
      case (name, definition) =>
        !complex.value.contains(name) &&
          definition.isInstanceOf[FieldDefinition] &&
          definition.asInstanceOf[FieldDefinition].required.value &&
          definition.asInstanceOf[FieldDefinition].default.isEmpty
    }.foreach {
      case (name, definition) => compilationErrors += CompilationError(s"Property with name ${name.value} is required", complex.pos, None)
    }
    compilationErrors.toList
  }

  def analyzeValue(value: FieldValue, valueType: FieldDefinition, csarPath: List[Csar]): List[CompilationError] = {
    val compilationErrors = ListBuffer[CompilationError]()
    value match {
      case scalar: ScalarValue =>
        if (!FieldDefinition.isToscaPrimitiveType(valueType.valueType.value)) {
          compilationErrors += CompilationError(s"Expect non primitive type [${valueType.valueType.value}] but found primitive value [${scalar.value}]", scalar.pos, Some(scalar.value))
        } else {
          compilationErrors ++= analyzeScalarValue(scalar, valueType, csarPath)
        }
      case list: ListValue =>
        if (FieldDefinition.LIST != valueType.valueType.value) {
          val listValueAsText = CompilerUtil.serializePropertyValueToJson(list)
          compilationErrors += CompilationError(s"Value [$listValueAsText] is not valid for type [$valueType]", list.pos, Some(listValueAsText))
        } else {
          compilationErrors ++= analyzeListValue(list, valueType, csarPath)
        }
      case complex: ComplexValue =>
        if (!FieldDefinition.isToscaNativeType(valueType.valueType.value)) {
          val dataType = TypeLoader.loadDataTypeWithHierarchy(valueType.valueType.value, csarPath)
          if (dataType.isEmpty) {
            compilationErrors += CompilationError(s"Type [${valueType.valueType.value}] is not valid or cannot be found", valueType.valueType.pos, Some(valueType.valueType.value))
          } else {
            compilationErrors ++= analyzeDataTypeValue(complex, dataType.get.properties, csarPath)
          }
        } else {
          compilationErrors ++= analyzeMapValue(complex, valueType, csarPath)
        }
      case _ =>
    }
    compilationErrors.toList
  }

  def analyzeFieldDefinition(field: FieldDefinition, csarPath: List[Csar]): List[CompilationError] = {
    val compilationErrors = ListBuffer[CompilationError]()
    val propertyType = field.valueType
    if (!FieldDefinition.isToscaNativeType(propertyType.value)) {
      if (TypeLoader.loadDataType(propertyType.value, csarPath).isEmpty) {
        compilationErrors += CompilationError(s"Type [${propertyType.value}] is not valid or cannot be found", propertyType.pos, Some(propertyType.value))
      } else {
        compilationErrors ++= field.default.map(analyzeValue(_, field, csarPath)).getOrElse(List.empty)
      }
    } else {
      compilationErrors ++= field.default.map(analyzeValue(_, field, csarPath)).getOrElse(List.empty)
    }
    compilationErrors.toList
  }

  def analyzePropertyConstraintReference(referenceValueAsText: ParsedValue[String], propertyType: String) = {
    if (!FieldDefinition.isValidPrimitiveValue(referenceValueAsText.value, propertyType)) {
      Some(CompilationError(s"Property constraint's reference value [${referenceValueAsText.value}] is not valid for property type [$propertyType]", referenceValueAsText.pos, Some(referenceValueAsText.value)))
    } else None
  }

  def analyzePropertyDefinition(propertyDefinition: PropertyDefinition, csarPath: List[Csar]) = {
    val fieldErrors = analyzeFieldDefinition(propertyDefinition, csarPath)
    val propertyType = propertyDefinition.valueType.value
    val constraintReferenceErrors = propertyDefinition.constraints.map { constraints =>
      constraints.flatMap { constraint =>
        constraint.reference match {
          case textReference: ParsedValue[_] => analyzePropertyConstraintReference(textReference.asInstanceOf[ParsedValue[String]], propertyType).map(List(_)).getOrElse(List.empty)
          case listReference: List[_] => listReference.flatMap(textReference => analyzePropertyConstraintReference(textReference.asInstanceOf[ParsedValue[String]], propertyType))
        }
      }
    }.getOrElse(List.empty)
    val constraintTypeErrors = propertyDefinition.constraints.map { constraints =>
      constraints.flatMap { constraint =>
        constraint.operator.value match {
          case Tokens.greater_than_token | Tokens.greater_or_equal_token | Tokens.less_than_token | Tokens.less_or_equal_token | Tokens.in_range_token =>
            if (!FieldDefinition.isComparableType(propertyType)) {
              Some(CompilationError(s"Property constraint's [${constraint.operator.value}] needs comparable type, but incompatible [$propertyType] found", constraint.operator.pos, Some(constraint.operator.value)))
            } else None
          case Tokens.length_token | Tokens.min_length_token | Tokens.max_length_token | Tokens.pattern_token =>
            if (propertyType != FieldDefinition.STRING) {
              Some(CompilationError(s"Property constraint's [${constraint.operator.value}] needs string type , but incompatible [$propertyType] found", constraint.operator.pos, Some(constraint.operator.value)))
            } else None
          case _ => None
        }
      }
    }.getOrElse(List.empty)
    fieldErrors ++ constraintReferenceErrors ++ constraintTypeErrors
  }

  def analyzePropertyDefinitions(toscaType: Type, csarPath: List[Csar]) = {
    toscaType.properties.map(_.values.flatMap {
      case propertyDefinition: PropertyDefinition => analyzePropertyDefinition(propertyDefinition, csarPath)
      case function: Function =>
        toscaType match {
          case runtimeType: RuntimeType =>
            analyzeFunction(runtimeType, function)
          case _ =>
            List(CompilationError(s"Function is only supported on node type or relationship type", function.pos, Some(function.function.value)))
        }
      case compositeFunction: CompositeFunction =>
        toscaType match {
          case runtimeType: RuntimeType =>
            analyzeCompositeFunction(runtimeType, compositeFunction)
          case _ =>
            List(CompilationError(s"Composite function is only supported on node type or relationship type", compositeFunction.pos, Some(compositeFunction.function.value)))
        }
      case _ => List.empty
    }.toList).getOrElse(List.empty)
  }

  def analyzeAttributeDefinitions(nodeType: NodeType, csarPath: List[Csar]) = {
    nodeType.attributes.map(_.values.flatMap {
      case definition: AttributeDefinition => analyzeFieldDefinition(definition, csarPath)
      case function: Function => analyzeFunction(nodeType, function)
      case compositeFunction: CompositeFunction => analyzeCompositeFunction(nodeType, compositeFunction)
      case _ => List.empty
    }.toList).getOrElse(List.empty)
  }

  def analyzeDerivedFrom(typeName: ParsedValue[String], derivedFrom: Option[ParsedValue[String]], csarPath: List[Csar], typeLoader: (String, List[Csar]) => Option[_]): Option[CompilationError] = {
    derivedFrom.flatMap { parentTypeName =>
      val typeFound = typeLoader(parentTypeName.value, csarPath)
      if (typeFound.isEmpty) {
        Some(CompilationError("Type [" + typeName.value + "] derived from unknown type [" + parentTypeName.value + "]", parentTypeName.pos, Some(parentTypeName.value)))
      } else None
    }
  }

  def analyzeDerivedFrom(toscaType: Type, csarPath: List[Csar], typeLoader: (String, List[Csar]) => Option[_]): Option[CompilationError] = {
    analyzeDerivedFrom(toscaType.name, toscaType.derivedFrom, csarPath, typeLoader)
  }

  def analyzeRequirementDefinition(nodeType: NodeType, csarPath: List[Csar]) = {
    nodeType.requirements.map { requirements =>
      requirements.flatMap { requirement =>
        val compilationErrors = ListBuffer[CompilationError]()
        if (requirement._2.capabilityType.isEmpty) {
          compilationErrors += CompilationError("Requirement [" + requirement._1.value + "] has undefined type for capability", requirement._1.pos, Some(requirement._1.value))
        } else {
          val definedCapabilityType = requirement._2.capabilityType.get
          val capabilityFound = TypeLoader.loadCapabilityTypeWithHierarchy(definedCapabilityType.value, csarPath)
          if (capabilityFound.isEmpty) {
            val nodeTypeFound = TypeLoader.loadNodeType(definedCapabilityType.value, csarPath)
            if (nodeTypeFound.isEmpty) {
              compilationErrors += CompilationError("Requirement [" + requirement._1.value + "] refers to unknown capability " + definedCapabilityType.value, definedCapabilityType.pos, Some(definedCapabilityType.value))
            }
          }
        }
        if (requirement._2.lowerBound.value > requirement._2.upperBound.value) {
          compilationErrors += CompilationError("Requirement [" + requirement._1.value + "] has lower bound [" + requirement._2.lowerBound.value + "] greater than upper bound [" + requirement._2.upperBound.value + "]", requirement._2.lowerBound.pos, Some(requirement._2.lowerBound.value.toString))
        }
        if (requirement._2.lowerBound.value < 0) {
          compilationErrors += CompilationError("Requirement [" + requirement._1.value + "] has negative lower bound", requirement._2.lowerBound.pos, Some(requirement._2.lowerBound.value.toString))
        }
        if (requirement._2.upperBound.value < 0) {
          compilationErrors += CompilationError("Requirement [" + requirement._1.value + "] has negative upper bound", requirement._2.upperBound.pos, Some(requirement._2.upperBound.value.toString))
        }
        compilationErrors.toList
      }
    }.getOrElse(List.empty).toList
  }

  def analyzeCapabilityDefinition(nodeType: NodeType, csarPath: List[Csar]) = {
    nodeType.capabilities.map { capabilities =>
      capabilities.flatMap { capability =>
        val compilationErrors = ListBuffer[CompilationError]()
        if (capability._2.capabilityType.isEmpty) {
          compilationErrors += CompilationError("Capability [" + capability._1.value + "] has undefined type for capability", capability._1.pos, Some(capability._1.value))
        } else {
          val definedCapabilityType = capability._2.capabilityType.get
          val capabilityFound = TypeLoader.loadCapabilityType(definedCapabilityType.value, csarPath)
          if (capabilityFound.isEmpty) {
            compilationErrors += CompilationError("Capability [" + capability._1.value + "] refers to unknown capability [" + definedCapabilityType.value + "]", definedCapabilityType.pos, Some(definedCapabilityType.value))
          }
        }
        if (capability._2.upperBound.value < 0) {
          compilationErrors += CompilationError("Capability [" + capability._1.value + "] has negative upper bound", capability._2.upperBound.pos, Some(capability._2.upperBound.value.toString))
        }
        compilationErrors.toList
      }
    }.getOrElse(List.empty).toList
  }

  def validateNumberOfArguments(function: Function) = {
    val compilationErrors = ListBuffer[CompilationError]()
    // Validate number of arguments
    function.function.value match {
      case Tokens.get_input_token =>
        if (function.paths.size != 1) {
          CompilationError(s"Function [${function.function.value}] expects one and only one argument for the input name", function.pos, Some(function.function.value))
        }
      case Tokens.get_property_token | Tokens.get_attribute_token =>
        if (function.paths.size != 2) {
          CompilationError(s"Function [${function.function.value}] expects two arguments for the entity and the property / attribute name", function.pos, Some(function.function.value))
        }
      case Tokens.get_operation_output_token =>
        if (function.paths.size != 4) {
          CompilationError(s"Function [${function.function.value}] expects four arguments for the entity, the interface name, the operation name and the output name", function.pos, Some(function.function.value))
        }
      case _ =>
    }
    compilationErrors.toList
  }

  def analyzeFunction(runtimeType: RuntimeType, function: Function) = {
    val compilationErrors = ListBuffer[CompilationError]()
    compilationErrors ++= validateNumberOfArguments(function)
    // Validate entity
    function.function.value match {
      case Tokens.get_property_token | Tokens.get_attribute_token | Tokens.get_operation_output_token =>
        if (runtimeType.isInstanceOf[NodeType] && function.paths.head.value != "SELF" && function.paths.head.value != "HOST") {
          compilationErrors += CompilationError(s"Function [${function.function.value}] expect (SELF, HOST) as entity in a node context", function.paths.head.pos, Some(function.paths.head.value))
        }
        if (runtimeType.isInstanceOf[RelationshipType] && function.paths.head.value != "SOURCE" && function.paths.head.value != "TARGET" && function.paths.head.value != "SELF") {
          compilationErrors += CompilationError(s"Function [${function.function.value}] expect (SELF, SOURCE, TARGET) as entity in a relationship context", function.paths.head.pos, Some(function.paths.head.value))
        }
      case _ =>
    }
    compilationErrors.toList
  }

  def analyzeCompositeFunction(runtimeType: RuntimeType, function: CompositeFunction): List[CompilationError] = {
    function.members.flatMap {
      case f: Function => analyzeFunction(runtimeType, f)
      case c: CompositeFunction => analyzeCompositeFunction(runtimeType, c)
      case _ => List.empty
    }.toList
  }

  def analyzeOperation(runtimeType: RuntimeType, operationId: ParsedValue[String], operation: Operation, recipePath: Path, csarPath: List[Csar]): List[CompilationError] = {
    val compilationErrors = ListBuffer[CompilationError]()
    operation.implementation.map { implementation =>
      if (implementation.ref.isEmpty) {
        compilationErrors += CompilationError("Operation [" + operationId.value + "]'s implementation artifact's reference is missing", implementation.pos, None)
      } else if (!Files.isRegularFile(recipePath.resolve(implementation.ref.get.value))) {
        compilationErrors += CompilationError("Operation [" + operationId.value + "]'s implementation artifact [" + implementation.ref.get.value + "] is not found in recipe path [" + recipePath + "]", implementation.pos, Some(implementation.ref.get.value))
      } else if (implementation.typeName.nonEmpty && TypeLoader.loadArtifactType(implementation.typeName.get.value, csarPath).isEmpty) {
        compilationErrors += CompilationError(s"Operation [${operationId.value}]'s implementation artifact's type [${implementation.typeName.get.value}] is unknown", implementation.typeName.get.pos, Some(implementation.typeName.get.value))
      } else if (implementation.typeName.isEmpty && TypeLoader.loadArtifactByFileExtension(FileUtil.getFileExtension(implementation.ref.get.value), csarPath).isEmpty) {
        compilationErrors += CompilationError(s"Operation [${operationId.value}]'s implementation artifact's type [${implementation.ref.get.value}] cannot be deduced from its extension", implementation.ref.get.pos, Some(implementation.ref.get.value))
      }
    }
    operation.inputs.getOrElse(Map.empty).values.map {
      case function: Function => compilationErrors ++= analyzeFunction(runtimeType, function)
      case compositeFunction: CompositeFunction => compilationErrors ++= analyzeCompositeFunction(runtimeType, compositeFunction)
      case _ =>
    }
    compilationErrors.toList
  }

  def analyzeInterfaces(runtimeType: RuntimeType, recipePath: Path, csarPath: List[Csar]) = {
    runtimeType.interfaces.map { interfaces =>
      interfaces.values.flatMap { interface =>
        interface.operations.flatMap {
          case (operationId: ParsedValue[String], operation: Operation) => analyzeOperation(runtimeType, operationId, operation, recipePath, csarPath)
        }
      }
    }.getOrElse(List.empty).toList
  }

  def analyzeDeploymentArtifacts(runtimeType: RuntimeType, recipePath: Path) = {
    runtimeType.artifacts.getOrElse(Map.empty).flatMap {
      case (name, artifact) =>
        if (artifact.ref.isEmpty) {
          Some(CompilationError("Deployment artifact [" + name.value + "]'s reference is missing", name.pos, Some(name.value)))
        } else if (!Files.isRegularFile(recipePath.resolve(artifact.ref.get.value))) {
          Some(CompilationError("Deployment artifact [" + name.value + "]'s reference [" + artifact.ref.get.value + "] is not found in recipe path [" + recipePath + "]", artifact.pos, Some(artifact.ref.get.value)))
        } else {
          None
        }
    }.toList
  }

  def analyzeNodeType(nodeType: NodeType, recipePath: Path, csarPath: List[Csar]) = {
    val compilationErrors = ListBuffer[CompilationError]()
    compilationErrors ++= analyzeDerivedFrom(nodeType, csarPath, TypeLoader.loadNodeType)
    compilationErrors ++= analyzePropertyDefinitions(nodeType, csarPath)
    compilationErrors ++= analyzeAttributeDefinitions(nodeType, csarPath)
    compilationErrors ++= analyzeRequirementDefinition(nodeType, csarPath)
    compilationErrors ++= analyzeCapabilityDefinition(nodeType, csarPath)
    compilationErrors ++= analyzeInterfaces(nodeType, recipePath, csarPath)
    compilationErrors ++= analyzeDeploymentArtifacts(nodeType, recipePath)
    compilationErrors.toList
  }

  def analyzeCapabilityType(capabilityType: CapabilityType, csarPath: List[Csar]) = {
    val compilationErrors = ListBuffer[CompilationError]()
    compilationErrors ++= analyzeDerivedFrom(capabilityType, csarPath, TypeLoader.loadCapabilityType)
    compilationErrors ++= analyzePropertyDefinitions(capabilityType, csarPath)
    compilationErrors.toList
  }

  def analyzeRelationshipType(relationshipType: RelationshipType, recipePath: Path, csarPath: List[Csar]) = {
    val compilationErrors = ListBuffer[CompilationError]()
    compilationErrors ++= analyzeDerivedFrom(relationshipType, csarPath, TypeLoader.loadRelationshipType)
    compilationErrors ++= analyzePropertyDefinitions(relationshipType, csarPath)
    compilationErrors ++= analyzeInterfaces(relationshipType, recipePath, csarPath)
    compilationErrors ++= analyzeDeploymentArtifacts(relationshipType, recipePath)
    compilationErrors.toList
  }

  def analyzeArtifactType(artifactType: ArtifactType, csarPath: List[Csar]) = {
    analyzeDerivedFrom(artifactType.name, artifactType.derivedFrom, csarPath, TypeLoader.loadArtifactType).map(List(_)).getOrElse(List.empty)
  }

  def analyzeRequirement(topologyTemplate: TopologyTemplate,
                         sourceNodeName: String,
                         sourceNodeType: NodeType,
                         requirementsDefinitionsOpt: Option[Map[ParsedValue[String], RequirementDefinition]],
                         requirement: Requirement,
                         csarPath: List[Csar]): List[CompilationError] = {
    val compilationErrors = ListBuffer[CompilationError]()
    if (requirementsDefinitionsOpt.isEmpty) {
      compilationErrors += CompilationError(s"Requirement ${requirement.name.value} is not expected as not any is defined for this type", requirement.name.pos, Some(requirement.name.value))
    } else {
      val requirementsDefinitions = requirementsDefinitionsOpt.get.map { case (ParsedValue(key), value) => (key, value) }
      if (!requirementsDefinitions.contains(requirement.name.value)) {
        compilationErrors += CompilationError(s"Requirement ${requirement.name.value} is not defined in the corresponding type, expecting one of [${requirementsDefinitions.keys.mkString(", ")}]", requirement.name.pos, Some(requirement.name.value))
      } else {
        val requirementDefinition = requirementsDefinitions(requirement.name.value)
        if (requirement.targetNode.isDefined) {
          val targetNodeName = requirement.targetNode.get
          val nodeTemplates = topologyTemplate.nodeTemplates.getOrElse(Map.empty)
          if (!nodeTemplates.contains(targetNodeName)) {
            compilationErrors += CompilationError(s"Requirement [${requirement.name.value}] refer to a non existing node [${targetNodeName.value}]", targetNodeName.pos, Some(targetNodeName.value))
          } else {
            val targetNode = nodeTemplates(targetNodeName)
            if (targetNode.typeName.isDefined) {
              val targetType = TypeLoader.loadNodeType(targetNode.typeName.get.value, csarPath)
              if (targetType.isDefined) {
                if (requirement.targetCapability.isDefined && TypeLoader.loadCapabilityType(requirement.targetCapability.get.value, csarPath).isEmpty) {
                  compilationErrors += CompilationError(s"Requirement [${requirement.name.value}] refer to a non existing capability [${requirement.targetCapability.get.value}]", requirement.targetCapability.get.pos, Some(requirement.targetCapability.get.value))
                } else {
                  val relationshipType = TypeLoader.loadRelationshipWithHierarchy(requirement, requirementDefinition, sourceNodeType.name.value, targetType.get.name.value, csarPath)
                  if (relationshipType.isEmpty) {
                    compilationErrors += CompilationError(s"Requirement [${requirement.name.value}] cannot be satisfied, no relationship can be created from node [$sourceNodeName] to node [${targetNodeName.value}]", requirement.pos, Some(requirement.name.value))
                  } else {
                    compilationErrors ++= analyzeProperties(topologyTemplate, requirement.name, requirement.properties, relationshipType.get.properties, csarPath)
                  }
                }
              }
            }
          }
        } else {
          compilationErrors += CompilationError(s"Requirement [${requirement.name.value}] do not have any defined target", requirement.pos, Some(requirement.name.value))
        }
      }
    }
    compilationErrors.toList
  }

  def analyzeProperties(topologyTemplate: TopologyTemplate,
                        pos: Positional,
                        properties: Option[Map[ParsedValue[String], EvaluableFieldValue]],
                        propertiesDefinitions: Option[Map[ParsedValue[String], FieldValue]],
                        csarPath: List[Csar]) = {
    val compilationErrors = ListBuffer[CompilationError]()
    compilationErrors ++= properties.map { properties =>
      val propertiesAsComplex = ComplexValue(properties)
      propertiesAsComplex.setPos(pos.pos)
      analyzeDataTypeValue(propertiesAsComplex, propertiesDefinitions, csarPath)
    }.getOrElse(List.empty)

    compilationErrors ++= properties.map {
      _.values.filter(prop => prop.isInstanceOf[Function] || prop.isInstanceOf[CompositeFunction]).flatMap {
        case function: Function => analyzeFunction(topologyTemplate, function)
        case compositeFunction: CompositeFunction => analyzeCompositeFunction(topologyTemplate, compositeFunction)
        case _ => List.empty
      }
    }.getOrElse(List.empty)
    compilationErrors.toList
  }

  def analyzeCapability(topologyTemplate: TopologyTemplate,
                        capabilitiesDefinitionsOpt: Option[Map[ParsedValue[String], CapabilityDefinition]],
                        capability: Capability,
                        csarPath: List[Csar]) = {
    val compilationErrors = ListBuffer[CompilationError]()
    if (capabilitiesDefinitionsOpt.isEmpty) {
      compilationErrors += CompilationError(s"Capability ${capability.name.value} is not expected as not any is defined for this type", capability.name.pos, Some(capability.name.value))
    } else {
      val capabilitiesDefinitions = capabilitiesDefinitionsOpt.get.map { case (ParsedValue(key), value) => (key, value) }
      if (!capabilitiesDefinitions.contains(capability.name.value)) {
        compilationErrors += CompilationError(s"Capability ${capability.name.value} is not defined in the corresponding type, expecting one of [${capabilitiesDefinitions.keys.mkString(", ")}]", capability.name.pos, Some(capability.name.value))
      } else {
        // The goal here is to only validate node template, all types are already validated by other methods
        // Every errors concerning node type and capability type will just be ignored here
        val capabilityTypeNameOpt = capabilitiesDefinitions(capability.name.value).capabilityType
        if (capabilityTypeNameOpt.nonEmpty) {
          val capabilityTypeOpt = TypeLoader.loadCapabilityTypeWithHierarchy(capabilityTypeNameOpt.get.value, csarPath)
          if (capabilityTypeOpt.nonEmpty) {
            compilationErrors ++= analyzeProperties(topologyTemplate, capability.name, Some(capability.properties), capabilityTypeOpt.get.properties, csarPath)
          }
        }
      }
    }
    compilationErrors.toList
  }

  def analyzeNodeTemplate(topologyTemplate: TopologyTemplate, nodeTemplate: NodeTemplate, csarPath: List[Csar]) = {
    val compilationErrors = ListBuffer[CompilationError]()
    if (nodeTemplate.typeName.isDefined) {
      val typeName = nodeTemplate.typeName.get
      val typeFound = TypeLoader.loadNodeTypeWithHierarchy(typeName.value, csarPath)
      if (typeFound.isEmpty) {
        compilationErrors += CompilationError(s"Node template [${nodeTemplate.name.value}] is of unknown type [${typeName.value}]", typeName.pos, Some(typeName.value))
      } else {
        compilationErrors ++= analyzeProperties(topologyTemplate, nodeTemplate.name, nodeTemplate.properties, typeFound.get.properties, csarPath)
        compilationErrors ++= nodeTemplate.capabilities.map { capabilities =>
          capabilities.flatMap {
            case (name, capability) => analyzeCapability(topologyTemplate, typeFound.get.capabilities, capability, csarPath)
          }
        }.getOrElse(List.empty)
        nodeTemplate.requirements.foreach { requirements =>
          requirements.foreach {
            case requirement => compilationErrors ++= analyzeRequirement(topologyTemplate, nodeTemplate.name.value, typeFound.get, typeFound.get.requirements, requirement, csarPath)
          }
        }
      }
    } else {
      compilationErrors += CompilationError(s"Type name is mandatory for node template [${nodeTemplate.name.value}]", nodeTemplate.name.pos, Some(nodeTemplate.name.value))
    }
    compilationErrors.toList
  }

  def analyzeFunction(topologyTemplate: TopologyTemplate, function: Function) = {
    val compilationErrors = ListBuffer[CompilationError]()
    compilationErrors ++= validateNumberOfArguments(function)
    function.function.value match {
      case Tokens.get_input_token =>
        if (!topologyTemplate.inputs.getOrElse(Map.empty).contains(function.paths.head)) {
          compilationErrors += CompilationError(s"Input [${function.paths.head.value}] is not defined for the topology", function.paths.head.pos, Some(function.paths.head.value))
        }
      case Tokens.get_property_token | Tokens.get_attribute_token | Tokens.get_operation_output_token =>
        if (!topologyTemplate.nodeTemplates.getOrElse(Map.empty).contains(function.paths.head)) {
          compilationErrors += CompilationError(s"Node [${function.paths.head.value}] is not defined for the topology", function.paths.head.pos, Some(function.paths.head.value))
        }
    }
    compilationErrors.toList
  }

  def analyzeCompositeFunction(topologyTemplate: TopologyTemplate, function: CompositeFunction): List[CompilationError] = {
    function.members.flatMap {
      case f: Function => analyzeFunction(topologyTemplate, f)
      case c: CompositeFunction => analyzeCompositeFunction(topologyTemplate, c)
      case _ => List.empty
    }.toList
  }

  def analyzeOutput(topologyTemplate: TopologyTemplate, output: Output) = {
    output.value.map {
      case function: Function => analyzeFunction(topologyTemplate, function)
      case compositeFunction: CompositeFunction => analyzeCompositeFunction(topologyTemplate, compositeFunction)
      case _ => List.empty
    }.getOrElse(List.empty)
  }

  def analyzeTopology(topologyTemplate: TopologyTemplate, csarPath: List[Csar]) = {
    val compilationErrors = ListBuffer[CompilationError]()
    compilationErrors ++= topologyTemplate.inputs.map(_.values.flatMap(analyzePropertyDefinition(_, csarPath)).toList).getOrElse(List.empty)
    compilationErrors ++= topologyTemplate.outputs.map(_.values.flatMap(analyzeOutput(topologyTemplate, _)).toList).getOrElse(List.empty)
    compilationErrors ++= topologyTemplate.nodeTemplates.map(nodeTemplates => nodeTemplates.values.flatMap(analyzeNodeTemplate(topologyTemplate, _, csarPath)).toList).getOrElse(List.empty)
    compilationErrors.toList
  }

  def analyzeTopologyBeforeDeployment(topologyTemplate: TopologyTemplate, csarPath: List[Csar]) = {
    val compilationErrors = ListBuffer[CompilationError]()
    topologyTemplate.nodeTemplates.getOrElse(Map.empty).values.foreach { nodeTemplate =>
      // Type not found should be detected long before reaching here
      TypeLoader.loadNodeType(nodeTemplate.typeName.get.value, csarPath).map { nodeType =>
        if (nodeType.isAbstract.value) compilationErrors += CompilationError(s"Type ${nodeTemplate.typeName.get.value} is abstract and cannot be used in a deployment topology", nodeTemplate.typeName.get.pos, nodeTemplate.typeName.map(_.value))
      }
    }
    compilationErrors.toList
  }

  def analyzeDefinition(definition: Definition, recipePath: Path, csarPath: List[Csar]) = {
    val compilationErrors = ListBuffer[CompilationError]()
    definition.nodeTypes.map { nodeTypes =>
      nodeTypes.values.map { nodeType =>
        compilationErrors ++= analyzeNodeType(nodeType, recipePath, csarPath)
      }
    }
    definition.capabilityTypes.map { capabilityTypes =>
      capabilityTypes.values.map { capabilityType =>
        compilationErrors ++= analyzeCapabilityType(capabilityType, csarPath)
      }
    }
    definition.relationshipTypes.map { relationshipTypes =>
      relationshipTypes.values.map { relationshipType =>
        compilationErrors ++= analyzeRelationshipType(relationshipType, recipePath, csarPath)
      }
    }
    definition.artifactTypes.map { artifactTypes =>
      artifactTypes.values.map { artifactType =>
        compilationErrors ++= analyzeArtifactType(artifactType, csarPath)
      }
    }
    definition.topologyTemplate.map { topologyTemplate =>
      compilationErrors ++= analyzeTopology(topologyTemplate, csarPath)
    }
    compilationErrors.toList
  }

  def analyze(csar: Csar, archivePath: Path, csarPath: List[Csar]) = {
    val csarPathWithSelf = csar :: csarPath
    csar.definitions.map {
      case (path, definition) => (path, analyzeDefinition(definition, archivePath, csarPathWithSelf))
    }.filter(_._2.nonEmpty)
  }
}
