package com.toscaruntime.compiler

import com.toscaruntime.compiler.tosca._

object TypeLoader {

  def isNodeInstanceOf(left: String, right: String, csarsPath: Seq[Csar]) = isInstanceOf(left, right, loadNodeTypes(csarsPath))

  def loadNodeType(typeName: String, csarsPath: Seq[Csar]) = loadNodeTypes(csarsPath).get(typeName)

  def loadNodeTypes(csarsPath: Seq[Csar]) = csarsPath.flatMap(_.definitions.values.flatMap(_.nodeTypes.getOrElse(Map.empty).map(entry => (entry._1.value, entry._2)))).toMap

  def isRelationshipInstanceOf(left: String, right: String, csarsPath: Seq[Csar]) = isInstanceOf(left, right, loadRelationshipTypes(csarsPath))

  def loadRelationshipType(typeName: String, csarsPath: Seq[Csar]): Option[RelationshipType] = loadRelationshipTypes(csarsPath).get(typeName)

  def loadRelationshipTypes(csarsPath: Seq[Csar]) = csarsPath.flatMap(_.definitions.values.flatMap(_.relationshipTypes.getOrElse(Map.empty).map(entry => (entry._1.value, entry._2)))).toMap

  def isCapabilityInstanceOf(left: String, right: String, csarsPath: Seq[Csar]) = isInstanceOf(left, right, loadCapabilityTypes(csarsPath))

  def loadCapabilityType(typeName: String, csarsPath: Seq[Csar]) = loadCapabilityTypes(csarsPath).get(typeName)

  def loadCapabilityTypes(csarsPath: Seq[Csar]) = csarsPath.flatMap(_.definitions.values.flatMap(_.capabilityTypes.getOrElse(Map.empty).map(entry => (entry._1.value, entry._2)))).toMap

  def loadArtifactType(typeName: String, csarsPath: Seq[Csar]) = loadArtifactTypes(csarsPath).get(typeName)

  def loadArtifactTypes(csarsPath: Seq[Csar]) = csarsPath.flatMap(_.definitions.values.flatMap(_.artifactTypes.getOrElse(Map.empty).map(entry => (entry._1.value, entry._2)))).toMap

  def loadDataType(typeName: String, csarsPath: Seq[Csar]) = loadDataTypes(csarsPath).get(typeName)

  def loadDataTypes(csarsPath: Seq[Csar]) = csarsPath.flatMap(_.definitions.values.flatMap(_.dataTypes.getOrElse(Map.empty).map(entry => (entry._1.value, entry._2)))).toMap

  def isInstanceOf[T <: Type](left: String, rights: Seq[String], allTypes: Map[String, T]): Boolean = {
    rights.foreach(right =>
      if (isInstanceOf(left, right, allTypes)) {
        return true
      }
    )
    false
  }

  def isInstanceOf[T <: Type](left: String, right: String, allTypes: Map[String, T]): Boolean = {
    var instanceType = left
    if (instanceType == right) {
      return true
    }
    if (!allTypes.contains(instanceType)) return false
    while (allTypes(instanceType).derivedFrom.isDefined) {
      instanceType = allTypes(instanceType).derivedFrom.get.value
      if (instanceType == right) {
        return true
      }
    }
    false
  }

  private def recursiveLoadTypeWithHierarchy[T <: Type](simpleType: T,
                                                        allTypes: Map[String, T],
                                                        mergeFunction: (T, T) => T,
                                                        csarsPath: Seq[Csar]): T = {
    if (simpleType.derivedFrom.isDefined) {
      mergeFunction(simpleType, recursiveLoadTypeWithHierarchy(allTypes(simpleType.derivedFrom.get.value), allTypes, mergeFunction, csarsPath))
    } else simpleType
  }

  private def mergeNodeType(left: NodeType, right: NodeType) = left.copy(
    artifacts = mergeMap(left.artifacts, right.artifacts),
    properties = mergeMap(left.properties, right.properties),
    attributes = mergeMap(left.attributes, right.attributes),
    capabilities = mergeMap(left.capabilities, right.capabilities),
    requirements = mergeMap(left.requirements, right.requirements),
    interfaces = mergeMap(left.interfaces, right.interfaces)
  )

  def loadNodeTypeWithHierarchy(typeName: String, csarsPath: Seq[Csar]) = {
    val allNodeTypes = loadNodeTypes(csarsPath)
    allNodeTypes.get(typeName).map(recursiveLoadTypeWithHierarchy(_, allNodeTypes, mergeNodeType, csarsPath))
  }

  private def mergeDataType(left: DataType, right: DataType) = left.copy(properties = mergeMap(left.properties, right.properties))

  def loadDataTypeWithHierarchy(typeName: String, csarsPath: Seq[Csar]) = {
    val allDataTypes = loadDataTypes(csarsPath)
    allDataTypes.get(typeName).map(recursiveLoadTypeWithHierarchy(_, allDataTypes, mergeDataType, csarsPath))
  }

  def mergeCapabilityType(left: CapabilityType, right: CapabilityType) = left.copy(properties = mergeMap(left.properties, right.properties))

  def loadCapabilityTypeWithHierarchy(typeName: String, csarsPath: Seq[Csar]) = {
    val allCapabilityTypes = loadCapabilityTypes(csarsPath)
    allCapabilityTypes.get(typeName).map(recursiveLoadTypeWithHierarchy(_, allCapabilityTypes, mergeCapabilityType, csarsPath))
  }

  def mergeRelationshipType(left: RelationshipType, right: RelationshipType) = left.copy(
    artifacts = mergeMap(left.artifacts, right.artifacts),
    properties = mergeMap(left.properties, right.properties),
    attributes = mergeMap(left.attributes, right.attributes),
    interfaces = mergeMap(left.interfaces, right.interfaces)
  )

  def loadRelationshipWithHierarchy(requirement: Requirement,
                                    requirementDefinition: RequirementDefinition,
                                    sourceTypeName: String,
                                    targetTypeName: String,
                                    csarPath: Seq[Csar]): Option[RelationshipType] = {
    val allRelationshipTypes = loadRelationshipTypes(csarPath)
    val relationshipType = loadRelationshipType(requirement, requirementDefinition, sourceTypeName, targetTypeName, csarPath)
    relationshipType.map(recursiveLoadTypeWithHierarchy(_, allRelationshipTypes, mergeRelationshipType, csarPath))
  }

  def loadRelationshipType(requirement: Requirement,
                           requirementDefinition: RequirementDefinition,
                           sourceTypeName: String,
                           targetTypeName: String,
                           csarPath: Seq[Csar]): Option[RelationshipType] = {
    val relationshipTypeNameOpt = requirement.relationshipType.orElse(requirementDefinition.relationshipType)
    relationshipTypeNameOpt.map {
      // First try to load relationship from node template's requirement
      relationshipTypeName => TypeLoader.loadRelationshipType(relationshipTypeName.value, csarPath)
    }.getOrElse {
      // The required capability could come from the node template's requirement or can come from its type
      val requiredCapability = requirement.targetCapability.map(_.value).getOrElse(requirementDefinition.capabilityType.get.value)
      // If not specifically defined in node template then dynamically resolve the relationship
      TypeLoader.loadRelationshipType(sourceTypeName, targetTypeName, requiredCapability, csarPath)
    }
  }

  def loadRelationshipType(source: String, target: String, capability: String, csarPath: Seq[Csar]): Option[RelationshipType] = {
    val allRelationshipTypes = loadRelationshipTypes(csarPath)
    val allCapabilityTypes = loadCapabilityTypes(csarPath)
    val allNodeTypes = loadNodeTypes(csarPath)
    allRelationshipTypes.values.find {
      relationshipType =>
        val validTargets = relationshipType.validTargets.map(_.map(_.value))
        val validSources = relationshipType.validSources.map(_.map(_.value))
        // A relationship is eligible only if it's not abstract and it has valid targets
        !relationshipType.isAbstract.value && validTargets.isDefined &&
          // A relationship is eligible if its valid target is defined and the capability or the target it-self is of those types
          (isInstanceOf(capability, validTargets.get, allCapabilityTypes) ||
            isInstanceOf(capability, validTargets.get, allNodeTypes) ||
            isInstanceOf(target, validTargets.get, allNodeTypes)) &&
          // Valid source must be empty or else it must be checked to be sure that the relationship is eligible
          (validSources.isEmpty || isInstanceOf(source, validSources.get, allNodeTypes))
    }
  }

  /**
    * Merge left map into right, so elements on the left will override
    *
    * @param left  the one to be merged
    * @param right the one that will receive the merge
    * @tparam T value type
    * @return merged map
    */
  private def mergeMap[T](left: Option[Map[ParsedValue[String], T]], right: Option[Map[ParsedValue[String], T]]) = {
    if (right.isDefined) {
      if (left.isDefined) Some(right.get ++ left.get) else right
    } else left
  }
}
