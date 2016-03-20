package dao

import java.sql.Timestamp
import javax.inject.{Inject, Singleton}

import com.toscaruntime.exception.deployment.execution.ConcurrentWorkflowExecutionException
import models.ExecutionEntity
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import slick.driver.JdbcProfile

import scala.concurrent.Future

trait ExecutionsComponent {
  self: HasDatabaseConfigProvider[JdbcProfile] =>

  import driver.api._

  class ExecutionTable(tag: Tag) extends Table[ExecutionEntity](tag, "EXECUTION") {

    def id = column[String]("ID", O.PrimaryKey)

    def workflowId = column[String]("WORKFLOW_ID")

    def startTime = column[Timestamp]("START_TIME")

    def endTime = column[Option[Timestamp]]("END_TIME")

    def error = column[Option[String]]("ERROR")

    def status = column[String]("STATUS")

    def * = (id, workflowId, startTime, endTime, error, status) <>(ExecutionEntity.tupled, ExecutionEntity.unapply)
  }

}

@Singleton()
class ExecutionDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) extends ExecutionsComponent with HasDatabaseConfigProvider[JdbcProfile] {

  import driver.api._

  private val Executions = TableQuery[ExecutionTable]

  def get(executionId: String): Future[Seq[ExecutionEntity]] = db.run(Executions.filter(_.id === executionId).result)

  def all(): Future[Seq[ExecutionEntity]] = db.run(Executions.sortBy(_.endTime.desc.nullsFirst).result)

  def insert(executionEntity: ExecutionEntity): Future[Int] = {
    val insertAction = Executions.filter { execution =>
      execution.endTime.isEmpty
    }.result.flatMap { runningExecutions =>
      if (runningExecutions.isEmpty) {
        Executions += executionEntity
      } else {
        throw new ConcurrentWorkflowExecutionException(s"Cannot start execution for workflow ${executionEntity.workflowId} because deployment has unfinished executions")
      }
    }
    db.run(insertAction.transactionally)
  }

  def stop(error: Option[String]) = {
    db.run(Executions.filter(_.endTime.isEmpty).map { ex => (ex.status, ex.error) }.update(("STOPPED", error)))
  }

  def resume() = {
    db.run(Executions.filter(_.endTime.isEmpty).map { ex => (ex.status, ex.error) }.update(("RUNNING", None)))
  }

  def finish(status: String, error: Option[String]) = {
    db.run(Executions.filter(_.endTime.isEmpty).map { ex => (ex.status, ex.endTime, ex.error) }.update((status, Some(new Timestamp(System.currentTimeMillis())), error)))
  }
}