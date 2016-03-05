package dao

import javax.inject.{Inject, Singleton}

import models.AttributeEntity
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.driver.JdbcProfile

import scala.concurrent.Future

trait AttributesComponent extends InstancesComponent {
  self: HasDatabaseConfigProvider[JdbcProfile] =>

  import driver.api._

  private val Instances = TableQuery[InstanceTable]

  class AttributeTable(tag: Tag) extends Table[AttributeEntity](tag, "ATTRIBUTE") {

    def pk = primaryKey("ATTRIBUTE_PK", (instanceId, key))

    def instanceId = column[String]("INSTANCE_ID")

    def key = column[String]("KEY")

    def value = column[String]("VALUE")

    def instance = foreignKey("ATTRIBUTE_INSTANCE_FK", instanceId, Instances)(_.id, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def * = (instanceId, key, value) <>(AttributeEntity.tupled, AttributeEntity.unapply)
  }

}

@Singleton()
class AttributeDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) extends AttributesComponent with HasDatabaseConfigProvider[JdbcProfile] {

  import driver.api._

  private val Attributes = TableQuery[AttributeTable]

  def get(instanceId: String): Future[Seq[AttributeEntity]] = {
    db.run(Attributes.filter(_.instanceId === instanceId).result)
  }

  def save(attributeEntity: AttributeEntity): Future[Int] = db.run(Attributes insertOrUpdate attributeEntity)

  def delete(instanceId: String, key: String) = {
    db.run(Attributes.filter(attribute => attribute.instanceId === instanceId && attribute.key === key).delete)
  }
}
