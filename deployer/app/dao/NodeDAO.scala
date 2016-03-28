package dao

import java.sql.SQLException
import javax.inject.{Inject, Singleton}

import com.typesafe.scalalogging.LazyLogging
import models.NodeEntity
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import slick.driver.JdbcProfile

import scala.concurrent.Future

trait NodesComponent {
  self: HasDatabaseConfigProvider[JdbcProfile] =>

  import driver.api._

  class NodeTable(tag: Tag) extends Table[NodeEntity](tag, "NODE") {

    def id = column[String]("ID", O.PrimaryKey)

    def instancesCount = column[Int]("INSTANCES_COUNT")

    def * = (id, instancesCount) <>(NodeEntity.tupled, NodeEntity.unapply)
  }

}

@Singleton()
class NodeDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) extends NodesComponent with LazyLogging with HasDatabaseConfigProvider[JdbcProfile] {

  import driver.api._

  private val Nodes = TableQuery[NodeTable]

  def all(): Future[Seq[NodeEntity]] = db.run(Nodes.result)

  def count() = {
    db.run(Nodes.countDistinct.result)
  }

  def getNodeInstancesCount(id: String) = {
    db.run(Nodes.filter(_.id === id).map(_.instancesCount).result.head)
  }

  def insertIfNotExist(nodeEntity: NodeEntity): Future[Int] = {
    val insertAction = Nodes.filter(_.id === nodeEntity.id).exists.result.flatMap { exists =>
      if (!exists) Nodes += nodeEntity else DBIO.successful(0)
    }.transactionally
    db.run(insertAction)
  }

  def saveInstancesCount(id: String, newInstancesCount: Int) = {
    db.run(Nodes.filter(_.id === id).map(_.instancesCount).update(newInstancesCount))
  }
}
