package com.mkv.tosca.compiler

import java.nio.file.{Files, Path}

import com.mkv.tosca.compiler.tosca._

import scala.collection.mutable.ListBuffer

object SemanticAnalyzer {

  def analyzeFieldDefinition(field: Field) = {
    val propertyType = field.valueType
    if (!Field.isTypeValid(propertyType.value)) {
      Some(CompilationError("Property type [" + propertyType.value + "] is not valid", propertyType.pos, propertyType.value))
    } else {
      field.default.flatMap { default =>
        try {
          Evaluator.eval[Any](default.value, propertyType.value)
          None
        } catch {
          case e: Exception => Some(CompilationError("Property's default value [" + default.value + "] is not valid for property type [" + propertyType.value + "]: " + e.getMessage, default.pos, default.value))
        }
      }
    }
  }

  def analyzePropertyConstraintReference(textReference: ParsedValue[String], propertyType: String) = {
    try {
      Evaluator.eval[Any](textReference.value, propertyType)
      None
    } catch {
      case e: Exception => Some(CompilationError("Property constraint's reference value [" + textReference.value + "] is not valid for property type [" + propertyType + "]: " + e.getMessage, textReference.pos, textReference.value))
    }
  }

  def analyzePropertyDefinition(propertyDefinition: PropertyDefinition) = {
    val fieldErrors = analyzeFieldDefinition(propertyDefinition)
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
          case "greater_than" | "greater_or_equal" | "less_than" | "less_or_equal" | "in_range" =>
            if (!Field.isTypeComparable(propertyType)) {
              Some(CompilationError("Property constraint's [" + constraint.operator.value + "] needs comparable type, but incompatible [" + propertyType + "] found", constraint.operator.pos, constraint.operator.value))
            } else {
              None
            }
          case "length" | "min_length" | "max_length" | "pattern" =>
            if (propertyType != Field.STRING) {
              Some(CompilationError("Property constraint's [" + constraint.operator.value + "] needs string type , but incompatible [" + propertyType + "] found", constraint.operator.pos, constraint.operator.value))
            } else {
              None
            }
          case _ => None
        }
      }
    }.getOrElse(List.empty)
    fieldErrors.map(_ :: constraintReferenceErrors).getOrElse(constraintReferenceErrors) ++ constraintTypeErrors
  }

  def analyzePropertyDefinitions(toscaType: Type) = {
    toscaType.properties.map(_.values.map(analyzePropertyDefinition).toList).getOrElse(List.empty).flatten
  }

  def analyzeAttributeDefinitions(nodeType: NodeType) = {
    nodeType.attributes.map(_.values.map(analyzeFieldDefinition).toList).getOrElse(List.empty).flatten
  }

  def analyzeDerivedFrom(typeName: ParsedValue[String], derivedFrom: Option[ParsedValue[String]], csarPath: List[Csar], typeLoader: (String, List[Csar]) => Option[_]): Option[CompilationError] = {
    derivedFrom.flatMap { parentTypeName =>
      val typeFound = typeLoader(parentTypeName.value, csarPath)
      if (!typeFound.isDefined) {
        Some(CompilationError("Type [" + typeName.value + "] derived from unknown type [" + parentTypeName.value + "]", parentTypeName.pos, parentTypeName.value))
      } else {
        None
      }
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
          compilationErrors += CompilationError("Requirement [" + requirement._1.value + "] has undefined type for capability", requirement._1.pos, requirement._1.value)
        } else {
          val definedCapabilityType = requirement._2.capabilityType.get
          val capabilityFound = TypeLoader.loadCapabilityType(definedCapabilityType.value, csarPath)
          if (!capabilityFound.isDefined) {
            compilationErrors += CompilationError("Requirement [" + requirement._1.value + "] refers to unknown capability " + definedCapabilityType, definedCapabilityType.pos, definedCapabilityType.value)
          }
        }
        if (requirement._2.lowerBound.value > requirement._2.upperBound.value) {
          compilationErrors += CompilationError("Requirement [" + requirement._1.value + "] has lower bound [" + requirement._2.lowerBound.value + "] greater than upper bound [" + requirement._2.upperBound.value + "]", requirement._2.lowerBound.pos, requirement._2.lowerBound.value.toString)
        }
        if (requirement._2.lowerBound.value < 0) {
          compilationErrors += CompilationError("Requirement [" + requirement._1.value + "] has negative lower bound", requirement._2.lowerBound.pos, requirement._2.lowerBound.value.toString)
        }
        if (requirement._2.upperBound.value < 0) {
          compilationErrors += CompilationError("Requirement [" + requirement._1.value + "] has negative upper bound", requirement._2.upperBound.pos, requirement._2.upperBound.value.toString)
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
          compilationErrors += CompilationError("Capability [" + capability._1.value + "] has undefined type for capability", capability._1.pos, capability._1.value)
        } else {
          val definedCapabilityType = capability._2.capabilityType.get
          val capabilityFound = TypeLoader.loadCapabilityType(definedCapabilityType.value, csarPath)
          if (!capabilityFound.isDefined) {
            compilationErrors += CompilationError("Capability [" + capability._1.value + "] refers to unknown capability [" + definedCapabilityType.value + "]", definedCapabilityType.pos, definedCapabilityType.value)
          }
        }
        if (capability._2.upperBound.value < 0) {
          compilationErrors += CompilationError("Capability [" + capability._1.value + "] has negative upper bound", capability._2.upperBound.pos, capability._2.upperBound.value.toString)
        }
        compilationErrors.toList
      }
    }.getOrElse(List.empty).toList
  }

  def analyzeOperation(operationId: ParsedValue[String], operation: Operation, recipePath: Path) = {
    operation.implementation.flatMap { implementation =>
      if (!Files.isRegularFile(recipePath.resolve(implementation.value))) {
        Some(CompilationError("Operation [" + operationId.value + "]'s implementation artifact [" + implementation.value + "] is not found in recipe path [" + recipePath + "]", implementation.pos, implementation.value))
      } else {
        None
      }
    }
  }

  def analyzeInterfaces(runtimeType: RuntimeType, recipePath: Path) = {
    runtimeType.interfaces.map { interfaces =>
      interfaces.values.flatMap { interface =>
        interface.operations.flatMap {
          case (operationId: ParsedValue[String], operation: Operation) => analyzeOperation(operationId, operation, recipePath)
        }
      }
    }.getOrElse(List.empty).toList
  }

  def analyzeNodeType(nodeType: NodeType, recipePath: Path, csarPath: List[Csar]) = {
    val compilationErrors = ListBuffer[CompilationError]()
    compilationErrors ++= analyzeDerivedFrom(nodeType, csarPath, TypeLoader.loadNodeType)
    compilationErrors ++= analyzePropertyDefinitions(nodeType)
    compilationErrors ++= analyzeAttributeDefinitions(nodeType)
    compilationErrors ++= analyzeRequirementDefinition(nodeType, csarPath)
    compilationErrors ++= analyzeCapabilityDefinition(nodeType, csarPath)
    compilationErrors ++= analyzeInterfaces(nodeType, recipePath)
    compilationErrors.toList
  }

  def analyzeCapabilityType(capabilityType: CapabilityType, csarPath: List[Csar]) = {
    val compilationErrors = ListBuffer[CompilationError]()
    compilationErrors ++= analyzeDerivedFrom(capabilityType, csarPath, TypeLoader.loadCapabilityType)
    compilationErrors ++= analyzePropertyDefinitions(capabilityType)
    compilationErrors.toList
  }

  def analyzeRelationshipType(relationshipType: RelationshipType, recipePath: Path, csarPath: List[Csar]) = {
    val compilationErrors = ListBuffer[CompilationError]()
    compilationErrors ++= analyzeDerivedFrom(relationshipType, csarPath, TypeLoader.loadRelationshipType)
    compilationErrors ++= analyzePropertyDefinitions(relationshipType)
    compilationErrors ++= analyzeInterfaces(relationshipType, recipePath)
    compilationErrors.toList
  }

  def analyzeArtifactType(artifactType: ArtifactType, csarPath: List[Csar]) = {
    analyzeDerivedFrom(artifactType.name, artifactType.derivedFrom, csarPath, TypeLoader.loadArtifactType).map(List(_)).getOrElse(List.empty)
  }

  def analyzeProperties(values: Map[ParsedValue[String], Any], definitions: Map[ParsedValue[String], PropertyDefinition]) = {

  }

  def analyzeNodeTemplate(nodeTemplate: NodeTemplate, csarPath: List[Csar]) = {
    val compilationErrors = ListBuffer[CompilationError]()
    if (nodeTemplate.typeName.isDefined) {
      val typeName = nodeTemplate.typeName.get
      val typeFound = TypeLoader.loadNodeType(typeName.value, csarPath)
      if (!typeFound.isDefined) {
        compilationErrors += CompilationError("Node template [" + nodeTemplate.name.value + "] is of unknown type [" + typeName.value + "]", typeName.pos, typeName.value)
      }
    } else {
      compilationErrors += CompilationError("Type name is mandatory for node template [" + nodeTemplate.name.value + "]", nodeTemplate.name.pos, nodeTemplate.name.value)
    }
    compilationErrors.toList
  }

  def analyzeTopology(topologyTemplate: TopologyTemplate, csarPath: List[Csar]) = {
    val compilationErrors = ListBuffer[CompilationError]()
    compilationErrors ++= topologyTemplate.inputs.map(_.values.map(analyzePropertyDefinition).toList).getOrElse(List.empty).flatten
    compilationErrors ++= topologyTemplate.nodeTemplates.map(_.values.map(analyzeNodeTemplate(_, csarPath)).toList).getOrElse(List.empty).flatten
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

  def analyze(csar: Csar, csarPath: List[Csar]) = {
    val csarPathWithSelf = csar :: csarPath
    csar.definitions.map {
      case (path, definition) => (path, analyzeDefinition(definition, csar.path, csarPathWithSelf))
    }.filter(_._2.nonEmpty)
  }
}
