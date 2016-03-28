package dao

import javax.inject.{Inject, Singleton}

import com.typesafe.scalalogging.LazyLogging
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import slick.driver.JdbcProfile
import slick.jdbc.meta.MTable

@Singleton()
class Schema @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)
  extends HasDatabaseConfigProvider[JdbcProfile]
    with AttributesComponent
    with ExecutionsComponent
    with ExecutionInputsComponent
    with InstancesComponent
    with NodesComponent
    with OperationOutputsComponent
    with RelationshipsComponents
    with RelationshipInstancesComponents
    with RelationshipAttributesComponent
    with OperationsComponent
    with RelationshipOperationsComponent
    with RelationshipOutputsComponent
    with RelationshipTasksComponent
    with NodeTasksComponent
    with TasksComponent
    with LazyLogging {

  import driver.api._

  private val Nodes = TableQuery[NodeTable]
  private val Instances = TableQuery[InstanceTable]
  private val Attributes = TableQuery[AttributeTable]
  private val OperationOutputs = TableQuery[OperationOutputTable]
  private val Relationships = TableQuery[RelationshipTable]
  private val RelationshipInstances = TableQuery[RelationshipInstanceTable]
  private val RelationshipAttributes = TableQuery[RelationshipAttributeTable]
  private val RelationshipOutputs = TableQuery[RelationshipOutputTable]
  private val Operations = TableQuery[OperationTable]
  private val RelationshipOperations = TableQuery[RelationshipOperationTable]
  private val Executions = TableQuery[ExecutionTable]
  private val ExecutionInputs = TableQuery[ExecutionInputTable]
  private val NodeTasks = TableQuery[NodeTaskTable]
  private val RelationshipTasks = TableQuery[RelationshipTaskTable]
  private val Tasks = TableQuery[TaskTable]

  private val schema = Nodes.schema ++
    Instances.schema ++
    Relationships.schema ++
    RelationshipInstances.schema ++
    Operations.schema ++
    RelationshipOperations.schema ++
    Attributes.schema ++
    RelationshipAttributes.schema ++
    OperationOutputs.schema ++
    RelationshipOutputs.schema ++
    Executions.schema ++
    ExecutionInputs.schema ++
    NodeTasks.schema ++
    RelationshipTasks.schema ++
    Tasks.schema

  def isSchemaCreated = db.run(MTable.getTables.headOption.map(_.isDefined))

  def createSchema() = {
    logger.info("Create persistence schema with statements")
    schema.createStatements.foreach(statement => logger.info(statement))
    db.run(schema.create)
  }

  def dropSchema() = {
    logger.info("Drop persistence schema with statements")
    schema.dropStatements.foreach(statement => logger.info(statement))
    db.run(schema.drop)
  }
}
