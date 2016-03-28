package dao

import javax.inject.{Inject, Singleton}

import models.OperationEntity
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.driver.JdbcProfile

import scala.concurrent.Future

trait OperationsComponent extends InstancesComponent {
  self: HasDatabaseConfigProvider[JdbcProfile] =>

  import driver.api._

  private val Instances = TableQuery[InstanceTable]

  class OperationTable(tag: Tag) extends Table[OperationEntity](tag, "OPERATION") {

    def pk = primaryKey("OPERATION_PK", (instanceId, interfaceName, operationName))

    def instanceId = column[String]("INSTANCE_ID")

    def interfaceName = column[String]("INTERFACE_NAME")

    def operationName = column[String]("OPERATION_NAME")

    def instance = foreignKey("OPERATION_INSTANCE_FK", instanceId, Instances)(_.id, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def * = (instanceId, interfaceName, operationName) <>(OperationEntity.tupled, OperationEntity.unapply)
  }

}

@Singleton()
class OperationDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) extends OperationsComponent with HasDatabaseConfigProvider[JdbcProfile] {

  import driver.api._

  private val Operations = TableQuery[OperationTable]

  def save(operationEntity: OperationEntity): Future[Int] = db.run(Operations insertOrUpdate operationEntity)

}

