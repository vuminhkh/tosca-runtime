package dao

import javax.inject.{Inject, Singleton}

import models.OperationOutputEntity
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.driver.JdbcProfile

import scala.concurrent.Future

trait OperationOutputsComponent extends OperationsComponent {
  self: HasDatabaseConfigProvider[JdbcProfile] =>

  import driver.api._

  private val Operations = TableQuery[OperationTable]

  class OperationOutputTable(tag: Tag) extends Table[OperationOutputEntity](tag, "OPERATION_OUTPUT") {

    def pk = primaryKey("OPERATION_OUTPUT_PK", (instanceId, interfaceName, operationName, key))

    def instanceId = column[String]("INSTANCE_ID")

    def interfaceName = column[String]("INTERFACE_NAME")

    def operationName = column[String]("OPERATION_NAME")

    def key = column[String]("KEY")

    def value = column[String]("VALUE")

    def operation =
      foreignKey("OPERATION_OUTPUT_OPERATION_FK", (instanceId, interfaceName, operationName), Operations)(op => (op.instanceId, op.interfaceName, op.operationName), onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def * = (instanceId, interfaceName, operationName, key, value) <>(OperationOutputEntity.tupled, OperationOutputEntity.unapply)
  }

}

@Singleton()
class OperationOutputDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) extends OperationOutputsComponent with HasDatabaseConfigProvider[JdbcProfile] {

  import driver.api._

  private val OperationOutputs = TableQuery[OperationOutputTable]

  def get(instanceId: String, interfaceName: String, operationName: String): Future[Seq[OperationOutputEntity]] = {
    db.run(OperationOutputs.filter(operationOutput =>
      operationOutput.instanceId === instanceId &&
        operationOutput.interfaceName === interfaceName &&
        operationOutput.operationName === operationName).result
    )
  }

  def getInterfaces(instanceId: String) = {
    db.run(OperationOutputs.filter(_.instanceId === instanceId).map(_.interfaceName).distinct.result)
  }

  def getOperations(instanceId: String, interfaceName: String) = {
    db.run(OperationOutputs.filter { operationOutput =>
      operationOutput.instanceId === instanceId &&
        operationOutput.interfaceName === interfaceName
    }.map(_.operationName).distinct.result)
  }

  def save(operationOutputEntity: OperationOutputEntity): Future[Int] = db.run(OperationOutputs insertOrUpdate operationOutputEntity)

  def saveAll(instanceId: String, interfaceName: String, operationName: String, outputs: Map[String, String]) = {
    val deleteAction = OperationOutputs.filter { output =>
      output.instanceId === instanceId &&
        output.interfaceName === interfaceName &&
        output.operationName === operationName
    }.delete
    val insertAction = OperationOutputs ++= outputs.map {
      case (key, value) => OperationOutputEntity(instanceId, interfaceName, operationName, key, value)
    }
    db.run((deleteAction >> insertAction).transactionally)
  }
}
