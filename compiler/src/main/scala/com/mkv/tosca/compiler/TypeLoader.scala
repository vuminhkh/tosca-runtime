package com.mkv.tosca.compiler

import com.mkv.tosca.compiler.tosca.{ParsedValue, Csar}

object TypeLoader {

  def loadType[T](typeName: String, csarsPath: List[Csar], factory: Csar => Map[ParsedValue[String], T]) = {
    csarsPath.find(factory(_).contains(ParsedValue(typeName))).flatMap(factory(_).get(ParsedValue(typeName)))
  }

  def loadNodeType(typeName: String, csarPath: List[Csar]) = loadType(typeName, csarPath, _.definitions.values.flatMap(_.nodeTypes.getOrElse(Map.empty)).toMap)

  def loadRelationshipType(typeName: String, csarPath: List[Csar]) = loadType(typeName, csarPath, _.definitions.values.flatMap(_.relationshipTypes.getOrElse(Map.empty)).toMap)

  def loadCapabilityType(typeName: String, csarPath: List[Csar]) = loadType(typeName, csarPath, _.definitions.values.flatMap(_.capabilityTypes.getOrElse(Map.empty)).toMap)

  def loadArtifactType(typeName: String, csarPath: List[Csar]) = loadType(typeName, csarPath, _.definitions.values.flatMap(_.artifactTypes.getOrElse(Map.empty)).toMap)
}
