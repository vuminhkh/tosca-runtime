package controllers

import java.io.File
import java.nio.file.{Files, Paths}
import java.util

import com.mkv.tosca.runtime.Deployer
import com.mkv.tosca.sdk.Deployment
import com.typesafe.config.ConfigFactory
import com.typesafe.config.impl.ConfigImpl
import models.{DeploymentInformation, RestResponse}
import org.yaml.snakeyaml.Yaml
import play.api.libs.json._
import play.api.mvc.{Action, Controller}

import scala.collection.JavaConverters._

object Application extends Controller with Logging {

  val yamlParser = new Yaml()

  var deploymentOpt: Option[Deployment] = None

  val recipePath = {
    Paths.get(play.Play.application().configuration().getString("tosca.runtime.deployment.recipeDir"))
  }

  val deploymentConfiguration = ConfigFactory.parseFile(new File(play.Play.application().configuration().getString("tosca.runtime.deployment.confFile"))).resolveWith(ConfigImpl.systemPropertiesAsConfig())

  val deploymentName = deploymentConfiguration.getString("tosca.runtime.deployment.name")

  val providerConfiguration = {
    val providerConfigRaw = ConfigFactory.parseFile(new File(play.Play.application().configuration().getString("tosca.runtime.provider.confFile"))).resolveWith(ConfigImpl.systemPropertiesAsConfig())
    providerConfigRaw.entrySet().asScala.map { configEntry =>
      (configEntry.getKey, configEntry.getValue.unwrapped().asInstanceOf[String])
    }.toMap
  }

  val deploymentInputs = {
    val inputs = yamlParser.loadAs(Files.newInputStream(Paths.get(play.Play.application().configuration().getString("tosca.runtime.deployment.inputFile"))), classOf[util.Map[String, AnyRef]])
    if (inputs != null && !inputs.isEmpty) {
      inputs.asScala.toMap
    } else {
      Map.empty[String, AnyRef]
    }
  }

  def deploy() = Action {
    implicit request =>
      log.info("Install deployment with name " + deploymentName)
      deploymentOpt = Some(Deployer.deploy(recipePath, deploymentInputs, providerConfiguration))
      Ok
  }


  def undeploy() = Action {
    implicit request =>
      log.info("Uninstall deployment with name " + deploymentName)
      deploymentOpt.map { deployment =>
        deployment.uninstall()
        Ok
      }.getOrElse(BadRequest("Application is not deployed"))
  }

  def getDeploymentInformation = Action {
    implicit request =>
      deploymentOpt.map { deployment =>
        Ok(Json.toJson(RestResponse.success[DeploymentInformation](Some(DeploymentInformation.fromDeployment(deploymentName, deployment)))))
      }.getOrElse(BadRequest("Application is not deployed"))
  }
}
