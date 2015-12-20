package com.toscaruntime.compiler

import com.toscaruntime.compiler.tosca.{Csar, ParsedValue, Type}

object TypeLoader {

  def isNodeInstanceOf(left: String, right: String, csarsPath: Seq[Csar]) = isInstanceOf(left, right, loadNodeTypes(csarsPath))

  def loadNodeType(typeName: String, csarsPath: Seq[Csar]) = loadNodeTypes(csarsPath).get(typeName)

  def loadPolymorphismResolvedNodeType(typeName: String, csarsPath: Seq[Csar]) = {
    val allNodeTypes = loadNodeTypes(csarsPath)
    allNodeTypes.get(typeName).map {
      nodeType =>
        var mergedArtifacts = nodeType.artifacts
        var mergedProperties = nodeType.properties
        var mergedAttributes = nodeType.attributes
        var mergedCapabilities = nodeType.capabilities
        var mergedRequirements = nodeType.requirements
        var mergedInterfaces = nodeType.interfaces
        var currentTypeName = typeName
        var currentType = nodeType
        while (currentType.derivedFrom.isDefined) {
          currentTypeName = allNodeTypes(currentTypeName).derivedFrom.get.value
          currentType = allNodeTypes(currentTypeName)
          mergedArtifacts = merge(mergedArtifacts, currentType.artifacts)
          mergedProperties = merge(mergedProperties, currentType.properties)
          mergedAttributes = merge(mergedAttributes, currentType.attributes)
          mergedCapabilities = merge(mergedCapabilities, currentType.capabilities)
          mergedRequirements = merge(mergedRequirements, currentType.requirements)
          mergedInterfaces = merge(mergedInterfaces, currentType.interfaces)
        }
        nodeType.copy(artifacts = mergedArtifacts, properties = mergedProperties, attributes = mergedAttributes, capabilities = mergedCapabilities, requirements = mergedRequirements, interfaces = mergedInterfaces)
    }
  }

  def merge[T](current: Option[Map[ParsedValue[String], T]], toBeMerged: Option[Map[ParsedValue[String], T]]) = {
    if (toBeMerged.isDefined) {
      if (current.isDefined) {
        Some(toBeMerged.get ++ current.get)
      } else {
        toBeMerged
      }
    } else {
      current
    }
  }

  def loadNodeTypes(csarsPath: Seq[Csar]) = csarsPath.flatMap(_.definitions.values.flatMap(_.nodeTypes.getOrElse(Map.empty).map(entry => (entry._1.value, entry._2)))).toMap

  def isRelationshipInstanceOf(left: String, right: String, csarsPath: Seq[Csar]) = isInstanceOf(left, right, loadRelationshipTypes(csarsPath))

  def loadRelationshipType(typeName: String, csarsPath: Seq[Csar]) = loadRelationshipTypes(csarsPath).get(typeName)

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
    while (allTypes(instanceType).derivedFrom.isDefined) {
      instanceType = allTypes(instanceType).derivedFrom.get.value
      if (instanceType == right) {
        return true
      }
    }
    false
  }

  def loadRelationshipType(source: String, target: String, capability: String, csarsPath: Seq[Csar]) = {
    val allRelationshipTypes = loadRelationshipTypes(csarsPath)
    val allCapabilityTypes = loadCapabilityTypes(csarsPath)
    val allNodeTypes = loadNodeTypes(csarsPath)
    allRelationshipTypes.values.find {
      relationshipType =>
        val validTargets = relationshipType.validTargets.map(_.map(_.value))
        val validSources = relationshipType.validSources.map(_.map(_.value))
        !relationshipType.isAbstract.value &&
          (validTargets.isDefined && (isInstanceOf(capability, validTargets.get, allCapabilityTypes) || isInstanceOf(target, validTargets.get, allNodeTypes))) &&
          (validSources.isEmpty || isInstanceOf(source, validSources.get, allNodeTypes))
    }
  }
}
