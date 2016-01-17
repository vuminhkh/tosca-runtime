package controllers

import javax.inject.Inject

import com.github.dockerjava.api.command.InspectContainerResponse
import com.toscaruntime.rest.client.DockerDaemonClient
import com.toscaruntime.rest.model.{DeploymentInfo, RestResponse}
import com.toscaruntime.util.DockerUtil
import play.api.cache._
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Application @Inject()(ws: WSClient, cache: CacheApi) extends Controller {

  // TODO better do it properly by performing connect operation in bootstrap recipe's relationship
  val dockerClient = connect(System.getenv("DOCKER_URL"))

  def connect(url: String) = {
    if (url != null && url.nonEmpty) {
      new DockerDaemonClient(url, null)
    } else {
      new DockerDaemonClient(DockerUtil.DEFAULT_DOCKER_URL, null)
    }
  }

  def getURL(deploymentId: String): Option[String] = {
    val cachedURL = cache.get[String](deploymentId)
    if (cachedURL.isEmpty) {
      dockerClient.getAgentInfo(deploymentId).map { agentInfo =>
        val context = cache.get[JsObject](bootstrapContextKey).getOrElse(Json.obj())
        val ipAddresses = agentInfo.getNetworkSettings.getNetworks.asScala.map {
          case (networkName: String, network: InspectContainerResponse.Network) => (networkName, network.getIpAddress)
        }.toMap
        val agentIp = ipAddresses.getOrElse(context.value.getOrElse("docker_network_name", JsString("bridge")).asInstanceOf[JsString].value, ipAddresses.values.head)
        val agentURL = "http://" + agentIp + ":9000/deployment"
        cache.set(deploymentId, agentURL)
        agentURL
      }
    } else {
      cachedURL
    }
  }

  private val bootstrapContextKey: String = "bootstrap_context"

  def saveBootstrapContext() = Action { request =>
    request.body.asJson.map { json =>
      json match {
        case JsObject(fields) =>
          cache.set(bootstrapContextKey, json.as[JsObject])
          Ok
        case _ => BadRequest("Expecting a map of value")
      }
    }.getOrElse {
      BadRequest("Expecting Json data")
    }
  }

  def getBootstrapContext = Action { request =>
    val bootstrapContext = cache.get[JsObject](bootstrapContextKey).getOrElse(Json.obj())
    Ok(Json.toJson(RestResponse.success[JsObject](Some(bootstrapContext))))
  }

  def list = Action {
    Ok(Json.toJson(RestResponse.success[List[DeploymentInfo]](Some(dockerClient.listDeploymentAgents().values.toList))))
  }

  // TODO better error handling
  def get(deploymentId: String) = Action.async {
    val cachedURL = getURL(deploymentId)
    cachedURL.map { url =>
      ws.url(url).get().map { response =>
        Ok(response.json)
      }
    }.getOrElse(Future(NotFound))
  }

  def deploy(deploymentId: String) = Action.async {
    val cachedURL = getURL(deploymentId)
    cachedURL.map { url =>
      ws.url(url).post("").map { response =>
        Ok(response.json)
      }
    }.getOrElse(Future(NotFound))
  }

  def undeploy(deploymentId: String) = Action.async {
    val cachedURL = getURL(deploymentId)
    cachedURL.map { url =>
      ws.url(url).delete().map { response =>
        Ok(response.json)
      }
    }.getOrElse(Future(NotFound))
  }
}
