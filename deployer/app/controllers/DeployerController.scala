package controllers

import java.io.File
import java.nio.file.{Files, Path, Paths}
import javax.inject.Inject

import com.toscaruntime.constant.{DeployerConstant, ToscaInterfaceConstant}
import com.toscaruntime.exception.BadUsageException
import com.toscaruntime.exception.deployment.execution.RunningExecutionNotFound
import com.toscaruntime.exception.deployment.workflow.InvalidWorkflowArgumentException
import com.toscaruntime.rest.model._
import com.toscaruntime.runtime.{Deployer, PluginConfiguration, ProviderConfiguration, TargetConfiguration}
import com.toscaruntime.sdk.Deployment
import com.toscaruntime.util.{JavaScalaConversionUtil, ScalaFileUtil}
import com.typesafe.config.ConfigFactory
import com.typesafe.config.impl.ConfigImpl
import dao.DeploymentDAO
import models.ExecutionEntity
import org.joda.time.DateTime
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.mvc.{Action, BodyParsers, Controller}

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.language.postfixOps

class DeployerController @Inject()(deploymentDAO: DeploymentDAO) extends Controller with Logging {

  lazy val recipePath = Paths.get(play.Play.application().configuration().getString("com.toscaruntime.deployment.recipeDir"))

  lazy val deploymentInputsPath = {
    val inputPath = Paths.get(play.Play.application().configuration().getString("com.toscaruntime.deployment.inputFile"))
    if (Files.isRegularFile(inputPath)) Some(inputPath) else None
  }

  lazy val bootstrapContextPath = {
    val bootstrapContextPath = Paths.get(play.Play.application().configuration().getString("com.toscaruntime.bootstrapContext"))
    if (Files.isRegularFile(bootstrapContextPath)) Some(bootstrapContextPath) else None
  }

  lazy val deploymentConfiguration = ConfigFactory.parseFile(
    new File(play.Play.application().configuration().getString("com.toscaruntime.deployment.confFile"))
  ).resolveWith(play.Play.application().configuration().underlying()).resolveWith(ConfigImpl.systemPropertiesAsConfig())

  lazy val deploymentName = deploymentConfiguration.getString(DeployerConstant.DEPLOYMENT_NAME_KEY)

  lazy val bootstrap = deploymentConfiguration.getBoolean(DeployerConstant.BOOTSTRAP_KEY)

  private def loadTarget(targetPath: Path) = {
    val providerConfig = ConfigFactory.parseFile(targetPath.toFile).resolveWith(play.Play.application().configuration().underlying()).resolveWith(ConfigImpl.systemPropertiesAsConfig())
    TargetConfiguration(targetPath.getFileName.toString, providerConfig.root().unwrapped())
  }

  private def loadProvider(providerPath: Path) = ProviderConfiguration(providerPath.getFileName.toString, ScalaFileUtil.listDirectories(providerPath).map(loadTarget))

  private def loadPlugin(providerPath: Path) = PluginConfiguration(providerPath.getFileName.toString, ScalaFileUtil.listDirectories(providerPath).map(loadTarget))

  lazy val providerConfigurations = {
    ScalaFileUtil.listDirectories(Paths.get(play.Play.application().configuration().getString("com.toscaruntime.provider.confDir"))).map(loadProvider)
  }

  lazy val pluginConfigurations = {
    ScalaFileUtil.listDirectories(Paths.get(play.Play.application().configuration().getString("com.toscaruntime.plugin.confDir"))).map(loadPlugin)
  }

  lazy val deployment: Deployment = {
    if (!deploymentDAO.isSchemaCreated) {
      deploymentDAO.createSchema()
    }
    Deployer.createDeployment(deploymentName, recipePath, deploymentInputsPath, providerConfigurations, pluginConfigurations, bootstrapContextPath, bootstrap, play.Play.application().classloader(), deploymentDAO)
  }

  private def createExecution(workflowExecutionRequest: WorkflowExecutionRequest) = {
    val workflowExecution = workflowExecutionRequest.workflowId match {
      case "install" => deployment.install()
      case "uninstall" => deployment.uninstall()
      case "teardown_infrastructure" => deployment.teardown()
      case "execute_node_operation" =>
        if (workflowExecutionRequest.inputs.get("operation_name").isEmpty) throw new InvalidWorkflowArgumentException("Missing 'operation_name' input for execute_node_operation workflow")
        deployment.executeNodeOperation(
          workflowExecutionRequest.inputs.getOrElse("node_id", null).asInstanceOf[String],
          workflowExecutionRequest.inputs.getOrElse("instance_id", null).asInstanceOf[String],
          workflowExecutionRequest.inputs.getOrElse("interface_name", ToscaInterfaceConstant.NODE_STANDARD_INTERFACE).asInstanceOf[String],
          workflowExecutionRequest.inputs("operation_name").asInstanceOf[String],
          JavaScalaConversionUtil.toJavaMap(workflowExecutionRequest.inputs.getOrElse("inputs", Map.empty).asInstanceOf[Map[String, Any]]),
          workflowExecutionRequest.inputs.getOrElse("transient", false).asInstanceOf[Boolean]
        )
      case "execute_relationship_operation" =>
        if (workflowExecutionRequest.inputs.get("relationship_type").isEmpty) throw new InvalidWorkflowArgumentException("Missing 'relationship_type' input for execute_relationship_operation workflow")
        if (workflowExecutionRequest.inputs.get("operation_name").isEmpty) throw new InvalidWorkflowArgumentException("Missing 'operation_name' input for execute_relationship_operation workflow")
        deployment.executeRelationshipOperation(
          workflowExecutionRequest.inputs.getOrElse("source_node_id", null).asInstanceOf[String],
          workflowExecutionRequest.inputs.getOrElse("source_instance_id", null).asInstanceOf[String],
          workflowExecutionRequest.inputs.getOrElse("target_node_id", null).asInstanceOf[String],
          workflowExecutionRequest.inputs.getOrElse("target_instance_id", null).asInstanceOf[String],
          workflowExecutionRequest.inputs("relationship_type").asInstanceOf[String],
          workflowExecutionRequest.inputs.getOrElse("interface_name", ToscaInterfaceConstant.RELATIONSHIP_STANDARD_INTERFACE).asInstanceOf[String],
          workflowExecutionRequest.inputs("operation_name").asInstanceOf[String],
          JavaScalaConversionUtil.toJavaMap(workflowExecutionRequest.inputs.getOrElse("inputs", Map.empty).asInstanceOf[Map[String, Any]]),
          workflowExecutionRequest.inputs.getOrElse("transient", false).asInstanceOf[Boolean]
        )
      case "scale" =>
        if (workflowExecutionRequest.inputs.get("node_id").isEmpty) throw new InvalidWorkflowArgumentException("Missing 'node_id' input for scale workflow")
        if (workflowExecutionRequest.inputs.get("new_instances_count").isEmpty) throw new InvalidWorkflowArgumentException("Missing 'new_instances_count' input for scale workflow")
        val nodeId = workflowExecutionRequest.inputs("node_id")
        if (!nodeId.isInstanceOf[String]) throw new InvalidWorkflowArgumentException("'node_id' input is not of type string for scale workflow")
        val newInstancesCount = workflowExecutionRequest.inputs("new_instances_count")
        if (!newInstancesCount.isInstanceOf[BigDecimal]) throw new InvalidWorkflowArgumentException("'new_instances_count' input is not of type integer for scale workflow")
        deployment.scale(nodeId.asInstanceOf[String], newInstancesCount.asInstanceOf[BigDecimal].toInt)
      case _ => throw new InvalidWorkflowArgumentException(s"Workflow ${workflowExecutionRequest.workflowId} is not supported on this deployment")
    }
    workflowExecution
  }

  def execute() = Action.async(BodyParsers.parse.json) { implicit request =>
    val requestBody = request.body.validate[WorkflowExecutionRequest]
    requestBody.fold(
      errors => {
        Future(BadRequest(s"Invalid workflow execution request $errors"))
      },
      workflowExecutionRequest => {
        val isTransientExecution = workflowExecutionRequest.inputs.getOrElse("transient", false).asInstanceOf[Boolean]
        if (isTransientExecution) {
          try {
            val workflowExecution = createExecution(workflowExecutionRequest)
            deployment.run(workflowExecution)
            log.info(s"Transient execution has been started for ${workflowExecutionRequest.workflowId} workflow")
            Future(Ok(s"Transient execution has been started for ${workflowExecutionRequest.workflowId} workflow"))
          } catch {
            case e: BadUsageException =>
              log.error(s"Bad transient workflow execution request", e)
              Future(BadRequest(e.getMessage))
            case e: Exception =>
              log.error(s"Internal server error while serving transient workflow execution request", e)
              Future(InternalServerError(s"Failed to launch transient ${workflowExecutionRequest.workflowId} workflow, error is ${e.getMessage}"))
          }
        } else {
          deploymentDAO.startExecution(workflowExecutionRequest.workflowId, workflowExecutionRequest.inputs).map { executionId =>
            log.info(s"Execution has been created for ${workflowExecutionRequest.workflowId} workflow with id $executionId")
            try {
              val workflowExecution = createExecution(workflowExecutionRequest)
              deployment.run(workflowExecution)
              (executionId, workflowExecution)
            } catch {
              case e: Exception =>
                deploymentDAO.failRunningExecution(e.getMessage)
                throw e
            }
          }.map {
            case (executionId, workflowExecution) =>
              log.info(s"Execution $executionId has been started for ${workflowExecutionRequest.workflowId} workflow")
              Ok(s"Execution $executionId has been started for ${workflowExecutionRequest.workflowId} workflow")
          }.recoverWith {
            case e: BadUsageException =>
              log.error(s"Bad workflow execution request", e)
              Future(BadRequest(e.getMessage))
            case e: Exception =>
              log.error(s"Internal server error while serving workflow execution request", e)
              Future(InternalServerError(s"Failed to launch ${workflowExecutionRequest.workflowId} workflow, error is ${e.getMessage}"))
          }
        }
      }
    )
  }

  def cancel(force: Boolean = false) = Action { implicit request =>
    try {
      deployment.cancel(force)
      Ok(s"Execution has been cancelled")
    } catch {
      case e: RunningExecutionNotFound =>
        deploymentDAO.cancelRunningExecution()
        BadRequest("No running execution is found to be cancelled")
    }
  }

  def resume() = Action.async { implicit request =>
    try {
      deployment.resume()
      deploymentDAO.resumeRunningExecution().map { rowHit =>
        if (rowHit == 0) {
          log.error("No running execution found in persistence to resume")
        }
        Ok("Execution has been resumed")
      }
    } catch {
      case e: RunningExecutionNotFound => Future(BadRequest("No running execution is found to be resumed"))
    }
  }

  def stop(force: Boolean = false) = Action { implicit request =>
    try {
      deployment.stop(force)
      Ok("Execution has been stopped")
    } catch {
      case e: RunningExecutionNotFound => BadRequest("No running execution is found to be stopped")
    }
  }

  def updateRecipe() = Action { implicit request =>
    try {
      deployment.updateRecipe()
      Ok("Recipe has been updated")
    } catch {
      case e: RunningExecutionNotFound => BadRequest("No running execution is found to be stopped")
    }
  }

  /**
    * Convert from java deployment to deployment information to return back to rest client
    *
    * @param deployment the managed deployment
    * @return current deployment information
    */
  def fromDeployment(name: String, deployment: Deployment) = {
    val nodes = deployment.getNodes.asScala.map { node =>
      val instances = node.getInstances.asScala.map { instance =>
        val instanceAttributes = JavaScalaConversionUtil.toScalaMap(instance.getAttributes)
        InstanceDTO(instance.getId, instance.getState, instanceAttributes)
      }.toList
      val nodeProperties = JavaScalaConversionUtil.toScalaMap(node.getProperties)
      NodeDTO(node.getId, nodeProperties, instances)
    }.toList
    val relationships = deployment.getRelationshipNodes.asScala.map { relationshipNode =>
      val relationshipInstances = relationshipNode.getRelationshipInstances.asScala.map { relationshipInstance =>
        val relationshipInstanceAttributes = JavaScalaConversionUtil.toScalaMap(relationshipInstance.getAttributes)
        RelationshipInstanceDTO(relationshipInstance.getSource.getId, relationshipInstance.getTarget.getId, relationshipInstance.getState, relationshipInstanceAttributes)
      }.toList
      val relationshipNodeProperties = JavaScalaConversionUtil.toScalaMap(relationshipNode.getProperties)
      RelationshipNodeDTO(relationshipNode.getSourceNodeId, relationshipNode.getTargetNodeId, relationshipNode.getRelationshipName, relationshipNodeProperties, relationshipInstances)
    }.toList
    val executions = deploymentDAO.listExecutions().flatMap { executions =>
      Future.sequence(executions.map {
        case execution: ExecutionEntity =>
          deploymentDAO.getExecutionInputs(execution.id).map { inputs =>
            ExecutionDTO(execution.id, execution.workflowId, new DateTime(execution.startTime.getTime), execution.endTime.map(endTime => new DateTime(endTime.getTime)), execution.error, execution.status, inputs)
          }
      })
    }
    executions.map { exes =>
      DeploymentDTO(name, nodes, relationships, JavaScalaConversionUtil.toScalaMap(deployment.getOutputs), exes.toList)
    }
  }

  def getDeploymentInformation = Action.async { implicit request =>
    fromDeployment(deploymentName, deployment).map(d => Ok(Json.toJson(RestResponse.success[DeploymentDTO](Some(d)))))
  }
}
