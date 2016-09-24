package com.toscaruntime.sdk;

import com.toscaruntime.exception.deployment.creation.InvalidDeploymentStateException;
import com.toscaruntime.sdk.model.DeploymentNode;
import com.toscaruntime.sdk.model.DeploymentRelationshipNode;
import com.toscaruntime.sdk.util.DeploymentUtil;
import tosca.nodes.Root;
import tosca.relationships.ManyToMany;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Utility methods to initialize nodes and instances of a deployment
 *
 * @author Minh Khang VU
 */
public class DeploymentInitializer {

    private TypeRegistry typeRegistry;

    public DeploymentInitializer(TypeRegistry instanceFactory) {
        this.typeRegistry = instanceFactory;
    }

    public DeploymentNode createNode(String nodeName,
                                     String type,
                                     String parentName,
                                     String hostName,
                                     Deployment deployment,
                                     Map<String, Object> properties,
                                     Map<String, Map<String, Object>> capabilitiesProperties) {
        DeploymentNode deploymentNode = new DeploymentNode();
        deploymentNode.setId(nodeName);
        deploymentNode.setType(typeRegistry.findInstanceType(type));
        deploymentNode.setHost(hostName);
        deploymentNode.setDeployment(deployment);
        deploymentNode.setParent(parentName);
        deploymentNode.setProperties(properties);
        deploymentNode.setCapabilitiesProperties(capabilitiesProperties);
        deploymentNode.setInstancesCount(deploymentNode.getDefaultInstancesCount());
        return deploymentNode;
    }

    public Root createInstance(Deployment deployment, DeploymentNode node, Root parent, int index) {
        try {
            Root instance = node.getType().newInstance();
            Root host = null;
            if (parent != null && node.getHost() != null && parent.getName().equals(node.getHost())) {
                host = parent;
            }
            instance.setName(node.getId());
            instance.setIndex(index);
            instance.setHost(host);
            instance.setParent(parent);
            instance.setNode(node);
            instance.setProperties(node.getProperties());
            instance.setCapabilitiesProperties(node.getCapabilitiesProperties());
            instance.setConfig(deployment.getConfig());
            return instance;
        } catch (Exception e) {
            throw new InvalidDeploymentStateException("Could not create new instance of type " + node.getType(), e);
        }
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


    public DeploymentRelationshipNode createRelationship(String sourceName,
                                                         String targetName,
                                                         Map<String, Object> properties,
                                                         String relationshipType) {
        DeploymentRelationshipNode relationshipNode = new DeploymentRelationshipNode();
        relationshipNode.setProperties(properties);
        relationshipNode.setSourceNodeId(sourceName);
        relationshipNode.setTargetNodeId(targetName);
        relationshipNode.setRelationshipType(typeRegistry.findRelationshipInstanceType(relationshipType));
        return relationshipNode;
    }

    public Set<tosca.relationships.Root> generateRelationshipsInstances(Set<Root> sourceInstances,
                                                                        Set<Root> targetInstances,
                                                                        DeploymentRelationshipNode relationshipNode) {
        Set<tosca.relationships.Root> newRelationshipInstances = new HashSet<>();
        for (tosca.nodes.Root sourceInstance : sourceInstances) {
            for (tosca.nodes.Root targetInstance : targetInstances) {
                if (!shouldFilterRelationship(sourceInstance, targetInstance, relationshipNode.getRelationshipType())) {
                    try {
                        tosca.relationships.Root relationshipInstance = relationshipNode.getRelationshipType().newInstance();
                        relationshipInstance.setConfig(sourceInstance.getConfig());
                        relationshipInstance.setSource(sourceInstance);
                        relationshipInstance.setTarget(targetInstance);
                        relationshipInstance.setNode(relationshipNode);
                        relationshipInstance.setProperties(relationshipNode.getProperties());
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
