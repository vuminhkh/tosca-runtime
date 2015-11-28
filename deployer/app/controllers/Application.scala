package controllers

import java.io.File
import java.nio.file.{Files, Paths}

import com.toscaruntime.constant.DeployerConstant
import com.toscaruntime.rest.model._
import com.toscaruntime.sdk.Deployment
import com.toscaruntime.runtime.Deployer
import com.typesafe.config.ConfigFactory
import com.typesafe.config.impl.ConfigImpl
import org.yaml.snakeyaml.Yaml
import play.api.libs.json.Json
import play.api.mvc.{Action, Controller}

import scala.collection.JavaConverters._

object Application extends Controller with Logging {

  val yamlParser = new Yaml()

  val recipePath = {
    Paths.get(play.Play.application().configuration().getString("com.toscaruntime.deployment.recipeDir"))
  }

  val deploymentInputsPath = {
    val inputPath = Paths.get(play.Play.application().configuration().getString("com.toscaruntime.deployment.inputFile"))
    if (Files.isRegularFile(inputPath)) {
      Some(inputPath)
    } else {
      None
    }
  }

  val deploymentConfiguration = ConfigFactory.parseFile(
    new File(play.Play.application().configuration().getString("com.toscaruntime.deployment.confFile"))
  ).resolveWith(play.Play.application().configuration().underlying()).resolveWith(ConfigImpl.systemPropertiesAsConfig())

  val deploymentName = deploymentConfiguration.getString(DeployerConstant.DEPLOYMENT_NAME_KEY)

  val bootstrap = deploymentConfiguration.getBoolean(DeployerConstant.BOOTSTRAP_KEY)

  val providerConfiguration = {
    val providerConfig = ConfigFactory.parseFile(
      new File(play.Play.application().configuration().getString("com.toscaruntime.provider.confFile"))
    ).resolveWith(play.Play.application().configuration().underlying()).resolveWith(ConfigImpl.systemPropertiesAsConfig())
    providerConfig.entrySet().asScala.map { entry =>
      (entry.getKey, entry.getValue.unwrapped().asInstanceOf[String])
    }.toMap
  }

  val deployment: Deployment = Deployer.createDeployment(recipePath, deploymentInputsPath, providerConfiguration, bootstrap)

  def deploy() = Action { implicit request =>
    log.info("Install deployment with name " + deploymentName + " from recipe at " + recipePath)
    deployment.install()
    Ok(Json.toJson(RestResponse.success[DeploymentDetails](Some(fromDeployment(deploymentName, deployment)))))
  }

  def undeploy() = Action { implicit request =>
    log.info("Uninstall deployment with name " + deploymentName)
    deployment.uninstall()
    Ok
  }

  /**
    * Convert from java deployment to deployment information to return back to rest client
    * @param deployment the managed deployment
    * @return current deployment information
    */
  def fromDeployment(name: String, deployment: Deployment) = {
    val nodes = deployment.getNodes.asScala.map { node =>
      val instances = node.getInstances.asScala.map { instance =>
        val instanceAttributes = if (instance.getAttributes == null) Map.empty[String, String] else instance.getAttributes.asScala.toMap
        Instance(instance.getId, instance.getState, instanceAttributes)
      }.toList
      val nodeProperties = if (node.getProperties == null) Map.empty[String, String] else node.getProperties.asScala.toMap.asInstanceOf[Map[String, String]]
      Node(node.getId, nodeProperties, instances)
    }.toList
    val relationships = deployment.getRelationshipNodes.asScala.map { relationshipNode =>
      val relationshipInstances = relationshipNode.getRelationshipInstances.asScala.map { relationshipInstance =>
        val relationshipInstanceAttributes = if (relationshipInstance.getAttributes == null) Map.empty[String, String] else relationshipInstance.getAttributes.asScala.toMap
        RelationshipInstance(relationshipInstance.getSource.getId, relationshipInstance.getTarget.getId, relationshipInstanceAttributes)
      }.toList
      val relationshipNodeProperties = if (relationshipNode.getProperties == null) Map.empty[String, String] else relationshipNode.getProperties.asScala.toMap.asInstanceOf[Map[String, String]]
      RelationshipNode(relationshipNode.getSourceNodeId, relationshipNode.getTargetNodeId, relationshipNodeProperties, relationshipInstances)
    }.toList
    DeploymentDetails(name, nodes, relationships, deployment.getOutputs.asScala.toMap.map { case (key: String, value: AnyRef) => (key, String.valueOf(value)) })
  }

  def getDeploymentInformation = Action { implicit request =>
    Ok(Json.toJson(RestResponse.success[DeploymentDetails](Some(fromDeployment(deploymentName, deployment)))))
  }

}
