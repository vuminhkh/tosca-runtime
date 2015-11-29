package controllers

import javax.inject.Inject

import com.toscaruntime.rest.client.DockerDaemonClient
import com.toscaruntime.rest.model.{DeploymentInfo, RestResponse}
import play.api.cache._
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.WSClient
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Application @Inject()(ws: WSClient, cache: CacheApi) extends Controller {

  // TODO better do it properly by performing connect operation in bootstrap recipe's relationship
  val dockerClient = connect(System.getenv("DOCKER_HOST"))

  def refreshAgentsURL() = {
    val allDeployments = dockerClient.listDeploymentAgents()
    allDeployments.foreach {
      case (deploymentId: String, deploymentInfo: DeploymentInfo) => cache.set(deploymentId, "http://" + deploymentInfo.agentIP + ":9000/deployment")
    }
  }

  def connect(url: String) = {
    if (url != null && url.nonEmpty) {
      new DockerDaemonClient(url, null)
    } else {
      new DockerDaemonClient("unix:///var/run/docker.sock", null)
    }
  }

  def getURL(deploymentId: String) = {
    var cachedURL = cache.get[String](deploymentId)
    if (cachedURL.isEmpty) {
      refreshAgentsURL()
      cachedURL = cache.get[String](deploymentId)
    }
    cachedURL
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
