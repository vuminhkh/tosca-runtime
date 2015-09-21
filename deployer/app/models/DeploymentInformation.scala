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

case class DeploymentInformation(nodes: List[Node], relationships: List[RelationshipNode])

object DeploymentInformation {

  implicit val DeploymentInformationFormat = Json.format[DeploymentInformation]

  /**
   * Convert from java deployment to deployment information to return back to rest client
   * @param deployment the managed deployment
   * @return current deployment information
   */
  def fromDeployment(deployment: Deployment) = {
    val nodes = deployment.getNodes.asScala.map { node =>
      val instances = node.getInstances.asScala.map { instance =>
        Instance(instance.getId, instance.getState, instance.getAttributes.asScala.toMap)
      }.toList
      Node(node.getId, node.getProperties.asScala.toMap.asInstanceOf[Map[String, String]], instances)
    }.toList
    val relationships = deployment.getRelationshipNodes.asScala.map { relationshipNode =>
      val relationshipInstances = relationshipNode.getRelationshipInstances.asScala.map { relationshipInstance =>
        RelationshipInstance(relationshipInstance.getSource.getId, relationshipInstance.getTarget.getId, relationshipInstance.getAttributes.asScala.toMap)
      }.toList
      RelationshipNode(relationshipNode.getSourceNodeId, relationshipNode.getTargetNodeId, relationshipNode.getProperties.asScala.toMap.asInstanceOf[Map[String, String]], relationshipInstances)
    }.toList
    DeploymentInformation(nodes, relationships)
  }
}
