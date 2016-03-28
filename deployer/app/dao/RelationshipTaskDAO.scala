package dao

import java.sql.Timestamp
import javax.inject.{Inject, Singleton}

import com.toscaruntime.constant.ExecutionConstant._
import models.{RelationshipOperationEntity, RelationshipTaskEntity, NodeTaskEntity}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import slick.driver.JdbcProfile

import scala.concurrent.Future

trait RelationshipTasksComponent extends RelationshipOperationsComponent with ExecutionsComponent {
  self: HasDatabaseConfigProvider[JdbcProfile] =>

  import driver.api._

  private val Operations = TableQuery[RelationshipOperationTable]

  private val Executions = TableQuery[ExecutionTable]

  class RelationshipTaskTable(tag: Tag) extends Table[RelationshipTaskEntity](tag, "RELATIONSHIP_TASK") {

    def pk = primaryKey("RELATIONSHIP_TASK_PK", (executionId, sourceInstanceId, targetInstanceId, relationshipType, interfaceName, operationName))

    def executionId = column[String]("EXECUTION_ID")

    def sourceInstanceId = column[String]("SOURCE_INSTANCE_ID")

    def targetInstanceId = column[String]("TARGET_INSTANCE_ID")

    def relationshipType = column[String]("TYPE")

    def interfaceName = column[String]("INTERFACE")

    def operationName = column[String]("OPERATION")

    def status = column[String]("STATUS")

    def startTime = column[Option[Timestamp]]("START_TIME")

    def endTime = column[Option[Timestamp]]("END_TIME")

    def error = column[Option[String]]("ERROR")

    def execution = foreignKey("RELATIONSHIP_TASK_EXECUTION_FK", executionId, Executions)(_.id, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def operation =
      foreignKey("RELATIONSHIP_TASK_RELATIONSHIP_OPERATION_FK", (sourceInstanceId, targetInstanceId, relationshipType, interfaceName, operationName), Operations)(op => (op.sourceInstanceId, op.targetInstanceId, op.relationshipType, op.interfaceName, op.operationName), onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def * = (executionId, sourceInstanceId, targetInstanceId, relationshipType, interfaceName, operationName, status, startTime, endTime, error) <>(RelationshipTaskEntity.tupled, RelationshipTaskEntity.unapply)

  }

}

@Singleton()
class RelationshipTaskDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) extends RelationshipTasksComponent with ExecutionsComponent with HasDatabaseConfigProvider[JdbcProfile] {


  import driver.api._

  private val RelationshipTasks = TableQuery[RelationshipTaskTable]

  private val Executions = TableQuery[ExecutionTable]

  private def filterByTask(executionId: String, operation: RelationshipOperationEntity) = {
    task: RelationshipTaskTable => {
      task.executionId === executionId &&
        task.sourceInstanceId === operation.sourceInstanceId &&
        task.targetInstanceId === operation.targetInstanceId &&
        task.relationshipType === operation.relationshipType &&
        task.interfaceName === operation.interfaceName &&
        task.operationName === operation.operationName
    }
  }

  private def getRunningExecution = Executions.filter(_.endTime.isEmpty).map(_.id).result.head

  def insertNewTask(operation: RelationshipOperationEntity) = {
    db.run(
      getRunningExecution.flatMap(exId =>
        RelationshipTasks += RelationshipTaskEntity(exId, operation.sourceInstanceId, operation.targetInstanceId, operation.relationshipType, operation.interfaceName, operation.operationName, INITIAL, None, None, None)
      )
    )
  }

  def startTask(operation: RelationshipOperationEntity) = {
    db.run(getRunningExecution.flatMap(exId =>
      RelationshipTasks.filter(filterByTask(exId, operation))
        .filter(_.endTime.isEmpty)
        .map { task => (task.status, task.startTime) }.update((RUNNING, Some(new Timestamp(System.currentTimeMillis())))))
    )
  }

  def finishTask(operation: RelationshipOperationEntity) = {
    db.run(getRunningExecution.flatMap(exId =>
      RelationshipTasks.filter(filterByTask(exId, operation))
        .filter(_.endTime.isEmpty)
        .map { task => (task.status, task.endTime) }.update((SUCCESS, Some(new Timestamp(System.currentTimeMillis())))))
    )
  }

  def stopTask(operation: RelationshipOperationEntity, error: String) = {
    db.run(getRunningExecution.flatMap(exId =>
      RelationshipTasks.filter(filterByTask(exId, operation))
        .filter(_.endTime.isEmpty)
        .map { task => (task.status, task.error) }.update((STOPPED, Some(error))))
    )
  }

  def getRunningExecutionTasks: Future[Seq[RelationshipTaskEntity]] = {
    db.run(getRunningExecution.flatMap(exId =>
      RelationshipTasks.filter { task => task.executionId === exId }.result
    ))
  }
}
