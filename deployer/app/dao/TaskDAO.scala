package dao

import java.sql.Timestamp
import javax.inject.{Inject, Singleton}

import com.toscaruntime.constant.ExecutionConstant._
import models.TaskEntity
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import slick.driver.JdbcProfile

import scala.concurrent.Future

trait TasksComponent extends OperationsComponent with ExecutionsComponent {
  self: HasDatabaseConfigProvider[JdbcProfile] =>

  import driver.api._

  private val Executions = TableQuery[ExecutionTable]

  class TaskTable(tag: Tag) extends Table[TaskEntity](tag, "TASK") {

    def pk = primaryKey("TASK_PK", (executionId, taskId))

    def executionId = column[String]("EXECUTION_ID")

    def taskId = column[String]("TASK_ID")

    def status = column[String]("STATUS")

    def startTime = column[Option[Timestamp]]("START_TIME")

    def endTime = column[Option[Timestamp]]("END_TIME")

    def error = column[Option[String]]("ERROR")

    def execution = foreignKey("TASK_EXECUTION_FK", executionId, Executions)(_.id, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def * = (executionId, taskId, status, startTime, endTime, error) <>(TaskEntity.tupled, TaskEntity.unapply)
  }

}

@Singleton()
class TaskDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) extends TasksComponent with ExecutionsComponent with HasDatabaseConfigProvider[JdbcProfile] {

  import driver.api._

  private val Tasks = TableQuery[TaskTable]

  private val Executions = TableQuery[ExecutionTable]

  private def getRunningExecution = Executions.filter(_.endTime.isEmpty).map(_.id).result.head

  def insertNewTask(taskId: String) = {
    db.run(getRunningExecution.flatMap(exId =>
      Tasks += TaskEntity(exId, taskId, INITIAL, None, None, None)
    ))
  }

  def startTask(taskId: String) = {
    db.run(getRunningExecution.flatMap(exId =>
      Tasks.filter(task => task.endTime.isEmpty && task.taskId === taskId)
        .map { task => (task.status, task.startTime) }.update((RUNNING, Some(new Timestamp(System.currentTimeMillis())))))
    )
  }

  def finishTask(taskId: String) = {
    db.run(getRunningExecution.flatMap(exId =>
      Tasks.filter(task => task.endTime.isEmpty && task.taskId === taskId)
        .map { task => (task.status, task.endTime) }.update((SUCCESS, Some(new Timestamp(System.currentTimeMillis())))))
    )
  }

  def stopTask(taskId: String, error: String) = {
    db.run(getRunningExecution.flatMap(exId =>
      Tasks.filter(task => task.endTime.isEmpty && task.taskId === taskId)
        .map { task => (task.status, task.error) }.update((STOPPED, Some(error)))
    ))
  }

  def getRunningExecutionTasks: Future[Seq[TaskEntity]] = {
    db.run(getRunningExecution.flatMap(exId =>
      Tasks.filter { task => task.executionId === exId }.result
    ))
  }
}
