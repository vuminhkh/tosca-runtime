package com.toscaruntime.sdk;

import com.toscaruntime.exception.deployment.creation.InvalidDeploymentStateException;
import com.toscaruntime.sdk.model.DeploymentAddInstancesModification;
import com.toscaruntime.sdk.model.DeploymentDeleteInstancesModification;
import com.toscaruntime.sdk.model.DeploymentNode;
import com.toscaruntime.sdk.model.DeploymentRelationshipNode;
import com.toscaruntime.sdk.util.DeploymentUtil;
import tosca.nodes.Root;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Perform impact on the deployment (add / delete nodes)
 *
 * @author Minh Khang VU
 */
public class DeploymentImpacter {

    private DeploymentInitializer deploymentInitializer;

    public DeploymentImpacter(DeploymentInitializer deploymentInitializer) {
        this.deploymentInitializer = deploymentInitializer;
    }

    private Root getDeletedInstance(Set<Root> instances, String name, int index) {
        for (Root instance : instances) {
            if (instance.getIndex() == index) {
                return instance;
            }
        }
        throw new InvalidDeploymentStateException("Unexpected situation, cannot find instance with name " + name + " and index " + index + " from " + instances);
    }

    private DeploymentDeleteInstancesModification deleteInstanceTree(Root toBeDeleted, Deployment deployment) {
        DeploymentDeleteInstancesModification deploymentModification = new DeploymentDeleteInstancesModification();
        deploymentModification.getInstancesToDelete().put(toBeDeleted.getId(), toBeDeleted);
        deploymentModification.getRelationshipInstancesToDelete().addAll(deployment.getRelationshipInstanceBySourceId(toBeDeleted.getId()));
        deploymentModification.getRelationshipInstancesToDelete().addAll(deployment.getRelationshipInstanceByTargetId(toBeDeleted.getId()));
        for (Root child : deployment.getChildren(toBeDeleted)) {
            deploymentModification.merge(deleteInstanceTree(child, deployment));
        }
        return deploymentModification;
    }

    private DeploymentAddInstancesModification createNewInstanceTree(Deployment deployment, DeploymentNode node, Root parentInstance, int index) {
        DeploymentAddInstancesModification deploymentModification = new DeploymentAddInstancesModification();
        deploymentModification.getImpactedNodesByAddition().add(node);
        Root newInstance = deploymentInitializer.createInstance(deployment, node, parentInstance, index);
        deploymentModification.getInstancesToAdd().put(newInstance.getId(), newInstance);

        // Get all relationship nodes which have the node as source
        Set<DeploymentRelationshipNode> sourceRelationships = deployment.getRelationshipNodeBySourceName(node.getId());
        deploymentModification.getImpactedRelationshipNodesByAddition().addAll(sourceRelationships);
        for (DeploymentRelationshipNode sourceRelationship : sourceRelationships) {
            deploymentModification.getImpactedNodesByAddition().add(deployment.getNodeMap().get(sourceRelationship.getTargetNodeId()));
            deploymentModification.getImpactedInstancesByAddition().putAll(DeploymentUtil.toMap(deployment.getNodeInstancesByNodeName(sourceRelationship.getTargetNodeId())));
        }

        // Get all relationship nodes which have the node as target
        Set<DeploymentRelationshipNode> targetRelationships = deployment.getRelationshipNodeByTargetName(node.getId());
        deploymentModification.getImpactedRelationshipNodesByAddition().addAll(targetRelationships);
        for (DeploymentRelationshipNode targetRelationship : targetRelationships) {
            deploymentModification.getImpactedNodesByAddition().add(deployment.getNodeMap().get(targetRelationship.getSourceNodeId()));
            deploymentModification.getImpactedInstancesByAddition().putAll(DeploymentUtil.toMap(deployment.getNodeInstancesByNodeName(targetRelationship.getSourceNodeId())));
        }

        for (String childName : node.getChildren()) {
            DeploymentNode childNode = deployment.getNodeMap().get(childName);
            for (int i = 1; i <= childNode.getInstancesCount(); i++) {
                deploymentModification.merge(createNewInstanceTree(deployment, childNode, newInstance, i));
            }
        }
        return deploymentModification;
    }

    private DeploymentDeleteInstancesModification doDeleteNodeInstances(Deployment deployment, DeploymentNode node, int numberOfInstancesToDelete, Root parent) {
        DeploymentDeleteInstancesModification deploymentModification = new DeploymentDeleteInstancesModification();
        for (int i = 0; i < numberOfInstancesToDelete; i++) {
            Root toBeDeleted;
            int index = node.getInstancesCount() - i;
            if (parent != null) {
                toBeDeleted = getDeletedInstance(node.getInstances(), node.getId(), index);
            } else {
                toBeDeleted = getDeletedInstance(deployment.getNodeInstancesByNodeName(node.getId()), node.getId(), index);
            }
            deploymentModification.merge(deleteInstanceTree(toBeDeleted, deployment));
        }
        return deploymentModification;
    }

    private DeploymentAddInstancesModification doAddNodeInstances(Deployment deployment, DeploymentNode node, int numberOfInstancesToAdd, Root parent) {
        DeploymentAddInstancesModification deploymentModification = new DeploymentAddInstancesModification();
        for (int i = 1; i <= numberOfInstancesToAdd; i++) {
            int newInstanceIndex = node.getInstancesCount() + i;
            deploymentModification.merge(createNewInstanceTree(deployment, node, parent, newInstanceIndex));
        }
        Map<String, Root> allImpactedInstancesByAddition = new HashMap<>(deploymentModification.getInstancesToAdd());
        allImpactedInstancesByAddition.putAll(deploymentModification.getImpactedInstancesByAddition());
        for (DeploymentRelationshipNode relationshipNode : deploymentModification.getImpactedRelationshipNodesByAddition()) {
            Set<tosca.relationships.Root> newRelationshipInstances = deploymentInitializer.generateRelationshipsInstances(
                    DeploymentUtil.getNodeInstancesByNodeName(allImpactedInstancesByAddition, relationshipNode.getSourceNodeId()),
                    DeploymentUtil.getNodeInstancesByNodeName(allImpactedInstancesByAddition, relationshipNode.getTargetNodeId()),
                    relationshipNode);
            // Sometime a relationship has already existed in the deployment, in this case must not add it to the scaling operation
            newRelationshipInstances.removeAll(deployment.getRelationshipInstances());
            deploymentModification.getRelationshipInstancesToAdd().addAll(newRelationshipInstances);
        }
        return deploymentModification;
    }

    public DeploymentAddInstancesModification addNodeInstances(Deployment deployment, DeploymentNode node, int numberOfInstancesToAdd) {
        Set<Root> currentParentInstances = null;
        if (node.getParent() != null) {
            currentParentInstances = deployment.getNodeInstancesByNodeName(node.getParent());
        }
        DeploymentAddInstancesModification deploymentModification = new DeploymentAddInstancesModification();
        if (currentParentInstances == null) {
            DeploymentAddInstancesModification modification = doAddNodeInstances(deployment, node, numberOfInstancesToAdd, null);
            deploymentModification.merge(modification);
        } else {
            for (Root currentParentInstance : currentParentInstances) {
                DeploymentAddInstancesModification modification = doAddNodeInstances(deployment, node, numberOfInstancesToAdd, currentParentInstance);
                deploymentModification.merge(modification);
            }
        }
        deployment.getProviderHooks().forEach(providerHook -> providerHook.postConstructInstances(deploymentModification.getInstancesToAdd(), deploymentModification.getRelationshipInstancesToAdd()));
        return deploymentModification;
    }

    public DeploymentDeleteInstancesModification deleteNodeInstances(Deployment deployment, DeploymentNode node, int numberOfInstancesToDelete) {
        Set<Root> currentParentInstances = null;
        if (node.getParent() != null) {
            currentParentInstances = deployment.getNodeInstancesByNodeName(node.getParent());
        }
        DeploymentDeleteInstancesModification deploymentModification = new DeploymentDeleteInstancesModification();
        if (currentParentInstances == null) {
            DeploymentDeleteInstancesModification modification = doDeleteNodeInstances(deployment, node, numberOfInstancesToDelete, null);
            deploymentModification.merge(modification);
        } else {
            for (Root currentParentInstance : currentParentInstances) {
                DeploymentDeleteInstancesModification modification = doDeleteNodeInstances(deployment, node, numberOfInstancesToDelete, currentParentInstance);
                deploymentModification.merge(modification);
            }
        }
        return deploymentModification;
    }
}
