package com.toscaruntime.sdk.util;

import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.toscaruntime.sdk.model.AbstractRuntimeType;
import com.toscaruntime.sdk.workflow.WorkflowExecution;
import com.toscaruntime.sdk.workflow.tasks.InstallLifeCycleTasks;
import com.toscaruntime.sdk.workflow.tasks.MockNodeTask;
import com.toscaruntime.sdk.workflow.tasks.MockRelationshipTask;
import com.toscaruntime.sdk.workflow.tasks.RelationshipInstallLifeCycleTasks;
import com.toscaruntime.sdk.workflow.tasks.RelationshipUninstallLifeCycleTasks;
import com.toscaruntime.sdk.workflow.tasks.UninstallLifeCycleTasks;
import com.toscaruntime.sdk.workflow.tasks.nodes.AbstractNodeTask;
import com.toscaruntime.sdk.workflow.tasks.relationships.AbstractRelationshipTask;

import tosca.nodes.Root;

public class WorkflowUtil {


    private static void refreshAttributes(Map<String, Root> nodeInstances,
                                          Set<tosca.relationships.Root> relationshipInstances) {
        nodeInstances.values().forEach(Root::refreshAttributes);
        relationshipInstances.forEach(tosca.relationships.Root::refreshAttributes);
    }

    public static void refreshDeploymentState(Map<String, tosca.nodes.Root> nodeInstances,
                                              Set<tosca.relationships.Root> relationshipInstances,
                                              AbstractRuntimeType instance,
                                              String newState,
                                              boolean refreshAttributes) {
        if (StringUtils.isNotBlank(newState)) {
            instance.setState(newState);
        }
        if (refreshAttributes) {
            refreshAttributes(nodeInstances, relationshipInstances);
        }
    }

    public static void changeRelationshipState(
            tosca.relationships.Root relationshipInstance,
            Map<String, Root> nodeInstances,
            Set<tosca.relationships.Root> relationshipInstances,
            String fromState,
            String toState) {
        // This will prevent concurrent state changes
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (relationshipInstance) {
            if (relationshipInstance.getState().equals(fromState)) {
                refreshDeploymentState(nodeInstances, relationshipInstances, relationshipInstance, toState, true);
            } else {
                refreshDeploymentState(nodeInstances, relationshipInstances, relationshipInstance, fromState, true);
            }
        }
    }

    public static void declareNodeInstallDependencies(InstallLifeCycleTasks installLifeCycleTasks) {
        // Configure is executed after create
        installLifeCycleTasks.getConfigureTask().dependsOn(installLifeCycleTasks.getCreateTask());
        // Start is executed after configure
        installLifeCycleTasks.getStartTask().dependsOn(installLifeCycleTasks.getConfigureTask());
    }

    public static void declareNodeUninstallDependencies(UninstallLifeCycleTasks uninstallLifeCycleTasks) {
        // Delete is executed after stop
        uninstallLifeCycleTasks.getDeleteTask().dependsOn(uninstallLifeCycleTasks.getStopTask());
    }

    public static void declareRelationshipInstallDependencies(RelationshipInstallLifeCycleTasks relationshipInstallLifeCycleTasks, InstallLifeCycleTasks sourceInstallLifeCycleTasks, InstallLifeCycleTasks targetInstallLifeCycleTasks) {

        // Dependencies between tasks of the relationship and the source
        // Pre-configure Source is executed after create source
        relationshipInstallLifeCycleTasks.getPreConfigureSourceTask().dependsOn(sourceInstallLifeCycleTasks.getCreateTask());
        // Configure source is executed after pre-configure source
        sourceInstallLifeCycleTasks.getConfigureTask().dependsOn(relationshipInstallLifeCycleTasks.getPreConfigureSourceTask());
        // Post-configure source is executed after configure source
        relationshipInstallLifeCycleTasks.getPostConfigureSourceTask().dependsOn(sourceInstallLifeCycleTasks.getConfigureTask());
        // Start source is executed after post-configure source
        sourceInstallLifeCycleTasks.getStartTask().dependsOn(relationshipInstallLifeCycleTasks.getPostConfigureSourceTask());
        // Add target is executed after start source
        relationshipInstallLifeCycleTasks.getAddTargetTask().dependsOn(sourceInstallLifeCycleTasks.getStartTask());

        // Dependencies between tasks of the relationship and the target
        // Pre-configure target is executed after create target
        relationshipInstallLifeCycleTasks.getPreConfigureTargetTask().dependsOn(targetInstallLifeCycleTasks.getCreateTask());
        // Configure target is executed after pre-configure target
        targetInstallLifeCycleTasks.getConfigureTask().dependsOn(relationshipInstallLifeCycleTasks.getPreConfigureTargetTask());
        // Post-configure target is executed after configure target
        relationshipInstallLifeCycleTasks.getPostConfigureTargetTask().dependsOn(targetInstallLifeCycleTasks.getConfigureTask());
        // Start target is executed after post-configure target
        targetInstallLifeCycleTasks.getStartTask().dependsOn(relationshipInstallLifeCycleTasks.getPostConfigureTargetTask());
        // Add source is executed after start target
        relationshipInstallLifeCycleTasks.getAddSourceTask().dependsOn(targetInstallLifeCycleTasks.getStartTask());
    }

    public static void declareRelationshipUninstallDependencies(RelationshipUninstallLifeCycleTasks relationshipUninstallLifeCycleTasks, UninstallLifeCycleTasks sourceUninstallLifeCycleTasks, UninstallLifeCycleTasks targetUninstallLifeCycleTasks) {
        // Target stop is executed after remove source
        targetUninstallLifeCycleTasks.getStopTask().dependsOn(relationshipUninstallLifeCycleTasks.getRemoveSourceTask());
        // Source stop is executed after remove target
        sourceUninstallLifeCycleTasks.getStopTask().dependsOn(relationshipUninstallLifeCycleTasks.getRemoveTargetTask());
    }

    public static void declareDependsOnInstallDependencies(RelationshipInstallLifeCycleTasks relationshipInstallLifeCycleTasks, InstallLifeCycleTasks sourceInstallLifeCycleTasks, InstallLifeCycleTasks targetInstallLifeCycleTasks) {
        // source(A) depends on target(B)
        // A depends on B then A is configured only after B is started
        relationshipInstallLifeCycleTasks.getPreConfigureSourceTask().dependsOn(targetInstallLifeCycleTasks.getStartTask());
        // A depends on B then B is configured only after A is created
        relationshipInstallLifeCycleTasks.getPreConfigureTargetTask().dependsOn(sourceInstallLifeCycleTasks.getCreateTask());
        // A depends on B then the appearance of A is notified only after A is started
        relationshipInstallLifeCycleTasks.getAddSourceTask().dependsOn(sourceInstallLifeCycleTasks.getStartTask());
        // A depends on B then the appearance of B is notified only after B is started
        relationshipInstallLifeCycleTasks.getAddTargetTask().dependsOn(targetInstallLifeCycleTasks.getStartTask());
    }

    public static void declareDependsOnUninstallDependencies(RelationshipUninstallLifeCycleTasks relationshipInstallLifeCycleTasks, UninstallLifeCycleTasks sourceUninstallLifeCycleTasks, UninstallLifeCycleTasks targetUninstallLifeCycleTasks) {
        // source(A) depends on target(B)
        // If A depends on B, then B stops after A stops
        targetUninstallLifeCycleTasks.getStopTask().dependsOn(sourceUninstallLifeCycleTasks.getStopTask());
        // If A depends on B then B is stopped after the removal of B is notified
        targetUninstallLifeCycleTasks.getStopTask().dependsOn(relationshipInstallLifeCycleTasks.getRemoveTargetTask());
        // If A depends on B then  A is stopped after the removal of A is notified
        sourceUninstallLifeCycleTasks.getStopTask().dependsOn(relationshipInstallLifeCycleTasks.getRemoveSourceTask());
    }

    public static void declareHostedOnInstallDependencies(RelationshipInstallLifeCycleTasks relationshipInstallLifeCycleTasks, InstallLifeCycleTasks childLifeCycleTasks, InstallLifeCycleTasks hostLifeCycleTasks) {
        // child(A) hosted on host(B)
        // A hosted on B then A is created after B is started
        childLifeCycleTasks.getCreateTask().dependsOn(hostLifeCycleTasks.getStartTask());
        // A hosted on B then the appearance of A is notified only after A is started
        relationshipInstallLifeCycleTasks.getAddSourceTask().dependsOn(childLifeCycleTasks.getStartTask());
        // A hosted on B then the appearance of B is notified only after B is started
        relationshipInstallLifeCycleTasks.getAddTargetTask().dependsOn(hostLifeCycleTasks.getStartTask());
    }

    public static void declareHostedOnUninstallDependencies(RelationshipUninstallLifeCycleTasks relationshipInstallLifeCycleTasks, UninstallLifeCycleTasks childLifeCycleTasks, UninstallLifeCycleTasks hostLifeCycleTasks) {
        // child(A) hosted on host(B)
        // A hosted on B then B is stopped after A is deleted
        hostLifeCycleTasks.getStopTask().dependsOn(childLifeCycleTasks.getDeleteTask());
        // If A hosted on B then B is stopped after the removal of B is notified
        hostLifeCycleTasks.getStopTask().dependsOn(relationshipInstallLifeCycleTasks.getRemoveTargetTask());
        // If A hosted on B then  A is stopped after the removal of A is notified
        childLifeCycleTasks.getStopTask().dependsOn(relationshipInstallLifeCycleTasks.getRemoveSourceTask());
    }

    public static InstallLifeCycleTasks mockInstallLifeCycleTasks(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, WorkflowExecution workflowExecution) {
        AbstractNodeTask createTask = new MockNodeTask("create", nodeInstances, relationshipInstances, nodeInstance, workflowExecution);
        AbstractNodeTask configureTask = new MockNodeTask("configure", nodeInstances, relationshipInstances, nodeInstance, workflowExecution);
        AbstractNodeTask startTask = new MockNodeTask("start", nodeInstances, relationshipInstances, nodeInstance, workflowExecution);
        return new InstallLifeCycleTasks(createTask, configureTask, startTask);
    }

    public static RelationshipInstallLifeCycleTasks mockRelationshipInstallLifeCycleTasks(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, tosca.relationships.Root relationshipInstance, WorkflowExecution workflowExecution) {
        AbstractRelationshipTask preConfigureSourceTask = new MockRelationshipTask("preConfigureSource", nodeInstances, relationshipInstances, relationshipInstance, workflowExecution);
        AbstractRelationshipTask preConfigureTargetTask = new MockRelationshipTask("preConfigureTarget", nodeInstances, relationshipInstances, relationshipInstance, workflowExecution);
        AbstractRelationshipTask postConfigureSourceTask = new MockRelationshipTask("postConfigureSource", nodeInstances, relationshipInstances, relationshipInstance, workflowExecution);
        AbstractRelationshipTask postConfigureTargetTask = new MockRelationshipTask("postConfigureTarget", nodeInstances, relationshipInstances, relationshipInstance, workflowExecution);
        AbstractRelationshipTask addSourceTask = new MockRelationshipTask("addSource", nodeInstances, relationshipInstances, relationshipInstance, workflowExecution);
        AbstractRelationshipTask addTargetTask = new MockRelationshipTask("addTarget", nodeInstances, relationshipInstances, relationshipInstance, workflowExecution);
        return new RelationshipInstallLifeCycleTasks(preConfigureSourceTask, preConfigureTargetTask, postConfigureSourceTask, postConfigureTargetTask, addSourceTask, addTargetTask);
    }

    public static UninstallLifeCycleTasks mockUninstallLifeCycleTasks(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, WorkflowExecution workflowExecution) {
        AbstractNodeTask stopTask = new MockNodeTask("stop", nodeInstances, relationshipInstances, nodeInstance, workflowExecution);
        AbstractNodeTask deleteTask = new MockNodeTask("delete", nodeInstances, relationshipInstances, nodeInstance, workflowExecution);
        return new UninstallLifeCycleTasks(stopTask, deleteTask);
    }

    public static RelationshipUninstallLifeCycleTasks mockRelationshipUninstallLifeCycleTasks(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, tosca.relationships.Root relationshipInstance, WorkflowExecution workflowExecution) {
        AbstractRelationshipTask removeSource = new MockRelationshipTask("removeSource", nodeInstances, relationshipInstances, relationshipInstance, workflowExecution);
        AbstractRelationshipTask removeTarget = new MockRelationshipTask("removeTarget", nodeInstances, relationshipInstances, relationshipInstance, workflowExecution);
        return new RelationshipUninstallLifeCycleTasks(removeSource, removeTarget);
    }
}
