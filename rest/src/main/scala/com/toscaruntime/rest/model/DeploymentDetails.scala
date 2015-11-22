package com.toscaruntime.rest.model

import play.api.libs.json.Json

case class Instance(id: String, state: String, attributes: Map[String, String])

object Instance {
  implicit val InstanceFormat = Json.format[Instance]
}

case class RelationshipInstance(sourceInstanceId: String, targetInstanceId: String, attributes: Map[String, String])

object RelationshipInstance {
  implicit val RelationshipInstanceFormat = Json.format[RelationshipInstance]
}

case class Node(id: String, properties: Map[String, String], instances: List[Instance])

object Node {
  implicit val NodeFormat = Json.format[Node]
}

case class RelationshipNode(sourceNodeId: String, targetNodeId: String, properties: Map[String, String], relationshipInstances: List[RelationshipInstance])

object RelationshipNode {
  implicit val RelationshipNodeFormat = Json.format[RelationshipNode]
}

case class DeploymentDetails(name: String, nodes: List[Node], relationships: List[RelationshipNode], outputs: Map[String, String])

object DeploymentDetails {

  implicit val DeploymentDetailsFormat = Json.format[DeploymentDetails]
}

case class DeploymentInfo(name: String, agentName:String, agentId: String, agentCreated: String, agentStatus: String, agentIP: String)

object DeploymentInfo {

  implicit val DeploymentInfoFormat = Json.format[DeploymentInfo]
}