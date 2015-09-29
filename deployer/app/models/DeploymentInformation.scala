package models

import com.mkv.tosca.sdk.Deployment
import play.api.libs.json.Json

import scala.collection.JavaConverters._

case class Instance(id: String, state: String, attributes: Map[String, String])

object Instance {
  implicit val InstanceFormat = Json.format[Instance]
}

case class RelationshipInstance(sourceInstanceId: String, targetInstanceId: String, attributes: Map[String, String])

object RelationshipInstance {
  implicit val RelationshipInstanceFormat = Json.format[RelationshipInstance]
}

case class Node(id: String, properties: Map[String, String], instances: List[Instance])

object Node {
  implicit val NodeFormat = Json.format[Node]
}

case class RelationshipNode(sourceNodeId: String, targetNodeId: String, properties: Map[String, String], relationshipInstances: List[RelationshipInstance])

object RelationshipNode {
  implicit val RelationshipNodeFormat = Json.format[RelationshipNode]
}

case class DeploymentInformation(name: String, nodes: List[Node], relationships: List[RelationshipNode])

object DeploymentInformation {

  implicit val DeploymentInformationFormat = Json.format[DeploymentInformation]

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
    DeploymentInformation(name, nodes, relationships)
  }
}
