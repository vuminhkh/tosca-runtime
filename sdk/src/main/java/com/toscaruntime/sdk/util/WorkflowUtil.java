package com.toscaruntime.sdk.util;

import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import com.toscaruntime.sdk.model.AbstractRuntimeType;
import com.toscaruntime.sdk.workflow.tasks.AbstractTask;
import com.toscaruntime.sdk.workflow.tasks.InstallLifeCycleTasks;
import com.toscaruntime.sdk.workflow.tasks.MockTask;
import com.toscaruntime.sdk.workflow.tasks.UninstallLifeCycleTasks;

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

    /**
     * Create a mock by copying attributes from the given task
     *
     * @param taskName name of the task
     * @param copyFrom the task to copy from
     * @return mock task
     */
    public static MockTask mockTask(String taskName, AbstractTask copyFrom) {
        return new MockTask(taskName, copyFrom.getNodeInstances(), copyFrom.getRelationshipInstances(), copyFrom.getNodeInstance(), copyFrom.getWorkflowExecution());
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
        // Dependencies between tasks of the same node instance are declared here
        // Pre configure source and target of the instance are executed after create
        installLifeCycleTasks.getPreConfigureSourceTask().dependsOn(installLifeCycleTasks.getCreateTask());
        installLifeCycleTasks.getPreConfigureTargetTask().dependsOn(installLifeCycleTasks.getCreateTask());
        // Configure is executed after pre configure source and target
        installLifeCycleTasks.getConfigureTask().dependsOn(installLifeCycleTasks.getPreConfigureSourceTask(), installLifeCycleTasks.getPreConfigureTargetTask());
        // Post configure source and target are executed after configure
        installLifeCycleTasks.getPostConfigureSourceTask().dependsOn(installLifeCycleTasks.getConfigureTask());
        installLifeCycleTasks.getPostConfigureTargetTask().dependsOn(installLifeCycleTasks.getConfigureTask());
        // Start is executed after post configure source and target
        installLifeCycleTasks.getStartTask().dependsOn(installLifeCycleTasks.getPostConfigureSourceTask(), installLifeCycleTasks.getPostConfigureTargetTask());
        // Add source and target are executed after start
        installLifeCycleTasks.getAddSourceTask().dependsOn(installLifeCycleTasks.getStartTask());
        installLifeCycleTasks.getAddTargetTask().dependsOn(installLifeCycleTasks.getStartTask());
    }

    public static void declareNodeUninstallDependencies(UninstallLifeCycleTasks uninstallLifeCycleTasks) {
        // Dependencies between tasks of the same node instance are declared here
        // Remove source and remove target are executed before stop
        uninstallLifeCycleTasks.getStopTask().dependsOn(uninstallLifeCycleTasks.getRemoveSourceTask(), uninstallLifeCycleTasks.getRemoveTargetTask());
        // Delete is executed after stop
        uninstallLifeCycleTasks.getDeleteTask().dependsOn(uninstallLifeCycleTasks.getStopTask());
    }

    public static void declareDependsOnInstallDependencies(InstallLifeCycleTasks fromLifeCycleTasks, InstallLifeCycleTasks toLifeCycleTasks) {
        // from(A) depends on to(B)
        // A depends on B then A is configured only if B is started
        fromLifeCycleTasks.getPreConfigureSourceTask().dependsOn(toLifeCycleTasks.getStartTask());
        // A depends on B then B is configured only if A is created
        toLifeCycleTasks.getPreConfigureTargetTask().dependsOn(fromLifeCycleTasks.getCreateTask());
        // A depends on B then B add source is executed only if A is started
        toLifeCycleTasks.getAddSourceTask().dependsOn(fromLifeCycleTasks.getStartTask());
    }

    public static void declareDependsOnUninstallDependencies(UninstallLifeCycleTasks fromLifeCycleTasks, UninstallLifeCycleTasks toLifeCycleTasks) {
        // If A is depended by B, then only stop A if B has been stopped already
        fromLifeCycleTasks.getStopTask().dependsOn(toLifeCycleTasks.getStopTask());
        // If A is depended by B, then notify A removal before B is stopped
        toLifeCycleTasks.getStopTask().dependsOn(fromLifeCycleTasks.getRemoveSourceTask());
    }

    public static void declareHostedOnInstallDependencies(InstallLifeCycleTasks childLifeCycleTasks, InstallLifeCycleTasks hostLifeCycleTasks) {
        // Instance create task must depends on start task of its host
        // It means that the life cycle of the host and the hosted is sequential
        childLifeCycleTasks.getCreateTask().dependsOn(hostLifeCycleTasks.getStartTask());
        // Add source of the the host depends on start of the instance, this way add source is called only when the source is already started
        hostLifeCycleTasks.getAddSourceTask().dependsOn(childLifeCycleTasks.getStartTask());
    }

    public static void declareHostedOnUninstallDependencies(UninstallLifeCycleTasks childLifeCycleTasks, UninstallLifeCycleTasks hostLifeCycleTasks) {
        // The host can be stopped only if all children has been deleted
        hostLifeCycleTasks.getStopTask().dependsOn(childLifeCycleTasks.getDeleteTask());
        // The child can be stopped only if the host has been notified of the fact that it's being removed
        childLifeCycleTasks.getStopTask().dependsOn(hostLifeCycleTasks.getRemoveSourceTask());
    }

    public static void declareNodeInstallDependenciesWithRelationship(Root instance, InstallLifeCycleTasks instanceLifeCycle, Map<Root, InstallLifeCycleTasks> allTasks, Set<tosca.relationships.Root> relationshipInstances) {
        WorkflowUtil.declareNodeInstallDependencies(instanceLifeCycle);
        Root host = instance.getHost();
        InstallLifeCycleTasks hostInstallLifeCycle = allTasks.get(host);
        if (hostInstallLifeCycle != null) {
            WorkflowUtil.declareHostedOnInstallDependencies(instanceLifeCycle, hostInstallLifeCycle);
        }
        Set<Root> dependencies = DeploymentUtil.getTargetInstancesOfRelationship(relationshipInstances, instance.getId());
        dependencies.stream().filter(dependency -> instance.getNode().getDependsOnNodes().contains(dependency.getName())).forEach(dependency -> {
            // instance = A depends on dependency = B
            InstallLifeCycleTasks dependencyLifeCycle = allTasks.get(dependency);
            if (dependencyLifeCycle != null) {
                WorkflowUtil.declareDependsOnInstallDependencies(instanceLifeCycle, dependencyLifeCycle);
            }
        });
    }

    public static void declareNodeUninstallDependenciesWithRelationship(Root instance, UninstallLifeCycleTasks instanceLifeCycle, Map<Root, UninstallLifeCycleTasks> allTasks, Set<tosca.relationships.Root> relationshipInstances) {
        WorkflowUtil.declareNodeUninstallDependencies(instanceLifeCycle);
        Root host = instance.getHost();
        UninstallLifeCycleTasks hostLifeCycle = allTasks.get(host);
        if (hostLifeCycle != null) {
            WorkflowUtil.declareHostedOnUninstallDependencies(instanceLifeCycle, hostLifeCycle);
        }
        Set<Root> dependencies = DeploymentUtil.getTargetInstancesOfRelationship(relationshipInstances, instance.getId());
        dependencies.stream().filter(dependency -> instance.getNode().getDependsOnNodes().contains(dependency.getName())).forEach(dependency -> {
            // instance = A depends on dependency = B
            UninstallLifeCycleTasks dependencyLifeCycle = allTasks.get(dependency);
            if (dependencyLifeCycle != null) {
                WorkflowUtil.declareDependsOnUninstallDependencies(instanceLifeCycle, dependencyLifeCycle);
            }
        });
    }
}
