package dao

import java.sql.Timestamp
import javax.inject.{Inject, Singleton}

import com.toscaruntime.constant.ExecutionConstant._
import models.{OperationEntity, NodeTaskEntity}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import slick.driver.JdbcProfile

import scala.concurrent.Future

trait NodeTasksComponent extends OperationsComponent with ExecutionsComponent {
  self: HasDatabaseConfigProvider[JdbcProfile] =>

  import driver.api._

  private val Operations = TableQuery[OperationTable]

  private val Executions = TableQuery[ExecutionTable]

  class NodeTaskTable(tag: Tag) extends Table[NodeTaskEntity](tag, "NODE_TASK") {

    def pk = primaryKey("NODE_TASK_PK", (executionId, instanceId, interfaceName, operationName))

    def executionId = column[String]("EXECUTION_ID")

    def instanceId = column[String]("INSTANCE_ID")

    def interfaceName = column[String]("INTERFACE_NAME")

    def operationName = column[String]("OPERATION_NAME")

    def status = column[String]("STATUS")

    def startTime = column[Option[Timestamp]]("START_TIME")

    def endTime = column[Option[Timestamp]]("END_TIME")

    def error = column[Option[String]]("ERROR")

    def execution = foreignKey("NODE_TASK_EXECUTION_FK", executionId, Executions)(_.id, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def operation =
      foreignKey("NODE_TASK_OPERATION_FK", (instanceId, interfaceName, operationName), Operations)(op => (op.instanceId, op.interfaceName, op.operationName), onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def * = (executionId, instanceId, interfaceName, operationName, status, startTime, endTime, error) <>(NodeTaskEntity.tupled, NodeTaskEntity.unapply)
  }

}

@Singleton()
class NodeTaskDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) extends NodeTasksComponent with ExecutionsComponent with HasDatabaseConfigProvider[JdbcProfile] {

  import driver.api._

  private val NodeTasks = TableQuery[NodeTaskTable]

  private val Executions = TableQuery[ExecutionTable]

  private def getRunningExecution = Executions.filter(_.endTime.isEmpty).map(_.id).result.head

  private def filterByTask(executionId: String, operation: OperationEntity) = {
    task: NodeTaskTable => {
      task.executionId === executionId &&
        task.instanceId === operation.instanceId &&
        task.interfaceName === operation.interfaceName &&
        task.operationName === operation.operationName
    }
  }

  def insertNewTask(operation: OperationEntity) = {
    db.run(getRunningExecution.flatMap(exId =>
      NodeTasks += NodeTaskEntity(exId, operation.instanceId, operation.interfaceName, operation.operationName, INITIAL, None, None, None)
    ))
  }

  def startTask(operation: OperationEntity) = {
    db.run(getRunningExecution.flatMap(exId =>
      NodeTasks.filter(filterByTask(exId, operation))
        .filter(_.endTime.isEmpty)
        .map { task => (task.status, task.startTime) }.update((RUNNING, Some(new Timestamp(System.currentTimeMillis())))))
    )
  }

  def finishTask(operation: OperationEntity) = {
    db.run(getRunningExecution.flatMap(exId =>
      NodeTasks.filter(filterByTask(exId, operation))
        .filter(_.endTime.isEmpty)
        .map { task => (task.status, task.endTime) }.update((SUCCESS, Some(new Timestamp(System.currentTimeMillis())))))
    )
  }

  def stopTask(operation: OperationEntity, error: String) = {
    db.run(getRunningExecution.flatMap(exId =>
      NodeTasks.filter(filterByTask(exId, operation))
        .filter(_.endTime.isEmpty)
        .map { task => (task.status, task.error) }.update((STOPPED, Some(error)))
    ))
  }

  def getRunningExecutionTasks: Future[Seq[NodeTaskEntity]] = {
    db.run(getRunningExecution.flatMap(exId =>
      NodeTasks.filter { task => task.executionId === exId }.result
    ))
  }
}
