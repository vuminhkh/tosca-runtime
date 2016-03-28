package dao

import javax.inject.{Inject, Singleton}

import models.RelationshipOperationEntity
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.driver.JdbcProfile

import scala.concurrent.Future

trait RelationshipOperationsComponent extends RelationshipInstancesComponents {
  self: HasDatabaseConfigProvider[JdbcProfile] =>

  import driver.api._

  private val RelationshipInstances = TableQuery[RelationshipInstanceTable]

  class RelationshipOperationTable(tag: Tag) extends Table[RelationshipOperationEntity](tag, "RELATIONSHIP_OPERATION") {

    def pk = primaryKey("RELATIONSHIP_OPERATION_PK", (sourceInstanceId, targetInstanceId, relationshipType, interfaceName, operationName))

    def sourceInstanceId = column[String]("SOURCE_INSTANCE_ID")

    def targetInstanceId = column[String]("TARGET_INSTANCE_ID")

    def relationshipType = column[String]("TYPE")

    def interfaceName = column[String]("INTERFACE")

    def operationName = column[String]("OPERATION")

    def relationshipInstance =
      foreignKey("RELATIONSHIP_OPERATION_INSTANCE_FK", (sourceInstanceId, targetInstanceId, relationshipType), RelationshipInstances)(relIns => (relIns.sourceInstanceId, relIns.targetInstanceId, relIns.relationshipType), onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def * = (sourceInstanceId, targetInstanceId, relationshipType, interfaceName, operationName) <>(RelationshipOperationEntity.tupled, RelationshipOperationEntity.unapply)
  }

}

@Singleton()
class RelationshipOperationDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) extends RelationshipOperationsComponent with HasDatabaseConfigProvider[JdbcProfile] {

  import driver.api._

  private val RelationshipOperations = TableQuery[RelationshipOperationTable]

  def save(relationshipOperation: RelationshipOperationEntity): Future[Int] = db.run(RelationshipOperations insertOrUpdate relationshipOperation)

}
