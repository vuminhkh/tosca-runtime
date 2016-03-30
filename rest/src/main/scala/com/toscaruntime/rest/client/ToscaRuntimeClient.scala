package com.toscaruntime.rest.client

import java.io.PrintStream
import java.net.ConnectException
import java.nio.file.Path

import akka.pattern._
import com.ning.http.client.AsyncHttpClientConfig
import com.toscaruntime.exception.UnexpectedException
import com.toscaruntime.exception.client._
import com.toscaruntime.rest.model._
import com.typesafe.scalalogging.LazyLogging
import play.api.http.Status
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.WSResponse
import play.api.libs.ws.ning.{NingAsyncHttpClientConfigBuilder, NingWSClient}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

/**
  * Entry point to use toscaruntime service. Offer all available toscaruntime command.
  *
  * @author Minh Khang VU
  */
class ToscaRuntimeClient(url: String, certPath: String) extends LazyLogging {

  val system = akka.actor.ActorSystem("system")

  private val daemonClient: DockerDaemonClient = new DockerDaemonClient(url, certPath)

  /**
    * When proxy url is not defined, we are in bootstrap mode on a non toscaruntime docker daemon
    */
  private var proxyURLOpt = daemonClient.getProxyURL

  def switchConnection(url: String, certPath: String) = {
    daemonClient.setDockerClient(url, certPath)
    proxyURLOpt = daemonClient.getProxyURL
    logger.info(s"New proxy url detected [${proxyURLOpt.getOrElse("none")}]")
  }

  val wsClient = {
    val config = new NingAsyncHttpClientConfigBuilder().build
    val builder = new AsyncHttpClientConfig.Builder(config)
      .setReadTimeout(Integer.MAX_VALUE)
      .setRequestTimeout(Integer.MAX_VALUE)
    new NingWSClient(builder.build)
  }

  private def getDeploymentAgentURL(deploymentId: String) = {
    proxyURLOpt.map(_ + "/deployments/" + deploymentId).getOrElse(daemonClient.getBootstrapAgentURL(deploymentId).getOrElse(throw new DaemonResourcesNotFoundException("Deployment agent " + deploymentId + " is not running")))
  }

  def listDeploymentAgents() = {
    proxyURLOpt.map { proxyURL =>
      wsClient.url(proxyURL + "/deployments").get().map(_.json.as[RestResponse[List[DeploymentInfoDTO]]].data.get)
    }.getOrElse(Future(daemonClient.listDeploymentAgents().values.toList))
  }

  def getDeploymentAgentInfo(deploymentId: String) = {
    val url = getDeploymentAgentURL(deploymentId)
    wsClient.url(url).get().map { response =>
      if (response.status == 200) {
        response.json.as[RestResponse[DeploymentDTO]].data.get
      } else throw new AgentNotRunningException(s"Agent is down and respond with status ${response.status} and body ${response.body}")
    }
  }

  private def handleWSResponse(response: WSResponse): String = {
    response.status match {
      case Status.OK => response.body
      case Status.BAD_REQUEST => throw new BadRequestException(response.body)
      case _ => throw new ServerFailureException(s"Encountered unexpected exception :\n ${response.body}")
    }
  }

  def executeInstallWorkflow(deploymentId: String) = {
    wsClient
      .url(getDeploymentAgentURL(deploymentId) + "/executions")
      .post(Json.toJson(WorkflowExecutionRequest("install", Map.empty)))
      .map(handleWSResponse)
  }

  def executeUninstallWorkflow(deploymentId: String) = {
    wsClient
      .url(getDeploymentAgentURL(deploymentId) + "/executions")
      .post(Json.toJson(WorkflowExecutionRequest("uninstall", Map.empty)))
      .map(handleWSResponse)
  }

  def executeTeardownInfrastructureWorkflow(deploymentId: String) = {
    wsClient
      .url(getDeploymentAgentURL(deploymentId) + "/executions")
      .post(Json.toJson(WorkflowExecutionRequest("teardown_infrastructure", Map.empty)))
      .map(handleWSResponse)
  }

  def executeScaleWorkflow(deploymentId: String, nodeName: String, instancesCount: Int) = {
    wsClient
      .url(getDeploymentAgentURL(deploymentId) + "/executions")
      .post(Json.toJson(WorkflowExecutionRequest("scale", Map("node_id" -> nodeName, "new_instances_count" -> instancesCount))))
      .map(handleWSResponse)
  }

  def cancelExecution(deploymentId: String, force: Boolean) = {
    wsClient
      .url(getDeploymentAgentURL(deploymentId) + "/executions/cancel")
      .withQueryString("force" -> force.toString)
      .post("")
      .map(handleWSResponse)
  }

  def stopExecution(deploymentId: String, force: Boolean) = {
    wsClient
      .url(getDeploymentAgentURL(deploymentId) + "/executions/stop")
      .withQueryString("force" -> force.toString)
      .post("")
      .map(handleWSResponse)
  }

  def resumeExecution(deploymentId: String) = {
    wsClient
      .url(getDeploymentAgentURL(deploymentId) + "/executions/resume")
      .post("")
      .map(handleWSResponse)
  }

  def waitForRunningExecutionToEnd(deploymentId: String): Future[DeploymentDTO] = {
    getDeploymentAgentInfo(deploymentId).flatMap { deploymentInfo =>
      if (deploymentInfo.executions.head.error.nonEmpty) {
        Future.failed(new WorkflowExecutionFailureException(s"Execution of workflow failed ${deploymentInfo.executions.head.error.get}"))
      } else if (deploymentInfo.executions.head.endTime.isEmpty) {
        after(2 seconds, system.scheduler)(waitForRunningExecutionToEnd(deploymentId))
      } else {
        Future(deploymentInfo)
      }
    }
  }

  def waitForRunningExecutionToReachStatus(deploymentId: String, status: String): Future[DeploymentDTO] = {
    getDeploymentAgentInfo(deploymentId).flatMap { deploymentInfo =>
      if (deploymentInfo.executions.head.status.nonEmpty) {
        if (deploymentInfo.executions.head.status == status) {
          Future(deploymentInfo)
        } else {
          Future.failed(new WorkflowExecutionFailureException(s"Expected to have $status as status but instead had ${deploymentInfo.executions.head.status}"))
        }
      } else {
        after(2 seconds, system.scheduler)(waitForRunningExecutionToEnd(deploymentId))
      }
    }
  }

  def waitForBootstrapToFinish(provider: String, target: String) = waitForRunningExecutionToEnd(generateDeploymentIdForBootstrap(provider, target))

  def bootstrap(provider: String, target: String): Future[DeploymentDTO] = {
    executeInstallWorkflow(generateDeploymentIdForBootstrap(provider, target)).flatMap { response =>
      logger.info(s"Install workflow launched for bootstrap $response")
      waitForBootstrapToFinish(provider, target).flatMap { bootstrapInfo =>
        val proxyUrl = bootstrapInfo.outputs.get("public_proxy_url").get + "/context"
        saveBootstrapContext(proxyUrl, JSONMapStringAnyFormat.convertMapToJsValue(bootstrapInfo.outputs), bootstrapInfo)
      }
    }
  }

  def getBootstrapContext: Future[Map[String, Any]] = {
    proxyURLOpt.map { proxyUrl =>
      wsClient.url(proxyUrl + "/context").get().map { response =>
        JSONMapStringAnyFormat.convertJsObjectToMap(response.json.as[RestResponse[JsObject]].data.get)
      }
    }.getOrElse(Future(Map.empty))
  }

  def getBootstrapAgentInfo(provider: String, target: String) = {
    getDeploymentAgentInfo(generateDeploymentIdForBootstrap(provider, target))
  }

  def updateBootstrapContext(context: Map[String, String]) = {
    val proxyUrl = proxyURLOpt.getOrElse(throw new UnexpectedException("Try to update bootstrap context but proxy url not configured"))
    wsClient.url(proxyUrl + "/context").post(JSONMapStringAnyFormat.convertMapToJsValue(context))
  }

  private def saveBootstrapContext[T](proxyUrl: String, context: JsObject, result: T): Future[T] = {
    wsClient.url(proxyUrl).post(context).map(_ => result).recoverWith {
      case e: ConnectException =>
        logger.info(s"Proxy is not yet up at [$proxyUrl] retry in 2 seconds")
        after(2 seconds, system.scheduler)(saveBootstrapContext(proxyUrl, context, result))
    }
  }

  def teardown(provider: String, target: String) = {
    executeUninstallWorkflow(generateDeploymentIdForBootstrap(provider, target)).flatMap { response =>
      logger.info(s"Install workflow launched for bootstrap $response")
      waitForBootstrapToFinish(provider, target)
    }
  }

  def createDeploymentImage(deploymentId: String, recipePath: Path, inputsPath: Option[Path], providerConfigPath: Path, bootstrap: Option[Boolean]) = {
    // TODO asynchronous
    val bootstrapContext = Await.result(getBootstrapContext, 365 days)
    // By default if the proxy url is empty then we are not in a bootstrap context, then it means we are bootstrapping
    daemonClient.createAgentImage(deploymentId, bootstrap.getOrElse(proxyURLOpt.isEmpty), recipePath, inputsPath, providerConfigPath, bootstrapContext)
  }

  def createBootstrapImage(provider: String, recipePath: Path, inputsPath: Option[Path], providerConfigPath: Path, target: String) = {
    val bootstrapContext = Await.result(getBootstrapContext, 365 days)
    daemonClient.createAgentImage(generateDeploymentIdForBootstrap(provider, target), bootstrap = true, recipePath, inputsPath, providerConfigPath, bootstrapContext)
  }

  def listDeploymentImages() = {
    daemonClient.listDeploymentImages()
  }

  def cleanDanglingImages() = {
    daemonClient.cleanDanglingImages()
  }

  def deleteDeploymentImage(deploymentId: String) = {
    daemonClient.deleteDeploymentImage(deploymentId)
  }

  def deleteBootstrapImage(provider: String, target: String) = {
    daemonClient.deleteDeploymentImage(generateDeploymentIdForBootstrap(provider, target))
  }

  def createDeploymentAgent(deploymentId: String) = {
    daemonClient.createDeploymentAgent(deploymentId, Await.result(getBootstrapContext, 365 days))
  }

  def createBootstrapAgent(provider: String, target: String) = {
    daemonClient.createBootstrapAgent(provider, target)
  }

  private def generateDeploymentIdForBootstrap(provider: String, target: String) = {
    daemonClient.generateDeploymentIdForBootstrap(provider, target)
  }

  def startDeploymentAgent(deploymentId: String) = {
    daemonClient.start(deploymentId)
  }

  def stopDeploymentAgent(deploymentId: String) = {
    daemonClient.stop(deploymentId)
  }

  def restartDeploymentAgent(deploymentId: String) = {
    daemonClient.restart(deploymentId)
  }

  def updateDeploymentAgentRecipe(deploymentId: String, recipePath: Path) = {
    daemonClient.updateAgentRecipe(deploymentId, recipePath)
    wsClient
      .url(getDeploymentAgentURL(deploymentId) + "/recipe/update")
      .post("")
      .map(handleWSResponse)
  }

  def deleteDeploymentAgent(deploymentId: String) = {
    daemonClient.delete(deploymentId)
  }

  def deleteBootstrapAgent(provider: String, target: String) = {
    daemonClient.delete(generateDeploymentIdForBootstrap(provider, target))
  }

  def tailLog(deploymentId: String, output: PrintStream) = {
    daemonClient.tailLog(deploymentId, output)
  }

  def tailBootstrapLog(provider: String, target: String, output: PrintStream) = {
    daemonClient.tailLog(generateDeploymentIdForBootstrap(provider, target), output)
  }

  def tailContainerLog(containerId: String, output: PrintStream) = {
    daemonClient.tailContainerLog(containerId, output)
  }

  def dockerVersion = {
    daemonClient.version
  }
}
