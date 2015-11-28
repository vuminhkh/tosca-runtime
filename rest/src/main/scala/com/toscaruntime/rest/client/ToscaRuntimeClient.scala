package com.toscaruntime.rest.client

import java.net.ConnectException
import java.nio.file.Path

import akka.pattern._
import com.ning.http.client.AsyncHttpClientConfig
import com.toscaruntime.rest.model.{DeploymentDetails, DeploymentInfo, RestResponse}
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.json.{JsObject, JsString}
import play.api.libs.ws.ning.{NingAsyncHttpClientConfigBuilder, NingWSClient}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * A holder for tosca runtime client
  *
  * @author Minh Khang VU
  */
class ToscaRuntimeClient(var daemonClient: DockerDaemonClient) extends LazyLogging {

  private val system = akka.actor.ActorSystem("system")
  /**
    * When proxy url is not defined, we are in bootstrap mode on a non toscaruntime docker daemon
    */
  private var proxyURLOpt = daemonClient.getProxyURL

  def setDaemonClient(url: String, certPath: String) = {
    daemonClient.setDockerClient(url, certPath)
    proxyURLOpt = daemonClient.getProxyURL
  }

  private val wsClient = {
    val config = new NingAsyncHttpClientConfigBuilder().build
    val builder = new AsyncHttpClientConfig.Builder(config)
      .setReadTimeout(Integer.MAX_VALUE)
      .setRequestTimeout(Integer.MAX_VALUE)
    new NingWSClient(builder.build)
  }

  private def getURL(deploymentId: String) = {
    proxyURLOpt.map(_ + "/deployments/" + deploymentId).getOrElse(daemonClient.getBootstrapAgentURL(deploymentId).getOrElse(throw new RuntimeException("Deployment agent " + deploymentId + " is not running")))
  }

  def listDeployments() = {
    proxyURLOpt.map { proxyURL =>
      wsClient.url(proxyURL + "/deployments").get().map(_.json.as[RestResponse[List[DeploymentInfo]]].data.get)
    }.getOrElse(Future(daemonClient.listDeployments().values.toList))
  }

  def getDeploymentInformation(deploymentId: String) = {
    val url = getURL(deploymentId)
    wsClient.url(url).get().map { response =>
      response.json.as[RestResponse[DeploymentDetails]].data.get
    }
  }

  def deploy(deploymentId: String) = {
    val url = getURL(deploymentId)
    wsClient.url(url).post("").map {
      case response => response.json.as[RestResponse[DeploymentDetails]].data.get
    }
  }

  def undeploy(deploymentId: String) = {
    val url = getURL(deploymentId)
    wsClient.url(url).delete()
  }

  def bootstrap(provider: String, target: String = "default") = {
    deploy(generateDeploymentIdForBootstrap(provider, target)).flatMap {
      case response =>
        val context = JsObject(response.outputs.map {
          case (key: String, value: String) => (key, JsString(value))
        })
        saveBootstrapContext(response, context)
    }
  }

  private def saveBootstrapContext(details: DeploymentDetails, context: JsObject): Future[DeploymentDetails] = {
    val proxyUrl = details.outputs.get("proxy_url").get + "/context"
    wsClient.url(proxyUrl).post(context).map(_ => details).recoverWith {
      case e: ConnectException =>
        logger.info("Proxy is not yet up at " + proxyUrl + " retry in 2 seconds")
        after(2 seconds, system.scheduler)(saveBootstrapContext(details, context))
    }
  }

  def teardown(provider: String, target: String = "default") = {
    undeploy(generateDeploymentIdForBootstrap(provider, target))
  }

  def createDeploymentImage(deploymentId: String, recipePath: Path, inputsPath: Option[Path], providerConfigPath: Path) = {
    daemonClient.createAgentImage(deploymentId, bootstrap = false, recipePath, inputsPath, providerConfigPath)
  }

  def createBootstrapImage(provider: String, recipePath: Path, inputsPath: Option[Path], providerConfigPath: Path, target: String = "default") = {
    daemonClient.createAgentImage(daemonClient.generateDeploymentIdForBootstrap(provider, target), bootstrap = true, recipePath, inputsPath, providerConfigPath)
  }

  def createImage(deploymentPath: Path) = {
    daemonClient.createAgentImage(deploymentPath)
  }

  def listImages() = {
    daemonClient.listImages()
  }

  def cleanDanglingImages() = {
    daemonClient.cleanDanglingImages()
  }

  def deleteImage(deploymentId: String) = {
    daemonClient.deleteImage(deploymentId)
  }

  def createDeploymentAgent(deploymentId: String) = {
    daemonClient.createDeploymentAgent(deploymentId)
  }

  def createBootstrapAgent(provider: String, target: String = "default") = {
    daemonClient.createBootstrapAgent(provider, target)
  }

  def generateDeploymentIdForBootstrap(provider: String, target: String = "default") = {
    daemonClient.generateDeploymentIdForBootstrap(provider, target)
  }

  def start(deploymentId: String) = {
    daemonClient.start(deploymentId)
  }

  def stop(deploymentId: String) = {
    daemonClient.stop(deploymentId)
  }

  def delete(deploymentId: String) = {
    daemonClient.delete(deploymentId)
  }
}
