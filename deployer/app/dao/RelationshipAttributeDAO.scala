package dao

import javax.inject.{Inject, Singleton}

import models.RelationshipAttributeEntity
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.driver.JdbcProfile

import scala.concurrent.Future

trait RelationshipAttributesComponent extends RelationshipInstancesComponents {
  self: HasDatabaseConfigProvider[JdbcProfile] =>

  import driver.api._

  private val RelationshipInstances = TableQuery[RelationshipInstanceTable]

  class RelationshipAttributeTable(tag: Tag) extends Table[RelationshipAttributeEntity](tag, "RELATIONSHIP_ATTRIBUTE") {

    def pk = primaryKey("RELATIONSHIP_ATTRIBUTE_PK", (sourceInstanceId, targetInstanceId, relationshipType, key))

    def sourceInstanceId = column[String]("SOURCE_INSTANCE_ID")

    def targetInstanceId = column[String]("TARGET_INSTANCE_ID")

    def relationshipType = column[String]("TYPE")

    def key = column[String]("KEY")

    def value = column[String]("VALUE")

    def relationshipInstance =
      foreignKey("RELATIONSHIP_ATTRIBUTE_INSTANCE_FK", (sourceInstanceId, targetInstanceId, relationshipType), RelationshipInstances)(relIns => (relIns.sourceInstanceId, relIns.targetInstanceId, relIns.relationshipType), onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def * = (sourceInstanceId, targetInstanceId, relationshipType, key, value) <>(RelationshipAttributeEntity.tupled, RelationshipAttributeEntity.unapply)
  }

}

@Singleton()
class RelationshipAttributeDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) extends RelationshipAttributesComponent with HasDatabaseConfigProvider[JdbcProfile] {

  import driver.api._

  private val RelationshipAttributes = TableQuery[RelationshipAttributeTable]

  private def filterByRelationshipIdFunction(sourceInstanceId: String, targetInstanceId: String, relationshipType: String) = {
    relationshipAttribute: RelationshipAttributeTable => {
      relationshipAttribute.sourceInstanceId === sourceInstanceId &&
        relationshipAttribute.targetInstanceId === targetInstanceId &&
        relationshipAttribute.relationshipType === relationshipType
    }
  }

  def get(sourceInstanceId: String, targetInstanceId: String, relationshipType: String): Future[Seq[RelationshipAttributeEntity]] = {
    db.run(RelationshipAttributes.filter(filterByRelationshipIdFunction(sourceInstanceId, targetInstanceId, relationshipType)).result)
  }

  def save(attributeEntity: RelationshipAttributeEntity): Future[Int] = db.run(RelationshipAttributes insertOrUpdate attributeEntity)

  def delete(sourceInstanceId: String, targetInstanceId: String, relationshipType: String, key: String) = {
    db.run(RelationshipAttributes.filter(filterByRelationshipIdFunction(sourceInstanceId, targetInstanceId, relationshipType)).filter(_.key === key).delete)
  }
}
