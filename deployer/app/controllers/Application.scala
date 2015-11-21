package controllers

import java.io.File
import java.nio.file.{Files, Paths}

import com.toscaruntime.constant.DeployerConstant
import com.toscaruntime.sdk.Deployment
import com.toscaruntime.tosca.runtime.Deployer
import com.typesafe.config.ConfigFactory
import com.typesafe.config.impl.ConfigImpl
import models.{DeploymentInformation, RestResponse}
import org.yaml.snakeyaml.Yaml
import play.api.libs.json._
import play.api.mvc.{Action, Controller}

import scala.collection.JavaConverters._

object Application extends Controller with Logging {

  val yamlParser = new Yaml()

  val recipePath = {
    Paths.get(play.Play.application().configuration().getString("tosca.runtime.deployment.recipeDir"))
  }

  val deploymentInputsPath = {
    val inputPath = Paths.get(play.Play.application().configuration().getString("tosca.runtime.deployment.inputFile"))
    if (Files.isRegularFile(inputPath)) {
      Some(inputPath)
    } else {
      None
    }
  }

  val deploymentConfiguration = ConfigFactory.parseFile(
    new File(play.Play.application().configuration().getString("tosca.runtime.deployment.confFile"))
  ).resolveWith(play.Play.application().configuration().underlying()).resolveWith(ConfigImpl.systemPropertiesAsConfig())

  val deploymentName = deploymentConfiguration.getString(DeployerConstant.DEPLOYMENT_NAME_KEY)

  val bootstrap = deploymentConfiguration.getBoolean(DeployerConstant.BOOTSTRAP_KEY)

  val providerConfiguration = {
    val providerConfig = ConfigFactory.parseFile(
      new File(play.Play.application().configuration().getString("tosca.runtime.provider.confFile"))
    ).resolveWith(play.Play.application().configuration().underlying()).resolveWith(ConfigImpl.systemPropertiesAsConfig())
    providerConfig.entrySet().asScala.map { entry =>
      (entry.getKey, entry.getValue.unwrapped().asInstanceOf[String])
    }.toMap
  }

  val deployment: Deployment = Deployer.createDeployment(recipePath, deploymentInputsPath, providerConfiguration, bootstrap)

  def deploy() = Action { implicit request =>
    log.info("Install deployment with name " + deploymentName + " from recipe at " + recipePath)
    deployment.install()
    Ok
  }

  def undeploy() = Action { implicit request =>
    log.info("Uninstall deployment with name " + deploymentName)
    deployment.uninstall()
    Ok
  }

  def getDeploymentInformation = Action { implicit request =>
    Ok(Json.toJson(RestResponse.success[DeploymentInformation](Some(DeploymentInformation.fromDeployment(deploymentName, deployment)))))
  }

}
