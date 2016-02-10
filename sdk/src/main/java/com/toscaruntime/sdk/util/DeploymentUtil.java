package com.toscaruntime.sdk.util;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.toscaruntime.sdk.model.DeploymentRelationshipNode;

import tosca.relationships.Root;

@SuppressWarnings("unchecked")
public class DeploymentUtil {

    public static Set<Root> getRelationshipsBySourceAndTarget(Set<Root> relationshipInstances, tosca.nodes.Root source, tosca.nodes.Root target) {
        return relationshipInstances.stream().filter(instance -> instance.getSource().equals(source) && instance.getTarget().equals(target)).collect(Collectors.toSet());
    }

    public static DeploymentRelationshipNode getRelationshipNodeBySourceNameTargetName(Set<DeploymentRelationshipNode> relationshipNodes, String sourceName, String targetName, Class<? extends Root> relationshipType) {
        for (DeploymentRelationshipNode relationshipNode : relationshipNodes) {
            if (relationshipNode.getSourceNodeId().equals(sourceName) && relationshipNode.getTargetNodeId().equals(targetName) && relationshipNode.getRelationshipType() == relationshipType) {
                return relationshipNode;
            }
        }
        return null;
    }

    public static Set<DeploymentRelationshipNode> getRelationshipNodeBySourceName(Set<DeploymentRelationshipNode> relationshipNodes, String sourceName) {
        return relationshipNodes.stream().filter(relationshipNode ->
                relationshipNode.getSourceNodeId().equals(sourceName)
        ).collect(Collectors.toSet());
    }

    public static Set<DeploymentRelationshipNode> getRelationshipNodeByTargetName(Set<DeploymentRelationshipNode> relationshipNodes, String targetName) {
        return relationshipNodes.stream().filter(relationshipNode ->
                relationshipNode.getTargetNodeId().equals(targetName)
        ).collect(Collectors.toSet());
    }

    public static Set<tosca.nodes.Root> getNodeInstancesByNodeName(Map<String, tosca.nodes.Root> nodeInstances, String nodeName) {
        return nodeInstances.values().stream().filter(nodeInstance ->
                nodeInstance.getName().equals(nodeName)
        ).collect(Collectors.toSet());
    }

    public static <T extends tosca.relationships.Root> Set<T> getRelationshipInstancesByNamesAndType(Set<tosca.relationships.Root> relationshipInstances, String sourceName, String targetName, Class<T> relationshipType) {
        return relationshipInstances.stream().filter(relationshipInstance ->
                relationshipInstance.getSource().getName().equals(sourceName) &&
                        relationshipInstance.getTarget().getName().equals(targetName) &&
                        relationshipType == relationshipInstance.getClass()
        ).map(relationshipInstance -> (T) relationshipInstance).collect(Collectors.toSet());
    }

    public static Set<tosca.relationships.Root> getRelationshipInstanceBySourceId(Set<tosca.relationships.Root> relationshipInstances, String sourceId) {
        return relationshipInstances.stream().filter(relationshipInstance ->
                relationshipInstance.getSource().getId().equals(sourceId)
        ).collect(Collectors.toSet());
    }

    public static Set<Root> getRelationshipInstanceByTargetId(Set<Root> relationshipInstances, String targetId) {
        return relationshipInstances.stream().filter(relationshipInstance ->
                relationshipInstance.getTarget().getId().equals(targetId)
        ).collect(Collectors.toSet());
    }

    public static Map<String, tosca.nodes.Root> toMap(Set<tosca.nodes.Root> instances) {
        Map<String, tosca.nodes.Root> instanceMap = new HashMap<>();
        for (tosca.nodes.Root instance : instances) {
            instanceMap.put(instance.getId(), instance);
        }
        return instanceMap;
    }

    public static LinkedHashMap<String, tosca.nodes.Root> getInstanceAncestors(tosca.nodes.Root instance) {
        LinkedHashMap<String, tosca.nodes.Root> ancestorsIncludingSelf = new LinkedHashMap<>();
        tosca.nodes.Root current = instance;
        while (current != null) {
            ancestorsIncludingSelf.put(current.getName(), current);
            current = current.getParent();
        }
        return ancestorsIncludingSelf;
    }

    public static <T extends tosca.nodes.Root> Set<T> getNodeInstancesByType(Map<String, tosca.nodes.Root> nodeInstances, Class<T> type) {
        return nodeInstances.values().stream().filter(nodeInstance ->
                type.isAssignableFrom(nodeInstance.getClass())
        ).map(nodeInstance -> (T) nodeInstance).collect(Collectors.toSet());
    }

    public static <T extends tosca.relationships.Root> Set<T> getRelationshipInstancesFromSource(Set<Root> relationshipInstances, String sourceId, Class<T> type) {
        return getRelationshipInstanceBySourceId(relationshipInstances, sourceId).stream().filter(relationshipInstance ->
                type.isAssignableFrom(relationshipInstance.getClass())
        ).map(relationshipInstance -> (T) relationshipInstance).collect(Collectors.toSet());
    }

    public static <T extends tosca.relationships.Root> Set<T> getRelationshipInstancesFromTarget(Set<Root> relationshipInstances, String targetId, Class<T> type) {
        return getRelationshipInstanceByTargetId(relationshipInstances, targetId).stream().filter(relationshipInstance ->
                type.isAssignableFrom(relationshipInstance.getClass())
        ).map(relationshipInstance -> (T) relationshipInstance).collect(Collectors.toSet());
    }

    public static <T extends tosca.nodes.Root, U extends tosca.relationships.Root> Set<T> getSourceInstancesOfRelationship(Set<Root> relationshipInstances, String targetId, Class<U> relationshipType, Class<T> sourceType) {
        Set<U> relationships = getRelationshipInstancesFromTarget(relationshipInstances, targetId, relationshipType);
        return relationships.stream().filter(relationship ->
                sourceType.isAssignableFrom(relationship.getSource().getClass())
        ).map(relationship -> (T) relationship.getSource()).collect(Collectors.toSet());
    }

    public static <T extends tosca.nodes.Root, U extends tosca.relationships.Root> Set<T> getTargetInstancesOfRelationship(Set<Root> relationshipInstances, String sourceId, Class<U> relationshipType, Class<T> targetType) {
        Set<U> relationships = getRelationshipInstancesFromSource(relationshipInstances, sourceId, relationshipType);
        return relationships.stream().filter(relationship ->
                targetType.isAssignableFrom(relationship.getTarget().getClass())
        ).map(relationship -> (T) relationship.getTarget()).collect(Collectors.toSet());
    }
}