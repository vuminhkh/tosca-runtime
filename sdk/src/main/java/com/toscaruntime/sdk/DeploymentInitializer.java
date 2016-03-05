package com.toscaruntime.sdk;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.toscaruntime.exception.deployment.creation.InvalidDeploymentStateException;
import com.toscaruntime.sdk.model.DeploymentNode;
import com.toscaruntime.sdk.model.DeploymentRelationshipNode;
import com.toscaruntime.sdk.util.DeploymentUtil;

import tosca.nodes.Root;
import tosca.relationships.ManyToMany;

/**
 * Utility methods to initialize nodes and instances of a deployment
 *
 * @author Minh Khang VU
 */
public class DeploymentInitializer {

    public void setDependencies(Map<String, DeploymentNode> allNodes, String nodeName, String... dependencies) {
        DeploymentNode node = allNodes.get(nodeName);
        node.setDependsOnNodes(new HashSet<>(Arrays.asList(dependencies)));
        for (String dependency : dependencies) {
            allNodes.get(dependency).getDependedByNodes().add(nodeName);
        }
    }

    public DeploymentNode initializeNode(String nodeName,
                                         Class<? extends Root> type,
                                         String parentName,
                                         String hostName,
                                         Deployment deployment,
                                         Map<String, Object> properties,
                                         Map<String, Map<String, Object>> capabilitiesProperties,
                                         Map<String, DeploymentNode> existingNodes) {
        DeploymentNode deploymentNode = new DeploymentNode();
        deploymentNode.setId(nodeName);
        deploymentNode.setType(type);
        deploymentNode.setHost(hostName);
        deploymentNode.setDeployment(deployment);
        deploymentNode.setParent(parentName);
        if (parentName != null) {
            existingNodes.get(parentName).getChildren().add(nodeName);
        }
        deploymentNode.setProperties(properties);
        deploymentNode.setCapabilitiesProperties(capabilitiesProperties);
        deploymentNode.setInstancesCount(deploymentNode.getDefaultInstancesCount());
        existingNodes.put(nodeName, deploymentNode);
        return deploymentNode;
    }

    public void initializeInstance(tosca.nodes.Root instance,
                                   Deployment deployment,
                                   String name,
                                   int index,
                                   tosca.nodes.Root parent,
                                   tosca.nodes.Root host) {
        instance.setName(name);
        instance.setIndex(index);
        instance.setHost(host);
        instance.setParent(parent);
        DeploymentNode node = deployment.getNodeMap().get(name);
        node.getInstances().add(instance);
        instance.setNode(node);
        instance.setProperties(node.getProperties());
        instance.setCapabilitiesProperties(node.getCapabilitiesProperties());
        if (parent != null) {
            parent.getChildren().add(instance);
        }
        instance.setDeployment(deployment);
        instance.setConfig(deployment.getConfig());
    }

    public boolean shouldFilterRelationship(tosca.nodes.Root sourceInstance, tosca.nodes.Root targetInstance, Class<? extends tosca.relationships.Root> relationshipType) {
        // Always generate relationship of type many to many
        if (ManyToMany.class.isAssignableFrom(relationshipType)) {
            return false;
        }
        // The relationship will be filtered if the nodes belong to the same scaling group but not the same scaling group instance
        // For ex: If the nodes are hosted on the same compute node, but on different instances of this same compute node
        LinkedHashMap<String, tosca.nodes.Root> sourceAncestors = DeploymentUtil.getInstanceAncestors(sourceInstance);
        LinkedHashMap<String, tosca.nodes.Root> targetAncestors = DeploymentUtil.getInstanceAncestors(targetInstance);
        // Go up the ancestors to search for the common node / scaling group but different instance to filter out the relationship
        String nearestCommonScalingGroup = null;
        for (String sourceAncestor : sourceAncestors.keySet()) {
            if (targetAncestors.containsKey(sourceAncestor)) {
                nearestCommonScalingGroup = sourceAncestor;
            }
        }
        // The source and target belong to the same scaling group but different instance, must filter out
        // For the future, the scaling group will also consider group defined by scaling policy and not only by parent child hierarchy
        return nearestCommonScalingGroup != null && !sourceAncestors.get(nearestCommonScalingGroup).getId().equals(targetAncestors.get(nearestCommonScalingGroup).getId());
    }


    public void generateRelationships(String sourceName,
                                      String targetName,
                                      Map<String, Object> properties,
                                      Class<? extends tosca.relationships.Root> relationshipType,
                                      Set<DeploymentRelationshipNode> relationshipNodes) {
        DeploymentRelationshipNode relationshipNode = new DeploymentRelationshipNode();
        relationshipNode.setProperties(properties);
        relationshipNode.setSourceNodeId(sourceName);
        relationshipNode.setTargetNodeId(targetName);
        relationshipNode.setRelationshipType(relationshipType);
        relationshipNodes.add(relationshipNode);
    }

    public void generateRelationshipsInstances(String sourceName,
                                               String targetName,
                                               Map<String, DeploymentNode> nodes,
                                               Set<DeploymentRelationshipNode> relationshipNodes,
                                               Class<? extends tosca.relationships.Root> relationshipType,
                                               Set<tosca.relationships.Root> relationshipInstances,
                                               Deployment deployment) {
        DeploymentRelationshipNode relationshipNode = DeploymentUtil.getRelationshipNodeBySourceNameTargetName(relationshipNodes, sourceName, targetName, relationshipType);
        if (relationshipNode == null) {
            throw new InvalidDeploymentStateException("Relationship node with source " + sourceName + " and target " + targetName + " and type " + relationshipType.getName() + " cannot be found");
        }
        Set<Root> sourceInstances = nodes.get(relationshipNode.getSourceNodeId()).getInstances();
        Set<Root> targetInstances = nodes.get(relationshipNode.getTargetNodeId()).getInstances();
        relationshipInstances.addAll(generateRelationshipsInstances(sourceInstances, targetInstances, relationshipNode, deployment));
    }

    public Set<tosca.relationships.Root> generateRelationshipsInstances(Set<Root> sourceInstances, Set<Root> targetInstances, DeploymentRelationshipNode relationshipNode, Deployment deployment) {
        Set<tosca.relationships.Root> newRelationshipInstances = new HashSet<>();
        for (tosca.nodes.Root sourceInstance : sourceInstances) {
            for (tosca.nodes.Root targetInstance : targetInstances) {
                if (!shouldFilterRelationship(sourceInstance, targetInstance, relationshipNode.getRelationshipType())) {
                    try {
                        tosca.relationships.Root relationshipInstance = relationshipNode.getRelationshipType().newInstance();
                        relationshipInstance.setSource(sourceInstance);
                        relationshipInstance.setTarget(targetInstance);
                        relationshipInstance.setNode(relationshipNode);
                        relationshipInstance.setProperties(relationshipNode.getProperties());
                        relationshipInstance.setDeployment(deployment);
                        newRelationshipInstances.add(relationshipInstance);
                    } catch (InstantiationException | IllegalAccessException e) {
                        throw new InvalidDeploymentStateException("Could not create relationship instance of type " + relationshipNode.getRelationshipType().getName(), e);
                    }
                }
            }
        }
        return newRelationshipInstances;
    }
}
