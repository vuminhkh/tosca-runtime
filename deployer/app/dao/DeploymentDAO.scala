package dao

import java.sql.Timestamp
import java.util
import java.util.UUID
import javax.inject.{Inject, Singleton}

import com.toscaruntime.constant.ExecutionConstant._
import com.toscaruntime.deployment._
import com.toscaruntime.util.JavaScalaConversionUtil
import models._
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.collection.JavaConverters._
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

@Singleton
class DeploymentDAO @Inject()(schema: Schema,
                              nodeDAO: NodeDAO,
                              instanceDAO: InstanceDAO,
                              attributeDAO: AttributeDAO,
                              outputDAO: OperationOutputDAO,
                              relationshipDAO: RelationshipDAO,
                              relationshipInstanceDAO: RelationshipInstanceDAO,
                              relationshipAttributeDAO: RelationshipAttributeDAO,
                              relationshipOutputDAO: RelationshipOutputDAO,
                              executionDAO: ExecutionDAO,
                              executionInputDAO: ExecutionInputDAO,
                              operationDAO: OperationDAO,
                              relationshipOperationDAO: RelationshipOperationDAO,
                              nodeTaskDAO: NodeTaskDAO,
                              relationshipTaskDAO: RelationshipTaskDAO,
                              taskDAO: TaskDAO) extends DeploymentPersister {

  private val forever = 365 days

  def createSchema() = schema.createSchema()

  def dropSchema() = schema.dropSchema()

  def isSchemaCreated = Await.result(schema.isSchemaCreated, forever)

  def listNodes() = nodeDAO.all()

  def countNodes() = nodeDAO.count()

  def insertNodeIfNotExist(id: String, instancesCount: Int) = nodeDAO.insertIfNotExist(NodeEntity(id, instancesCount))

  def getNodeInstancesCount(id: String) = nodeDAO.getNodeInstancesCount(id)

  def listInstances() = instanceDAO.all()

  def countInstances() = instanceDAO.count()

  def insertInstanceIfNotExist(id: String, nodeId: String, state: String) = instanceDAO.insertIfNotExist(InstanceEntity(id, nodeId, state))

  def deleteInstance(id: String) = instanceDAO.delete(id)

  def saveNodeInstancesCount(id: String, newInstancesCount: Int) = nodeDAO.saveInstancesCount(id, newInstancesCount)

  def saveInstanceState(id: String, newState: String) = instanceDAO.saveState(id, newState)

  def getInstanceState(id: String) = instanceDAO.getState(id)

  def saveInstanceAttribute(instanceId: String, key: String, value: String) = attributeDAO.save(AttributeEntity(instanceId, key, value))

  def deleteInstanceAttribute(instanceId: String, key: String) = attributeDAO.delete(instanceId, key)

  def saveOutput(instanceId: String, interfaceName: String, operationName: String, key: String, value: String) =
    outputDAO.save(OperationOutputEntity(instanceId, interfaceName, operationName, key, value))

  def saveAllOutputs(instanceId: String, interfaceName: String, operationName: String, outputs: Map[String, String]) = {
    val operation = OperationEntity(instanceId, interfaceName, operationName)
    operationDAO.save(operation).flatMap { _ =>
      outputDAO.saveAll(instanceId, interfaceName, operationName, outputs)
    }
  }

  def getOutputs(instanceId: String, interfaceName: String, operationName: String) =
    outputDAO.get(instanceId, interfaceName, operationName).map(_.map {
      case OperationOutputEntity(_, _, _, key: String, value: String) => (key, value)
    }.toMap)

  def getOutputInterfaces(instanceId: String) = outputDAO.getInterfaces(instanceId)

  def getOutputOperations(instanceId: String, interfaceName: String) = outputDAO.getOperations(instanceId, interfaceName)

  def getAttributes(instanceId: String) = {
    attributeDAO.get(instanceId).map(_.map {
      case AttributeEntity(_, key: String, value: String) => (key, value)
    }.toMap)
  }

  def listRelationships() = relationshipDAO.all()

  def insertRelationshipIfNotExist(sourceId: String, targetId: String, relationshipType: String) = relationshipDAO.insertIfNotExist(RelationshipEntity(sourceId, targetId, relationshipType))

  def insertRelationshipInstanceIfNotExist(sourceInstanceId: String, targetInstanceId: String, sourceNodeId: String, targetNodeId: String, relationshipType: String, state: String) =
    relationshipInstanceDAO.insertIfNotExist(RelationshipInstanceEntity(sourceInstanceId, targetInstanceId, sourceNodeId, targetNodeId, relationshipType, state))

  def deleteRelationshipInstance(sourceInstanceId: String, targetInstanceId: String, relationshipType: String) = relationshipInstanceDAO.delete(sourceInstanceId, targetInstanceId, relationshipType)

  def listRelationshipInstances() = relationshipInstanceDAO.all()

  def saveRelationshipInstanceState(sourceInstanceId: String, targetInstanceId: String, relationshipType: String, newState: String) = relationshipInstanceDAO.saveState(sourceInstanceId, targetInstanceId, relationshipType, newState)

  def saveRelationshipAttribute(sourceInstanceId: String, targetInstanceId: String, relationshipType: String, key: String, value: String) =
    relationshipAttributeDAO.save(RelationshipAttributeEntity(sourceInstanceId, targetInstanceId, relationshipType, key, value))

  def getRelationshipAttributes(sourceInstanceId: String, targetInstanceId: String, relationshipType: String) =
    relationshipAttributeDAO.get(sourceInstanceId, targetInstanceId, relationshipType).map(_.map {
      case RelationshipAttributeEntity(_, _, _, key: String, value: String) => (key, value)
    }.toMap)

  def getRelationshipState(sourceInstanceId: String, targetInstanceId: String, relationshipType: String) = relationshipInstanceDAO.getState(sourceInstanceId, targetInstanceId, relationshipType)

  def saveRelationshipOutput(sourceInstanceId: String, targetInstanceId: String, relationshipType: String, interfaceName: String, operationName: String, key: String, value: String) =
    relationshipOutputDAO.save(RelationshipOperationOutputEntity(sourceInstanceId, targetInstanceId, relationshipType, interfaceName, operationName, key, value))

  def saveRelationshipOperation(sourceInstanceId: String, targetInstanceId: String, relationshipType: String, interfaceName: String, operationName: String) = {
    val relationshipOperationEntity = RelationshipOperationEntity(sourceInstanceId, targetInstanceId, relationshipType, interfaceName, operationName)
    relationshipOperationDAO.save(relationshipOperationEntity)
  }

  def saveAllRelationshipOutputs(sourceInstanceId: String, targetInstanceId: String, relationshipType: String, interfaceName: String, operationName: String, outputs: Map[String, String]) = {
    val operation = RelationshipOperationEntity(sourceInstanceId, targetInstanceId, relationshipType, interfaceName, operationName)
    relationshipOperationDAO.save(operation).flatMap { _ =>
      relationshipOutputDAO.saveAll(sourceInstanceId, targetInstanceId, relationshipType, interfaceName, operationName, outputs)
    }
  }

  def getRelationshipOutputs(sourceInstanceId: String, targetInstanceId: String, relationshipType: String, interfaceName: String, operationName: String) =
    relationshipOutputDAO.get(sourceInstanceId, targetInstanceId, relationshipType).map(_.map {
      case RelationshipOperationOutputEntity(_, _, _, _, _, key: String, value: String) => (key, value)
    }.toMap)

  def getRelationshipOutputInterfaces(sourceInstanceId: String, targetInstanceId: String, relationshipType: String) = relationshipOutputDAO.getInterfaces(sourceInstanceId, targetInstanceId, relationshipType)

  def getRelationshipOutputOperations(sourceInstanceId: String, targetInstanceId: String, relationshipType: String, interfaceName: String) = relationshipOutputDAO.getOperations(sourceInstanceId, targetInstanceId, relationshipType, interfaceName)

  def listExecutions() = executionDAO.all()

  def getExecutionInputs(executionId: String) = executionInputDAO.get(executionId)

  def insertExecutionInputs(inputs: Map[String, Any]) = getRunningExecution.map {
    case Some(executionEntity) => executionInputDAO.insert(executionEntity.id, inputs)
  }

  def getExecution(executionId: String) = executionDAO.get(executionId)

  def getRunningExecution = executionDAO.getRunningExecution

  def startExecution(workflowId: String, inputs: Map[String, Any] = Map.empty) = {
    val uuid = UUID.randomUUID().toString
    executionDAO.insert(ExecutionEntity(uuid, workflowId, new Timestamp(System.currentTimeMillis()), None, None, RUNNING))
      .map { _ =>
        executionInputDAO.insert(uuid, inputs)
        uuid
      }
  }

  def stopExecution(error: Option[String]) = executionDAO.stop(error)

  def finishRunningExecution() = executionDAO.finish(SUCCESS, None)

  def resumeRunningExecution() = executionDAO.resume()

  def failRunningExecution(error: String) = executionDAO.finish(FAILURE, Some(error))

  def cancelRunningExecution() = executionDAO.finish(CANCELED, None)

  def saveOperation(instanceId: String, interfaceName: String, operationName: String) = {
    operationDAO.save(OperationEntity(instanceId, interfaceName, operationName))
  }

  def insertNewNodeTask(instanceId: String, interfaceName: String, operationName: String): Future[Int] = {
    val operation = OperationEntity(instanceId, interfaceName, operationName)
    operationDAO.save(operation).flatMap { _ =>
      nodeTaskDAO.insertNewTask(operation)
    }
  }

  def startNodeTask(instanceId: String, interfaceName: String, operationName: String) = {
    nodeTaskDAO.startTask(OperationEntity(instanceId, interfaceName, operationName))
  }

  def finishNodeTask(instanceId: String, interfaceName: String, operationName: String) = {
    nodeTaskDAO.finishTask(OperationEntity(instanceId, interfaceName, operationName))
  }

  def stopNodeTask(instanceId: String, interfaceName: String, operationName: String, error: String) = {
    nodeTaskDAO.stopTask(OperationEntity(instanceId, interfaceName, operationName), error)
  }

  def insertNewTask(taskId: String): Future[Int] = taskDAO.insertNewTask(taskId)

  def startTask(taskId: String) = taskDAO.startTask(taskId)

  def finishTask(taskId: String) = taskDAO.finishTask(taskId)

  def stopTask(taskId: String, error: String) = taskDAO.stopTask(taskId, error)

  def insertNewRelationshipTask(sourceInstanceId: String, targetInstanceId: String, relationshipType: String, interfaceName: String, operationName: String) = {
    val operation = RelationshipOperationEntity(sourceInstanceId, targetInstanceId, relationshipType, interfaceName, operationName)
    relationshipOperationDAO.save(operation).map { _ =>
      relationshipTaskDAO.insertNewTask(operation)
    }
  }

  def startRelationshipTask(sourceInstanceId: String, targetInstanceId: String, relationshipType: String, interfaceName: String, operationName: String) = {
    relationshipTaskDAO.startTask(RelationshipOperationEntity(sourceInstanceId, targetInstanceId, relationshipType, interfaceName, operationName))
  }

  def finishRelationshipTask(sourceInstanceId: String, targetInstanceId: String, relationshipType: String, interfaceName: String, operationName: String) = {
    relationshipTaskDAO.finishTask(RelationshipOperationEntity(sourceInstanceId, targetInstanceId, relationshipType, interfaceName, operationName))
  }

  def stopRelationshipTask(sourceInstanceId: String, targetInstanceId: String, relationshipType: String, interfaceName: String, operationName: String, error: String) = {
    relationshipTaskDAO.stopTask(RelationshipOperationEntity(sourceInstanceId, targetInstanceId, relationshipType, interfaceName, operationName), error)
  }

  def getNodeTasks = nodeTaskDAO.getRunningExecutionTasks

  def getRelationshipTasks = relationshipTaskDAO.getRunningExecutionTasks

  def getTasks = taskDAO.getRunningExecutionTasks

  override def syncInsertNodeIfNotExist(id: String, instancesCount: Int): Unit = Await.result(insertNodeIfNotExist(id, instancesCount), forever)

  override def syncInsertInstanceIfNotExist(id: String, nodeId: String, state: String): Unit = Await.result(insertInstanceIfNotExist(id, nodeId, state), forever)

  override def syncDeleteInstance(id: String): Unit = Await.result(deleteInstance(id), forever)

  override def syncSaveInstanceAttribute(instanceId: String, key: String, value: String): Unit = Await.result(saveInstanceAttribute(instanceId, key, value), forever)

  override def syncSaveInstanceOutputs(instanceId: String, interfaceName: String, operationName: String, outputs: java.util.Map[String, String]): Unit = Await.result(saveAllOutputs(instanceId, interfaceName, operationName, outputs.asScala.toMap), forever)

  override def syncSaveNodeInstancesCount(nodeId: String, newInstancesCount: Int): Unit = Await.result(saveNodeInstancesCount(nodeId, newInstancesCount), forever)

  override def syncSaveInstanceState(id: String, newState: String): Unit = Await.result(saveInstanceState(id, newState), forever)

  override def syncInsertRelationshipIfNotExist(sourceId: String, targetId: String, relationshipType: String): Unit = Await.result(insertRelationshipIfNotExist(sourceId, targetId, relationshipType), forever)

  override def syncInsertRelationshipInstanceIfNotExist(sourceInstanceId: String, targetInstanceId: String, sourceNodeId: String, targetNodeId: String, relationshipType: String, state: String): Unit = Await.result(insertRelationshipInstanceIfNotExist(sourceInstanceId, targetInstanceId, sourceNodeId, targetNodeId, relationshipType, state), forever)

  override def syncDeleteRelationshipInstance(sourceInstanceId: String, targetInstanceId: String, relationshipType: String): Unit = Await.result(deleteRelationshipInstance(sourceInstanceId, targetInstanceId, relationshipType), forever)

  override def syncSaveRelationshipAttribute(sourceInstanceId: String, targetInstanceId: String, relationshipType: String, key: String, value: String): Unit = Await.result(saveRelationshipAttribute(sourceInstanceId, targetInstanceId, relationshipType, key, value), forever)

  override def syncSaveRelationshipState(sourceInstanceId: String, targetInstanceId: String, relationshipType: String, newState: String): Unit = Await.result(saveRelationshipInstanceState(sourceInstanceId, targetInstanceId, relationshipType, newState), forever)

  override def syncSaveRelationshipOutputs(sourceInstanceId: String, targetInstanceId: String, relationshipType: String, interfaceName: String, operationName: String, outputs: java.util.Map[String, String]): Unit = Await.result(saveAllRelationshipOutputs(sourceInstanceId, targetInstanceId, relationshipType, interfaceName, operationName, outputs.asScala.toMap), forever)

  override def syncGetNodeInstancesCount(id: String): Int = Await.result(getNodeInstancesCount(id), forever)

  override def syncGetOutputs(instanceId: String, interfaceName: String, operationName: String): java.util.Map[String, String] = Await.result(getOutputs(instanceId, interfaceName, operationName), forever).asJava

  override def syncGetAttributes(instanceId: String): java.util.Map[String, String] = Await.result(getAttributes(instanceId), forever).asJava

  override def syncGetOutputInterfaces(instanceId: String): java.util.List[String] = Await.result(getOutputInterfaces(instanceId), forever).asJava

  override def syncGetOutputOperations(instanceId: String, interfaceName: String): java.util.List[String] = Await.result(getOutputOperations(instanceId, interfaceName), forever).asJava

  override def syncGetInstanceState(id: String): String = Await.result(getInstanceState(id), forever)

  override def hasExistingData: Boolean = Await.result(countInstances(), forever) > 0

  override def syncGetRelationshipAttributes(sourceInstanceId: String, targetInstanceId: String, relationshipType: String): util.Map[String, String] = Await.result(getRelationshipAttributes(sourceInstanceId, targetInstanceId, relationshipType), forever).asJava

  override def syncGetRelationshipOutputInterfaces(sourceInstanceId: String, targetInstanceId: String, relationshipType: String): util.List[String] = Await.result(getRelationshipOutputInterfaces(sourceInstanceId, targetInstanceId, relationshipType), forever).asJava

  override def syncGetRelationshipOutputOperations(sourceInstanceId: String, targetInstanceId: String, relationshipType: String, interfaceName: String): util.List[String] = Await.result(getRelationshipOutputOperations(sourceInstanceId, targetInstanceId, relationshipType, interfaceName), forever).asJava

  override def syncGetRelationshipOutputs(sourceInstanceId: String, targetInstanceId: String, relationshipType: String, interfaceName: String, operationName: String) = Await.result(getRelationshipOutputs(sourceInstanceId, targetInstanceId, relationshipType, interfaceName, operationName), forever).asJava

  override def syncGetRelationshipInstanceState(sourceInstanceId: String, targetInstanceId: String, relationshipType: String): String = Await.result(getRelationshipState(sourceInstanceId, targetInstanceId, relationshipType), forever)

  override def syncDeleteInstanceAttribute(instanceId: String, key: String): Unit = Await.result(attributeDAO.delete(instanceId, key), forever)

  override def syncDeleteRelationshipAttribute(sourceInstanceId: String, targetInstanceId: String, relationshipType: String, key: String): Unit = Await.result(relationshipAttributeDAO.delete(sourceInstanceId, targetInstanceId, relationshipType, key), forever)

  override def syncInsertNewNodeTask(instanceId: String, interfaceName: String, operationName: String): Unit = Await.result(insertNewNodeTask(instanceId, interfaceName, operationName), forever)

  override def syncStopNodeTask(instanceId: String, interfaceName: String, operationName: String, error: String): Unit = Await.result(stopNodeTask(instanceId, interfaceName, operationName, error), forever)

  override def syncFinishNodeTask(instanceId: String, interfaceName: String, operationName: String): Unit = Await.result(finishNodeTask(instanceId, interfaceName, operationName), forever)

  override def syncStartNodeTask(instanceId: String, interfaceName: String, operationName: String): Unit = Await.result(startNodeTask(instanceId, interfaceName, operationName), forever)

  override def syncInsertNewRelationshipTask(sourceInstanceId: String, targetInstanceId: String, relationshipType: String, interfaceName: String, operationName: String): Unit = Await.result(insertNewRelationshipTask(sourceInstanceId, targetInstanceId, relationshipType, interfaceName, operationName), forever)

  override def syncFinishRelationshipTask(sourceInstanceId: String, targetInstanceId: String, relationshipType: String, interfaceName: String, operationName: String): Unit = Await.result(finishRelationshipTask(sourceInstanceId, targetInstanceId, relationshipType, interfaceName, operationName), forever)

  override def syncStopRelationshipTask(sourceInstanceId: String, targetInstanceId: String, relationshipType: String, interfaceName: String, operationName: String, error: String): Unit = Await.result(stopRelationshipTask(sourceInstanceId, targetInstanceId, relationshipType, interfaceName, operationName, error), forever)

  override def syncStartRelationshipTask(sourceInstanceId: String, targetInstanceId: String, relationshipType: String, interfaceName: String, operationName: String): Unit = Await.result(startRelationshipTask(sourceInstanceId, targetInstanceId, relationshipType, interfaceName, operationName), forever)

  override def syncGetRunningExecution(): RunningExecutionDTO = Await.result(getRunningExecution.flatMap {
    _.map { executionEntity =>
      val workflowId = executionEntity.workflowId
      executionInputDAO.get(executionEntity.id).map { inputs =>
        new RunningExecutionDTO(workflowId, JavaScalaConversionUtil.toJavaMap(inputs))
      }
    }.getOrElse(Future.successful(null))
  }, forever)

  override def syncGetRunningExecutionNodeTasks(): util.Map[NodeTaskDTO, String] = Await.result(getNodeTasks.map { nodeTasks =>
    nodeTasks.map { nodeTask =>
      (new NodeTaskDTO(nodeTask.instanceId, nodeTask.interfaceName, nodeTask.operationName), nodeTask.status)
    }.toMap.asJava
  }, forever)

  override def syncGetRunningExecutionRelationshipTasks(): util.Map[RelationshipTaskDTO, String] = Await.result(getRelationshipTasks.map { relationshipTasks =>
    relationshipTasks.map { relationshipTask =>
      (new RelationshipTaskDTO(relationshipTask.sourceInstanceId, relationshipTask.targetInstanceId, relationshipTask.relationshipType, relationshipTask.interfaceName, relationshipTask.operationName), relationshipTask.status)
    }.toMap.asJava
  }, forever)

  override def syncInsertNewTask(taskId: String): Unit = Await.result(insertNewTask(taskId), forever)

  override def syncFinishTask(taskId: String): Unit = Await.result(finishTask(taskId), forever)

  override def syncStopTask(taskId: String, error: String): Unit = Await.result(stopTask(taskId, error), forever)

  override def syncStartTask(taskId: String): Unit = Await.result(startTask(taskId), forever)

  override def syncGetRunningExecutionTasks(): util.Map[TaskDTO, String] = Await.result(getTasks.map { tasks =>
    tasks.map { task =>
      (new TaskDTO(task.taskId), task.status)
    }.toMap.asJava
  }, forever)

  override def syncInsertExecutionInputs(inputs: util.Map[String, AnyRef]): Unit = Await.result(insertExecutionInputs(JavaScalaConversionUtil.toScalaMap(inputs)), forever)

  override def syncFinishRunningExecution(): Unit = Await.result(finishRunningExecution(), forever)

  override def syncStopRunningExecution(error: String): Unit = Await.result(stopExecution(Some(error)), forever)

  override def syncStopRunningExecution(): Unit = Await.result(stopExecution(None), forever)

  override def syncCancelRunningExecution(): Unit = Await.result(cancelRunningExecution(), forever)
}
