package dao

import javax.inject.{Inject, Singleton}

import models.RelationshipEntity
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import slick.driver.JdbcProfile

import scala.concurrent.Future

trait RelationshipsComponents extends NodesComponent {
  self: HasDatabaseConfigProvider[JdbcProfile] =>

  import driver.api._

  private val Nodes = TableQuery[NodeTable]

  class RelationshipTable(tag: Tag) extends Table[RelationshipEntity](tag, "RELATIONSHIP") {

    def pk = primaryKey("RELATIONSHIP_PK", (sourceId, targetId, relationshipType))

    def sourceId = column[String]("SOURCE_ID")

    def targetId = column[String]("TARGET_ID")

    def relationshipType = column[String]("TYPE")

    def source = foreignKey("RELATIONSHIP_SOURCE_FK", sourceId, Nodes)(_.id, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def target = foreignKey("RELATIONSHIP_TARGET_FK", targetId, Nodes)(_.id, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def * = (sourceId, targetId, relationshipType) <>(RelationshipEntity.tupled, RelationshipEntity.unapply)
  }

}

@Singleton()
class RelationshipDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) extends RelationshipsComponents with HasDatabaseConfigProvider[JdbcProfile] {

  import driver.api._

  private val Relationships = TableQuery[RelationshipTable]

  def all(): Future[Seq[RelationshipEntity]] = db.run(Relationships.result)

  def getFromSource(sourceId: String): Future[Seq[RelationshipEntity]] = db.run(Relationships.filter(_.sourceId === sourceId).result)

  def getFromTarget(targetId: String): Future[Seq[RelationshipEntity]] = db.run(Relationships.filter(_.targetId === targetId).result)

  def insertIfNotExist(relationshipEntity: RelationshipEntity): Future[Int] = {
    val insertAction = Relationships.filter { relationship =>
      relationship.sourceId === relationshipEntity.sourceId &&
        relationship.targetId === relationshipEntity.targetId &&
        relationship.relationshipType === relationshipEntity.relationshipType
    }.exists.result.flatMap { exists =>
      if (!exists) Relationships += relationshipEntity
      else DBIO.successful(0)
    }.transactionally
    db.run(insertAction)
  }
}
