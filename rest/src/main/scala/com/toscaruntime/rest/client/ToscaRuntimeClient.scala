package com.toscaruntime.rest.client

import java.io.PrintStream
import java.net.ConnectException
import java.nio.file.Path

import akka.pattern._
import com.ning.http.client.AsyncHttpClientConfig
import com.toscaruntime.exception.{BadConfigurationException, ResourcesNotFoundException}
import com.toscaruntime.rest.model.{DeploymentDetails, DeploymentInfo, RestResponse}
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json.{JsObject, JsString, JsValue}
import play.api.libs.ws.ning.{NingAsyncHttpClientConfigBuilder, NingWSClient}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * Entry point to use toscaruntime service. Offer all available toscaruntime command.
  *
  * @author Minh Khang VU
  */
class ToscaRuntimeClient(url: String, certPath: String) extends LazyLogging {

  private val system = akka.actor.ActorSystem("system")

  private val daemonClient: DockerDaemonClient = new DockerDaemonClient(url, certPath)

  /**
    * When proxy url is not defined, we are in bootstrap mode on a non toscaruntime docker daemon
    */
  private var proxyURLOpt = daemonClient.getProxyURL

  def switchConnection(url: String, certPath: String) = {
    daemonClient.setDockerClient(url, certPath)
    proxyURLOpt = daemonClient.getProxyURL
    logger.info("New proxy url detected " + proxyURLOpt.getOrElse("none"))
  }

  private val wsClient = {
    val config = new NingAsyncHttpClientConfigBuilder().build
    val builder = new AsyncHttpClientConfig.Builder(config)
      .setReadTimeout(Integer.MAX_VALUE)
      .setRequestTimeout(Integer.MAX_VALUE)
    new NingWSClient(builder.build)
  }

  private def getDeploymentAgentURL(deploymentId: String) = {
    proxyURLOpt.map(_ + "/deployments/" + deploymentId).getOrElse(daemonClient.getBootstrapAgentURL(deploymentId).getOrElse(throw new ResourcesNotFoundException("Deployment agent " + deploymentId + " is not running")))
  }

  def listDeploymentAgents() = {
    proxyURLOpt.map { proxyURL =>
      wsClient.url(proxyURL + "/deployments").get().map(_.json.as[RestResponse[List[DeploymentInfo]]].data.get)
    }.getOrElse(Future(daemonClient.listDeploymentAgents().values.toList))
  }

  def getDeploymentAgentInfo(deploymentId: String) = {
    val url = getDeploymentAgentURL(deploymentId)
    wsClient.url(url).get().map { response =>
      response.json.as[RestResponse[DeploymentDetails]].data.get
    }
  }

  def deploy(deploymentId: String) = {
    val url = getDeploymentAgentURL(deploymentId)
    wsClient.url(url).post("").map {
      case response => response.json.as[RestResponse[DeploymentDetails]].data.get
    }
  }

  def undeploy(deploymentId: String) = {
    val url = getDeploymentAgentURL(deploymentId)
    wsClient.url(url).delete().map {
      case response => response.json.as[RestResponse[DeploymentDetails]].data.get
    }
  }

  private def toJson(map: Map[String, String]) = {
    JsObject(map.map {
      case (key: String, value: String) => (key, JsString(value))
    })
  }

  private def fromJson(jsObject: JsObject) = {
    jsObject.fields.map {
      case (key: String, value: JsValue) => (key, value.as[JsString].value)
    }.toMap
  }

  def bootstrap(provider: String, target: String) = {
    deploy(generateDeploymentIdForBootstrap(provider, target)).flatMap {
      case response =>
        val proxyUrl = response.outputs.get("public_proxy_url").get + "/context"
        saveBootstrapContext(proxyUrl, toJson(response.outputs), response)
    }
  }

  def getBootstrapContext: Future[Map[String, String]] = {
    proxyURLOpt.map { proxyUrl =>
      wsClient.url(proxyUrl + "/context").get().map { response =>
        fromJson(response.json.as[RestResponse[JsObject]].data.get)
      }
    }.getOrElse(Future(Map.empty))
  }

  def getBootstrapAgentInfo(provider: String, target: String) = {
    getDeploymentAgentInfo(generateDeploymentIdForBootstrap(provider, target))
  }

  def updateBootstrapContext(context: Map[String, String]) = {
    val proxyUrl = proxyURLOpt.getOrElse(throw new BadConfigurationException("Try to update bootstrap context but proxy url not configured"))
    wsClient.url(proxyUrl + "/context").post(toJson(context))
  }

  private def saveBootstrapContext[T](proxyUrl: String, context: JsObject, result: T): Future[T] = {
    wsClient.url(proxyUrl).post(context).map(_ => result).recoverWith {
      case e: ConnectException =>
        logger.info("Proxy is not yet up at " + proxyUrl + " retry in 2 seconds")
        after(2 seconds, system.scheduler)(saveBootstrapContext(proxyUrl, context, result))
    }
  }

  def teardown(provider: String, target: String) = {
    undeploy(generateDeploymentIdForBootstrap(provider, target))
  }

  def createDeploymentImage(deploymentId: String, recipePath: Path, inputsPath: Option[Path], providerConfigPath: Path) = {
    // TODO asynchronous
    val bootstrapContext = Await.result(getBootstrapContext, 365 days)
    daemonClient.createAgentImage(deploymentId, bootstrap = false, recipePath, inputsPath, providerConfigPath, bootstrapContext)
  }

  def createBootstrapImage(provider: String, recipePath: Path, inputsPath: Option[Path], providerConfigPath: Path, target: String) = {
    val bootstrapContext = Await.result(getBootstrapContext, 365 days)
    daemonClient.createAgentImage(generateDeploymentIdForBootstrap(provider, target), bootstrap = true, recipePath, inputsPath, providerConfigPath, bootstrapContext)
  }

  def createImage(deploymentPath: Path) = {
    val bootstrapContext = Await.result(getBootstrapContext, 365 days)
    daemonClient.createAgentImage(deploymentPath, bootstrapContext)
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
