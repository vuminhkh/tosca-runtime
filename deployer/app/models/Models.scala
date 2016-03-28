package models

import java.sql.Timestamp

case class NodeEntity(id: String, instancesCount: Int)

case class InstanceEntity(id: String, nodeId: String, state: String)

case class AttributeEntity(instanceId: String, key: String, value: String)

case class OperationEntity(instanceId: String, interfaceName: String, operationName: String)

case class TaskEntity(executionId: String, taskId: String, status: String, startTime: Option[Timestamp], endTime: Option[Timestamp], error: Option[String])

case class NodeTaskEntity(executionId: String, instanceId: String, interfaceName: String, operationName: String, status: String, startTime: Option[Timestamp], endTime: Option[Timestamp], error: Option[String])

case class RelationshipTaskEntity(executionId: String, sourceInstanceId: String, targetInstanceId: String, relationshipType: String, interfaceName: String, operationName: String, status: String, startTime: Option[Timestamp], endTime: Option[Timestamp], error: Option[String])

case class OperationOutputEntity(instanceId: String, interfaceName: String, operationName: String, key: String, value: String)

case class ExecutionEntity(id: String, workflowId: String, startTime: Timestamp, endTime: Option[Timestamp], error: Option[String], status: String)

case class ExecutionInputEntity(executionId: String, key: String, value: String)

case class RelationshipEntity(sourceId: String, targetId: String, relationshipType: String)

case class RelationshipInstanceEntity(sourceInstanceId: String, targetInstanceId: String, sourceNodeId: String, targetNodeId: String, relationshipType: String, state: String)

case class RelationshipAttributeEntity(sourceInstanceId: String, targetInstanceId: String, relationshipType: String, key: String, value: String)

case class RelationshipOperationEntity(sourceInstanceId: String, targetInstanceId: String, relationshipType: String, interfaceName: String, operationName: String)

case class RelationshipOperationOutputEntity(sourceInstanceId: String, targetInstanceId: String, relationshipType: String, interfaceName: String, operationName: String, key: String, value: String)
