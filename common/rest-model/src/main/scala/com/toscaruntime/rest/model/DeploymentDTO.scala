package com.toscaruntime.rest.model

import org.joda.time.DateTime
import play.api.libs.json.Json

import JSONMapStringAnyFormat._

trait AbstractInstanceDTO {
  val state: String
  val attributes: Map[String, Any]
}

case class InstanceDTO(id: String, state: String, attributes: Map[String, Any]) extends AbstractInstanceDTO

object InstanceDTO {
  implicit val InstanceFormat = Json.format[InstanceDTO]
}

case class RelationshipInstanceDTO(sourceInstanceId: String, targetInstanceId: String, state: String, attributes: Map[String, Any]) extends AbstractInstanceDTO

object RelationshipInstanceDTO {
  implicit val RelationshipInstanceFormat = Json.format[RelationshipInstanceDTO]
}

trait AbstractNodeDTO {
  val properties: Map[String, Any]
}

case class NodeDTO(id: String, properties: Map[String, Any], instances: List[InstanceDTO]) extends AbstractNodeDTO

object NodeDTO {
  implicit val NodeFormat = Json.format[NodeDTO]
}

case class RelationshipNodeDTO(sourceNodeId: String, targetNodeId: String, relationshipType: String, properties: Map[String, Any], relationshipInstances: List[RelationshipInstanceDTO]) extends AbstractNodeDTO

object RelationshipNodeDTO {
  implicit val RelationshipNodeFormat = Json.format[RelationshipNodeDTO]
}

case class ExecutionDTO(id: String, workflowId: String, startTime: DateTime, endTime: Option[DateTime], error: Option[String], status: String, inputs: Map[String, Any])

object ExecutionDTO {
  implicit val ExecutionDTOFormat = Json.format[ExecutionDTO]
}

case class DeploymentDTO(name: String,
                         nodes: List[NodeDTO],
                         relationships: List[RelationshipNodeDTO],
                         outputs: Map[String, Any],
                         executions: List[ExecutionDTO])

object DeploymentDTO {

  implicit val DeploymentDTOFormat = Json.format[DeploymentDTO]
}

case class DeploymentInfoDTO(name: String,
                             agentName: String,
                             agentId: String,
                             agentCreated: String,
                             agentStatus: String,
                             agentIPs: Map[String, String])

object DeploymentInfoDTO {

  implicit val DeploymentInfoDTOFormat = Json.format[DeploymentInfoDTO]
}