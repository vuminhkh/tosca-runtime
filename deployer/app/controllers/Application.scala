package controllers

import java.nio.file.{Files, Paths}
import java.util

import com.mkv.tosca.runtime.Deployer
import com.mkv.tosca.sdk.Deployment
import models.{DeploymentInformation, RestResponse}
import org.yaml.snakeyaml.Yaml
import play.api.mvc.{Action, Controller}
import play.api.libs.json._
import scala.collection.JavaConverters._

object Application extends Controller with Logging {

  val yamlParser = new Yaml()

  var deploymentOpt: Option[Deployment] = None

  val recipePath = {
    Paths.get(play.Play.application().configuration().getString("recipeFolder"))
  }

  val providerConfiguration = {
    yamlParser.loadAs(Files.newInputStream(Paths.get(play.Play.application().configuration().getString("providerConfigurationFile"))), classOf[util.Map[String, AnyRef]]).asScala.toMap
  }

  val deploymentInputs = {
    yamlParser.loadAs(Files.newInputStream(Paths.get(play.Play.application().configuration().getString("deploymentInputFile"))), classOf[util.Map[String, AnyRef]]).asScala.toMap
  }

  def deploy() = Action {
    implicit request =>
      log.info("Deploying recipe")
      deploymentOpt = Some(Deployer.deploy(recipePath, providerConfiguration, deploymentInputs))
      Ok
  }

  def undeploy() = Action {
    implicit request =>
      log.info("Undeploying recipe")
      deploymentOpt.map { deployment =>
        deployment.uninstall()
        Ok
      }.getOrElse(BadRequest("Application is not deployed"))
  }

  def getDeploymentInformation = Action {
    implicit request =>
      deploymentOpt.map { deployment =>
        Ok(Json.toJson(RestResponse.success[DeploymentInformation](Some(DeploymentInformation.fromDeployment(deployment)))))
      }.getOrElse(BadRequest("Application is not deployed"))
  }
}
