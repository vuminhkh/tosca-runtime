package dao

import javax.inject.{Inject, Singleton}

import models.RelationshipOperationOutputEntity
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.driver.JdbcProfile

import scala.concurrent.Future

trait RelationshipOutputsComponent extends RelationshipInstancesComponents {
  self: HasDatabaseConfigProvider[JdbcProfile] =>

  import driver.api._

  private val RelationshipInstances = TableQuery[RelationshipInstanceTable]

  class RelationshipOutputTable(tag: Tag) extends Table[RelationshipOperationOutputEntity](tag, "RELATIONSHIP_OUTPUT") {

    def pk = primaryKey("RELATIONSHIP_OUTPUT_PK", (sourceInstanceId, targetInstanceId, relationshipType, interfaceName, operationName, key))

    def sourceInstanceId = column[String]("SOURCE_INSTANCE_ID")

    def targetInstanceId = column[String]("TARGET_INSTANCE_ID")

    def relationshipType = column[String]("TYPE")

    def interfaceName = column[String]("INTERFACE")

    def operationName = column[String]("OPERATION")

    def key = column[String]("KEY")

    def value = column[String]("VALUE")

    def relationshipInstance =
      foreignKey("RELATIONSHIP_OUTPUT_INSTANCE_FK", (sourceInstanceId, targetInstanceId, relationshipType), RelationshipInstances)(relIns => (relIns.sourceInstanceId, relIns.targetInstanceId, relIns.relationshipType), onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def * = (sourceInstanceId, targetInstanceId, relationshipType, interfaceName, operationName, key, value) <>(RelationshipOperationOutputEntity.tupled, RelationshipOperationOutputEntity.unapply)
  }

}

@Singleton()
class RelationshipOutputDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) extends RelationshipOutputsComponent with HasDatabaseConfigProvider[JdbcProfile] {

  import driver.api._

  private val RelationshipOutputs = TableQuery[RelationshipOutputTable]

  private def filterByRelationshipIdFunction(sourceInstanceId: String, targetInstanceId: String, relationshipType: String) = {
    relationshipOutput: RelationshipOutputTable => {
      relationshipOutput.sourceInstanceId === sourceInstanceId &&
        relationshipOutput.targetInstanceId === targetInstanceId &&
        relationshipOutput.relationshipType === relationshipType
    }
  }

  def get(sourceInstanceId: String, targetInstanceId: String, relationshipType: String): Future[Seq[RelationshipOperationOutputEntity]] = {
    db.run(RelationshipOutputs.filter(filterByRelationshipIdFunction(sourceInstanceId, targetInstanceId, relationshipType)).result)
  }

  def getInterfaces(sourceInstanceId: String, targetInstanceId: String, relationshipType: String) = {
    db.run(RelationshipOutputs.filter(filterByRelationshipIdFunction(sourceInstanceId, targetInstanceId, relationshipType)).map(_.interfaceName).distinct.result)
  }

  def getOperations(sourceInstanceId: String, targetInstanceId: String, relationshipType: String, interfaceName: String) = {
    db.run(RelationshipOutputs
      .filter(filterByRelationshipIdFunction(sourceInstanceId, targetInstanceId, relationshipType))
      .filter(_.interfaceName === interfaceName)
      .map(_.operationName).distinct.result
    )
  }

  def save(attributeEntity: RelationshipOperationOutputEntity): Future[Int] = db.run(RelationshipOutputs insertOrUpdate attributeEntity)

  def saveAll(sourceInstanceId: String, targetInstanceId: String, relationshipType: String, interfaceName: String, operationName: String, outputs: Map[String, String]): Future[Option[Int]] = {
    val deleteAction = RelationshipOutputs
      .filter(filterByRelationshipIdFunction(sourceInstanceId, targetInstanceId, relationshipType))
      .filter { output => output.interfaceName === interfaceName && output.operationName === operationName }
      .delete
    val insertAction = RelationshipOutputs ++= outputs.map {
      case (key, value) => RelationshipOperationOutputEntity(sourceInstanceId, targetInstanceId, relationshipType, interfaceName, operationName, key, value)
    }
    db.run((deleteAction >> insertAction).transactionally)
  }
}
