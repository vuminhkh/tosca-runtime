package com.toscaruntime.sdk.util;

import com.toscaruntime.sdk.model.DeploymentRelationshipNode;
import tosca.relationships.Root;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@SuppressWarnings("unchecked")
public class DeploymentUtil {

    public static DeploymentRelationshipNode getRelationshipNodeBySourceNameTargetName(Set<DeploymentRelationshipNode> relationshipNodes, String sourceName, String targetName, String relationshipType) {
        for (DeploymentRelationshipNode relationshipNode : relationshipNodes) {
            if (relationshipNode.getSourceNodeId().equals(sourceName) && relationshipNode.getTargetNodeId().equals(targetName) && relationshipNode.getRelationshipName().equals(relationshipType)) {
                return relationshipNode;
            }
        }
        return null;
    }

    public static Set<tosca.nodes.Root> getChildren(Map<String, tosca.nodes.Root> nodeInstances, tosca.nodes.Root ofInstance) {
        return nodeInstances.values().stream().filter(nodeInstance -> nodeInstance.getParent() != null && nodeInstance.getParent().equals(ofInstance)).collect(Collectors.toSet());
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

    public static <T extends tosca.relationships.Root> Set<T> getRelationshipInstancesByTypes(Set<tosca.relationships.Root> relationshipInstances, Class<?> sourceType, Class<?> targetType, Class<T> relationshipType) {
        return relationshipInstances.stream().filter(relationshipInstance ->
                relationshipType.isAssignableFrom(relationshipInstance.getClass()) &&
                        sourceType.isAssignableFrom(relationshipInstance.getSource().getClass()) &&
                        targetType.isAssignableFrom(relationshipInstance.getTarget().getClass())
        ).map(relationshipInstance -> (T) relationshipInstance).collect(Collectors.toSet());
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

    public static <T extends tosca.nodes.Root> Map<String, T> toMap(Set<T> instances) {
        Map<String, T> instanceMap = new HashMap<>();
        for (T instance : instances) {
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

    public static Set<tosca.nodes.Root> getTargetInstancesOfRelationship(Set<Root> relationshipInstances, String sourceId) {
        return relationshipInstances.stream().filter(relationship -> relationship.getSource().getId().equals(sourceId)).map(Root::getTarget).collect(Collectors.toSet());
    }

    public static void runWithClassLoader(ClassLoader classLoader, Runnable runnable) {
        ClassLoader current = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(classLoader);
            runnable.run();
        } finally {
            Thread.currentThread().setContextClassLoader(current);
        }
    }

    public interface Callable<T> {
        T call();
    }

    public static <T> T runWithClassLoader(ClassLoader classLoader, Callable<T> runnable) {
        ClassLoader current = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(classLoader);
            return runnable.call();
        } finally {
            Thread.currentThread().setContextClassLoader(current);
        }
    }
}