package dao

import java.sql.SQLException
import javax.inject.{Inject, Singleton}

import com.typesafe.scalalogging.LazyLogging
import models.InstanceEntity
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import slick.driver.JdbcProfile

import scala.concurrent.Future

trait InstancesComponent extends NodesComponent {
  self: HasDatabaseConfigProvider[JdbcProfile] =>

  import driver.api._

  private val Nodes = TableQuery[NodeTable]

  class InstanceTable(tag: Tag) extends Table[InstanceEntity](tag, "INSTANCE") {

    def id = column[String]("ID", O.PrimaryKey)

    def nodeId = column[String]("NODE_ID")

    def state = column[String]("STATE")

    def node = foreignKey("INSTANCE_NODE_FK", nodeId, Nodes)(_.id, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def * = (id, nodeId, state) <>(InstanceEntity.tupled, InstanceEntity.unapply)
  }

}

@Singleton()
class InstanceDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) extends InstancesComponent with LazyLogging with HasDatabaseConfigProvider[JdbcProfile] {

  import driver.api._

  private val Instances = TableQuery[InstanceTable]

  def all(): Future[Seq[InstanceEntity]] = db.run(Instances.result)

  def count() : Future[Int] = db.run(Instances.countDistinct.result)

  def insertIfNotExist(instanceEntity: InstanceEntity): Future[Int] = {
    val insertAction = Instances.filter(_.id === instanceEntity.id).exists.result.flatMap { exists =>
      if (!exists) Instances += instanceEntity
      else DBIO.successful(0)
    }.transactionally
    val insertFuture = db.run(insertAction)
    insertFuture.onFailure {
      case error: SQLException => logger.error(s"SQL exception happened while inserting instance $instanceEntity", error)
    }
    insertFuture
  }

  def delete(id: String) = db.run(Instances.filter(_.id === id).delete)

  def saveState(id: String, newState: String) = {
    db.run(Instances.filter(_.id === id).map(_.state).update(newState))
  }

  def getState(id: String) = {
    db.run(Instances.filter(_.id === id).map(_.state).result.head)
  }
}
