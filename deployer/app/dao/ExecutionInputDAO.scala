package dao

import javax.inject.{Inject, Singleton}

import com.toscaruntime.util.{JSONUtil, JavaScalaConversionUtil}
import models.ExecutionInputEntity
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import slick.driver.JdbcProfile

trait ExecutionInputsComponent extends ExecutionsComponent {
  self: HasDatabaseConfigProvider[JdbcProfile] =>

  import driver.api._

  private val Executions = TableQuery[ExecutionTable]

  class ExecutionInputTable(tag: Tag) extends Table[ExecutionInputEntity](tag, "EXECUTION_INPUT") {

    def pk = primaryKey("EXECUTION_INPUT_PK", (executionId, key))

    def executionId = column[String]("EXECUTION_ID")

    def key = column[String]("KEY")

    def value = column[String]("VALUE")

    def execution = foreignKey("EXECUTION_INPUT_EXECUTION_FK", executionId, Executions)(_.id, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def * = (executionId, key, value) <>(ExecutionInputEntity.tupled, ExecutionInputEntity.unapply)
  }

}

@Singleton()
class ExecutionInputDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) extends ExecutionInputsComponent with HasDatabaseConfigProvider[JdbcProfile] {

  import driver.api._

  private val ExecutionInputs = TableQuery[ExecutionInputTable]

  def get(executionId: String) = {
    db.run(ExecutionInputs.filter(_.executionId === executionId).result).map {
      _.map {
        case executionInputEntity => (executionInputEntity.key, JavaScalaConversionUtil.toScala(JSONUtil.toObject(executionInputEntity.value)))
      }.toMap
    }
  }

  def insert(executionId: String, inputs: Map[String, Any]) = {
    val action = ExecutionInputs ++= inputs.map {
      case (key, value) => ExecutionInputEntity(executionId, key, JSONUtil.toString(JavaScalaConversionUtil.toJava(value)))
    }
    db.run(action)
  }
}
