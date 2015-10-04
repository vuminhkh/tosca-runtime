package controllers

import java.io.File
import java.nio.file.Paths

import com.google.common.collect.Maps
import com.mkv.tosca.constant.DeployerConstant
import com.mkv.tosca.runtime.Deployer
import com.mkv.tosca.sdk.Deployment
import com.typesafe.config.impl.ConfigImpl
import com.typesafe.config.{ConfigFactory, ConfigValue}
import models.{DeploymentInformation, RestResponse}
import org.yaml.snakeyaml.Yaml
import play.api.libs.json._
import play.api.mvc.{Action, Controller}
import scala.collection.JavaConversions._

object Application extends Controller with Logging {

  val yamlParser = new Yaml()

  var deploymentOpt: Option[Deployment] = None

  val recipePath = {
    Paths.get(play.Play.application().configuration().getString("tosca.runtime.deployment.recipeDir"))
  }

  val deploymentInputsPath = Paths.get(play.Play.application().configuration().getString("tosca.runtime.deployment.inputFile"))

  val deploymentConfiguration = ConfigFactory.parseFile(new File(play.Play.application().configuration().getString("tosca.runtime.deployment.confFile"))).resolveWith(ConfigImpl.systemPropertiesAsConfig())

  val deploymentName = deploymentConfiguration.getString(DeployerConstant.DEPLOYMENT_NAME_KEY)

  val providerConfiguration = {
    val providerConfigRaw = ConfigFactory.parseFile(new File(play.Play.application().configuration().getString("tosca.runtime.provider.confFile"))).resolveWith(ConfigImpl.systemPropertiesAsConfig())
    val providerConfig = Maps.newHashMap[String, String]()
    for (entry: java.util.Map.Entry[String, ConfigValue] <- providerConfigRaw.entrySet()) {
      providerConfig.put(entry.getKey, entry.getValue.unwrapped().asInstanceOf[String])
    }
    providerConfig
  }

  def deploy() = Action {
    implicit request =>
      log.info("Install deployment with name " + deploymentName + " from recipe at " + recipePath)
      deploymentOpt.map { deployment =>
        BadRequest("Application is already deployed")
      }.getOrElse {
        deploymentOpt = Some(Deployer.deploy(recipePath, deploymentInputsPath, providerConfiguration))
        Ok
      }
  }

  def undeploy() = Action {
    implicit request =>
      log.info("Uninstall deployment with name " + deploymentName)
      deploymentOpt.map { deployment =>
        deployment.uninstall()
        deploymentOpt = None
        Ok
      }.getOrElse(BadRequest("Application is not deployed"))
  }

  def getDeploymentInformation = Action {
    implicit request =>
      deploymentOpt.map { deployment =>
        Ok(Json.toJson(RestResponse.success[DeploymentInformation](Some(DeploymentInformation.fromDeployment(deploymentName, deployment)))))
      }.getOrElse(Ok("Application is not deployed"))
  }
}
