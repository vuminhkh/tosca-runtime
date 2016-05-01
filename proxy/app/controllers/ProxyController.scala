package controllers

import java.nio.file._
import javax.inject.Inject

import com.github.dockerjava.api.model.NetworkSettings
import com.toscaruntime.exception.UnexpectedException
import com.toscaruntime.rest.client.DockerDaemonClient
import com.toscaruntime.rest.model.{DeploymentInfoDTO, RestResponse}
import com.typesafe.scalalogging.LazyLogging
import play.api.cache._
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc._
import play.mvc.Http.MimeTypes

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.collection.JavaConverters._

class ProxyController @Inject()(ws: WSClient, cache: CacheApi) extends Controller with LazyLogging {

  private val bootstrapContextCacheKey: String = "bootstrap_context"

  private val agentURLCacheKeyPrefix: String = "agent_"

  // TODO better do it properly by performing connect operation in bootstrap recipe's relationship
  private lazy val dockerClient = connect(System.getenv("DOCKER_URL"))

  private lazy val bootstrapContextPath = Paths.get(play.Play.application().configuration().getString("com.toscaruntime.bootstrapContext"))

  def loadBootstrapContext() = {
    val cached = cache.get[JsObject](bootstrapContextCacheKey)
    if (cached.nonEmpty) {
      cached.get
    } else if (Files.exists(bootstrapContextPath)) {
      logger.info(s"Load bootstrap context from $bootstrapContextPath")
      val persisted = Json.parse(Files.newInputStream(bootstrapContextPath)).as[JsObject]
      cache.set(bootstrapContextCacheKey, persisted)
      persisted
    } else {
      Json.obj()
    }
  }

  def connect(url: String) = {
    if (url != null && url.nonEmpty) {
      new DockerDaemonClient(url, null)
    } else {
      throw new UnexpectedException("Need docker url to initialize toscaruntime proxy")
    }
  }

  def getURL(deploymentId: String): Option[String] = {
    val agentURLKey = agentURLCacheKeyPrefix + deploymentId
    val cachedURL = cache.get[String](agentURLKey)
    if (cachedURL.isEmpty) {
      dockerClient.getAgentInfo(deploymentId).map { agentInfo =>
        val context = loadBootstrapContext()
        val ipAddresses = agentInfo.getNetworkSettings.getNetworks.asScala.map {
          case (networkName: String, network: NetworkSettings.Network) => (networkName, network.getIpAddress)
        }.toMap
        val agentIp = ipAddresses.getOrElse(context.value.getOrElse("docker_network_name", JsString("bridge")).asInstanceOf[JsString].value, ipAddresses.values.head)
        val agentURL = "http://" + agentIp + ":9000"
        cache.set(agentURLKey, agentURL)
        agentURL
      }
    } else {
      cachedURL
    }
  }

  def saveBootstrapContext() = Action { request =>
    request.body.asJson.map {
      case json@JsObject(fields) =>
        cache.set(bootstrapContextCacheKey, json.as[JsObject])
        Files.write(bootstrapContextPath, Json.prettyPrint(json).getBytes("UTF-8"), StandardOpenOption.TRUNCATE_EXISTING)
        Ok(s"Bootstrap context saved to $bootstrapContextPath")
      case _ => BadRequest("Expecting a map of value")
    }.getOrElse {
      BadRequest("Expecting Json data")
    }
  }

  def getBootstrapContext = Action { request =>
    Ok(Json.toJson(RestResponse.success[JsObject](Some(loadBootstrapContext()))))
  }

  def list = Action {
    Ok(Json.toJson(RestResponse.success[List[DeploymentInfoDTO]](Some(dockerClient.listDeploymentAgents().values.toList))))
  }

  private def handleWSResponse(response: WSResponse) = {
    response.status match {
      case play.api.http.Status.OK =>
        val contentTypeOpt = response.header(play.api.http.HeaderNames.CONTENT_TYPE)
        val proxyResponse = Ok(response.body)
        if (contentTypeOpt.isDefined) proxyResponse.withHeaders((play.api.http.HeaderNames.CONTENT_TYPE, contentTypeOpt.get))
        proxyResponse
      case play.api.http.Status.BAD_REQUEST => BadRequest(response.body)
      case _ => InternalServerError(s"Encountered unexpected status ${response.status} :\n ${response.body}")
    }
  }

  private def doRedirect(deploymentId: String, path: String, request: Request[AnyContent], webAction: (String, Request[AnyContent]) => Future[WSResponse]) = {
    val url = getURL(deploymentId).map(_ + path)
    url.map { url =>
      webAction(url, request).map(handleWSResponse)
    }.getOrElse(Future(NotFound(s"Deployment id $deploymentId do not exist")))
  }

  private def doRedirectPost(deploymentId: String, path: String, request: Request[AnyContent]) = {
    doRedirect(deploymentId, path, request, (url, request) => {
      val redirectQuery = ws.url(url)
      request.queryString.foreach {
        case (key, values) => values.foreach(value => redirectQuery.withQueryString((key, value)))
      }
      request.contentType.getOrElse(MimeTypes.TEXT) match {
        case MimeTypes.JSON => redirectQuery.post(request.body.asJson.getOrElse(JsNull))
        case _ => redirectQuery.post(request.body.asText.getOrElse(""))
      }
    })
  }

  private def doRedirectGet(deploymentId: String, path: String, request: Request[AnyContent]) = {
    doRedirect(deploymentId, path, request, (url, request) => ws.url(url).get())
  }

  def execute(deploymentId: String) = Action.async { request =>
    doRedirectPost(deploymentId, "/deployment/executions", request)
  }

  def get(deploymentId: String) = Action.async { request =>
    doRedirectGet(deploymentId, "/deployment", request)
  }

  def cancel(deploymentId: String) = Action.async { request =>
    doRedirectPost(deploymentId, "/deployment/executions/cancel", request)
  }

  def resume(deploymentId: String) = Action.async { request =>
    doRedirectPost(deploymentId, "/deployment/executions/resume", request)
  }

  def stop(deploymentId: String) = Action.async { request =>
    doRedirectPost(deploymentId, "/deployment/executions/stop", request)
  }

  def updateRecipe(deploymentId: String) = Action.async { request =>
    doRedirectPost(deploymentId, "/deployment/recipe/update", request)
  }
}
