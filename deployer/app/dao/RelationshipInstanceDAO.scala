package dao

import javax.inject.{Inject, Singleton}

import models.RelationshipInstanceEntity
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import slick.driver.JdbcProfile

import scala.concurrent.Future

trait RelationshipInstancesComponents extends InstancesComponent with RelationshipsComponents {
  self: HasDatabaseConfigProvider[JdbcProfile] =>

  import driver.api._

  private val Relationships = TableQuery[RelationshipTable]

  private val Instances = TableQuery[InstanceTable]

  class RelationshipInstanceTable(tag: Tag) extends Table[RelationshipInstanceEntity](tag, "RELATIONSHIP_INSTANCE") {

    def pk = primaryKey("RELATIONSHIP_INSTANCE_PK", (sourceInstanceId, targetInstanceId, relationshipType))

    def sourceInstanceId = column[String]("SOURCE_INSTANCE_ID")

    def targetInstanceId = column[String]("TARGET_INSTANCE_ID")

    def sourceNodeId = column[String]("SOURCE_NODE_ID")

    def targetNodeId = column[String]("TARGET_NODE_ID")

    def relationshipType = column[String]("TYPE")

    def state = column[String]("STATE")

    def sourceInstance = foreignKey("RELATIONSHIP_INSTANCE_SOURCE_INSTANCE_FK", sourceInstanceId, Instances)(_.id, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def targetInstance = foreignKey("RELATIONSHIP_INSTANCE_TARGET_INSTANCE_FK", targetInstanceId, Instances)(_.id, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def relationshipNode =
      foreignKey("RELATIONSHIP_INSTANCE_NODE_FK", (sourceNodeId, targetNodeId, relationshipType), Relationships)(relIns => (relIns.sourceId, relIns.targetId, relIns.relationshipType), onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def * = (sourceInstanceId, targetInstanceId, sourceNodeId, targetNodeId, relationshipType, state) <>(RelationshipInstanceEntity.tupled, RelationshipInstanceEntity.unapply)
  }

}

@Singleton()
class RelationshipInstanceDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) extends RelationshipInstancesComponents with HasDatabaseConfigProvider[JdbcProfile] {

  import driver.api._

  private val RelationshipInstances = TableQuery[RelationshipInstanceTable]

  private def filterByRelationshipIdFunction(sourceInstanceId: String, targetInstanceId: String, relationshipType: String) = {
    relationshipInstance: RelationshipInstanceTable => {
      relationshipInstance.sourceInstanceId === sourceInstanceId &&
        relationshipInstance.targetInstanceId === targetInstanceId &&
        relationshipInstance.relationshipType === relationshipType
    }
  }

  def all(): Future[Seq[RelationshipInstanceEntity]] = db.run(RelationshipInstances.result)

  def getFromSource(sourceInstanceId: String): Future[Seq[RelationshipInstanceEntity]] = db.run(RelationshipInstances.filter(_.sourceInstanceId === sourceInstanceId).result)

  def getFromTarget(targetInstanceId: String): Future[Seq[RelationshipInstanceEntity]] = db.run(RelationshipInstances.filter(_.targetInstanceId === targetInstanceId).result)

  def insertIfNotExist(relIns: RelationshipInstanceEntity): Future[Int] = {
    val insertAction = RelationshipInstances
      .filter(filterByRelationshipIdFunction(relIns.sourceInstanceId, relIns.targetInstanceId, relIns.relationshipType))
      .exists.result.flatMap { exists =>
      if (!exists) RelationshipInstances += relIns
      else DBIO.successful(0)
    }.transactionally
    db.run(insertAction)
  }

  def delete(sourceInstanceId: String, targetInstanceId: String, relationshipType: String) = {
    db.run(RelationshipInstances
      .filter(filterByRelationshipIdFunction(sourceInstanceId, targetInstanceId, relationshipType))
      .delete
    )
  }

  def saveState(sourceInstanceId: String, targetInstanceId: String, relationshipType: String, newState: String) = {
    db.run(RelationshipInstances
      .filter(filterByRelationshipIdFunction(sourceInstanceId, targetInstanceId, relationshipType))
      .map(_.state)
      .update(newState))
  }

  def getState(sourceInstanceId: String, targetInstanceId: String, relationshipType: String) = {
    db.run(RelationshipInstances
      .filter(filterByRelationshipIdFunction(sourceInstanceId, targetInstanceId, relationshipType))
      .map(_.state)
      .result.head)
  }
}

