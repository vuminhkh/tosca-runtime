package controllers

import java.io.File
import java.nio.file.{Files, Paths}

import com.mkv.tosca.constant.DeployerConstant
import com.mkv.tosca.runtime.Deployer
import com.mkv.tosca.sdk.Deployment
import com.typesafe.config.ConfigFactory
import com.typesafe.config.impl.ConfigImpl
import models.{DeploymentInformation, RestResponse}
import org.yaml.snakeyaml.Yaml
import play.api.libs.json._
import play.api.mvc.{Action, Controller}

object Application extends Controller with Logging {

  val yamlParser = new Yaml()

  var deploymentOpt: Option[Deployment] = None

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
  ).resolveWith(play.Play.application().configuration().underlying())

  val deploymentName = deploymentConfiguration.getString(DeployerConstant.DEPLOYMENT_NAME_KEY)

  val providerConfiguration = {
    ConfigFactory.parseFile(
      new File(play.Play.application().configuration().getString("tosca.runtime.provider.confFile"))
    ).resolveWith(play.Play.application().configuration().underlying())
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
