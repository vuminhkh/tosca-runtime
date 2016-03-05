package controllers

import java.io.File
import java.nio.file.{Files, Paths}
import javax.inject.Inject

import com.toscaruntime.constant.DeployerConstant
import com.toscaruntime.exception.BadUsageException
import com.toscaruntime.exception.deployment.execution.RunningExecutionNotFound
import com.toscaruntime.exception.deployment.workflow.InvalidWorkflowException
import com.toscaruntime.rest.model._
import com.toscaruntime.runtime.Deployer
import com.toscaruntime.sdk.Deployment
import com.toscaruntime.sdk.workflow.WorkflowExecutionListener
import com.toscaruntime.util.JavaScalaConversionUtil
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

  lazy val providerConfiguration = {
    val providerConfig = ConfigFactory.parseFile(
      new File(play.Play.application().configuration().getString("com.toscaruntime.provider.confFile"))
    ).resolveWith(play.Play.application().configuration().underlying()).resolveWith(ConfigImpl.systemPropertiesAsConfig())
    providerConfig.entrySet().asScala.map { entry =>
      (entry.getKey, entry.getValue.unwrapped().asInstanceOf[String])
    }.toMap
  }

  lazy val deployment: Deployment = {
    if (!deploymentDAO.isSchemaCreated) {
      deploymentDAO.createSchema()
    }
    Deployer.createDeployment(deploymentName, recipePath, deploymentInputsPath, providerConfiguration, bootstrapContextPath, bootstrap, play.Play.application().classloader(), deploymentDAO)
  }

  def execute() = Action.async(BodyParsers.parse.json) { implicit request =>
    val requestBody = request.body.validate[WorkflowExecutionRequest]
    requestBody.fold(
      errors => {
        Future(BadRequest(s"Invalid workflow execution request $errors"))
      },
      workflowExecutionRequest => {
        deploymentDAO.startExecution(workflowExecutionRequest.workflowId, workflowExecutionRequest.inputs).map { executionId =>
          log.info(s"Execution has been created for ${workflowExecutionRequest.workflowId} workflow with id $executionId")
          try {
            val workflowExecution = workflowExecutionRequest.workflowId match {
              case "install" => deployment.install()
              case "uninstall" => deployment.uninstall()
              case "scale" =>
                if (workflowExecutionRequest.inputs.get("nodeId").isEmpty) throw new InvalidWorkflowException("Missing 'nodeId' input for scale workflow")
                if (workflowExecutionRequest.inputs.get("newInstancesCount").isEmpty) throw new InvalidWorkflowException("Missing 'newInstancesCount' input for scale workflow")
                val nodeId = workflowExecutionRequest.inputs("nodeId")
                if (!nodeId.isInstanceOf[String]) throw new InvalidWorkflowException("'nodeId' input is not of type string for scale workflow")
                val newInstancesCount = workflowExecutionRequest.inputs("newInstancesCount")
                if (!newInstancesCount.isInstanceOf[BigDecimal]) throw new InvalidWorkflowException("'newInstancesCount' input is not of type integer for scale workflow")
                deployment.scale(nodeId.asInstanceOf[String], newInstancesCount.asInstanceOf[BigDecimal].toInt)
              case _ => throw new InvalidWorkflowException(s"Workflow ${workflowExecutionRequest.workflowId} is not supported on this deployment")
            }
            (executionId, workflowExecution)
          } catch {
            case e: Exception =>
              deploymentDAO.failRunningExecution(e.getMessage)
              throw e
          }
        }.map {
          case (executionId, workflowExecution) =>
            log.info(s"Execution $executionId has been started for ${workflowExecutionRequest.workflowId} workflow")
            workflowExecution.addListener(new WorkflowExecutionListener {
              override def onFinish(): Unit = deploymentDAO.finishRunningExecution()

              override def onFailure(errors: java.util.List[Throwable]): Unit = {
                val errorMessages = errors.asScala.map(e => {
                  log.info(s"Execution $executionId has been stopped for ${workflowExecutionRequest.workflowId} workflow due to error", e)
                  e.getMessage
                })
                deploymentDAO.stopExecution(Some(errorMessages.mkString(",")))
              }
            })
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
    )
  }

  def cancel() = Action.async { implicit request =>
    deploymentDAO.cancelRunningExecution().map { rowsHit =>
      if (rowsHit == 0) {
        BadRequest(s"No running execution exists for this deployment")
      } else {
        deployment.cancel()
        Ok(s"Execution has been cancelled")
      }
    }
  }

  def resume() = Action { implicit request =>
    try {
      deployment.resume()
      Ok("Execution of the current workflow has been resumed")
    } catch {
      case e: RunningExecutionNotFound => BadRequest("No running execution is found to be resumed")
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
      RelationshipNodeDTO(relationshipNode.getSourceNodeId, relationshipNode.getTargetNodeId, relationshipNodeProperties, relationshipInstances)
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
