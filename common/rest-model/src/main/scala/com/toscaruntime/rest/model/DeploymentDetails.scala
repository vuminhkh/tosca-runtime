package com.toscaruntime.rest.model

import play.api.libs.json.Json

import JSONMapStringAnyFormat._

trait AbstractInstance {
  val state: String
  val attributes: Map[String, Any]
}

case class Instance(id: String, state: String, attributes: Map[String, Any]) extends AbstractInstance

object Instance {
  implicit val InstanceFormat = Json.format[Instance]
}

case class RelationshipInstance(sourceInstanceId: String, targetInstanceId: String, state: String, attributes: Map[String, Any]) extends AbstractInstance

object RelationshipInstance {
  implicit val RelationshipInstanceFormat = Json.format[RelationshipInstance]
}

trait AbstractNode {
  val properties: Map[String, Any]
}

case class Node(id: String, properties: Map[String, Any], instances: List[Instance]) extends AbstractNode

object Node {
  implicit val NodeFormat = Json.format[Node]
}

case class RelationshipNode(sourceNodeId: String, targetNodeId: String, properties: Map[String, Any], relationshipInstances: List[RelationshipInstance]) extends AbstractNode

object RelationshipNode {
  implicit val RelationshipNodeFormat = Json.format[RelationshipNode]
}

case class DeploymentDetails(name: String, nodes: List[Node], relationships: List[RelationshipNode], outputs: Map[String, Any])

object DeploymentDetails {

  implicit val DeploymentDetailsFormat = Json.format[DeploymentDetails]
}

case class DeploymentInfo(name: String,
                          agentName: String,
                          agentId: String,
                          agentCreated: String,
                          agentStatus: String,
                          agentIPs: Map[String, String])

object DeploymentInfo {

  implicit val DeploymentInfoFormat = Json.format[DeploymentInfo]
}