package com.toscaruntime.sdk.workflow;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.toscaruntime.exception.ToscaRuntimeException;
import com.toscaruntime.exception.WorkflowExecutionException;
import com.toscaruntime.sdk.model.AbstractRuntimeType;
import com.toscaruntime.sdk.util.DeploymentUtil;
import com.toscaruntime.sdk.workflow.executors.Parallel;
import com.toscaruntime.sdk.workflow.executors.Sequence;
import com.toscaruntime.sdk.workflow.executors.Task;
import com.toscaruntime.sdk.workflow.executors.TaskExecutorFactory;

import tosca.nodes.Root;

/**
 * The default workflow engine for toscaruntime
 *
 * @author Minh Khang VU
 */
public class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

    private void refreshAttributes(Map<String, tosca.nodes.Root> nodeInstances,
                                   Set<tosca.relationships.Root> relationshipInstances) {
        nodeInstances.values().forEach(Root::refreshAttributes);
        relationshipInstances.forEach(tosca.relationships.Root::refreshAttributes);
    }

    private void refreshDeploymentState(Map<String, tosca.nodes.Root> nodeInstances,
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
     * Perform installation of the given set of node instances and relationships instances
     *
     * @param nodeInstances         node instances to be installed
     * @param relationshipInstances relationship instances to be installed
     */
    public void install(Map<String, tosca.nodes.Root> nodeInstances,
                        Set<tosca.relationships.Root> relationshipInstances) {
        log.info("Begin to run install workflow");
        Sequence installSequence = new Sequence();
        Set<Root> waitForCreatedQueue = new HashSet<>(nodeInstances.values());
        Set<tosca.nodes.Root> waitForStartedQueue = new HashSet<>(nodeInstances.values());
        int waitForCreatedQueueSize = nodeInstances.size();
        int waitForStartedQueueSize = nodeInstances.size();
        while (waitForCreatedQueueSize > 0 || waitForStartedQueueSize > 0) {
            installSequence.getActionList().add(buildInstallWorkflowStep(nodeInstances, relationshipInstances, waitForCreatedQueue, waitForStartedQueue));
            if (waitForCreatedQueue.size() >= waitForCreatedQueueSize && waitForStartedQueue.size() >= waitForStartedQueueSize) {
                throw new ToscaRuntimeException("Detected cyclic dependencies in topology");
            } else {
                waitForStartedQueueSize = waitForStartedQueue.size();
                waitForCreatedQueueSize = waitForCreatedQueue.size();
            }
        }
        try {
            TaskExecutorFactory.getSequenceExecutor().execute(installSequence);
            log.info("Finished to run install workflow");
        } catch (WorkflowExecutionException e) {
            log.error("Workflow install execution failed", e);
            throw e;
        }
    }

    /**
     * Perform un-installation of the given set of node instances and relationships instances
     *
     * @param nodeInstances         node instances to be uninstalled
     * @param relationshipInstances relationship instances to be uninstalled
     */
    private Sequence buildInstallWorkflowStep(Map<String, tosca.nodes.Root> nodeInstances,
                                              Set<tosca.relationships.Root> relationshipInstances,
                                              final Set<Root> waitForCreatedQueue,
                                              final Set<tosca.nodes.Root> waitForStartedQueue) {
        Sequence step = new Sequence();
        Parallel createParallel = new Parallel();
        Set<Root> processedCreated = new HashSet<>();
        for (final tosca.nodes.Root nodeInstance : waitForCreatedQueue) {
            // Only run create + configure unless if the node has no direct host or its direct host has been started
            if (nodeInstance.getHost() == null || !waitForStartedQueue.contains(nodeInstance.getHost())) {
                // Check if all dependencies have been satisfied in order to create
                boolean dependenciesSatisfied = true;
                for (String dependsOnNode : nodeInstance.getNode().getDependsOnNodes()) {
                    if (waitForStartedQueue.stream().anyMatch(instance -> instance.getName().equals(dependsOnNode))) {
                        dependenciesSatisfied = false;
                        break;
                    }
                }
                if (dependenciesSatisfied) {
                    createParallel.getActionList().add(new Task() {
                        @Override
                        public void run() {
                            refreshDeploymentState(nodeInstances, relationshipInstances, nodeInstance, "creating", false);
                            nodeInstance.create();
                            refreshDeploymentState(nodeInstances, relationshipInstances, nodeInstance, "created", true);
                            Set<tosca.relationships.Root> nodeInstanceSourceRelationships = DeploymentUtil.getRelationshipInstanceBySourceId(relationshipInstances, nodeInstance.getId());
                            for (tosca.relationships.Root relationship : nodeInstanceSourceRelationships) {
                                refreshDeploymentState(nodeInstances, relationshipInstances, relationship, "preConfiguringSource", false);
                                relationship.preConfigureSource();
                                refreshDeploymentState(nodeInstances, relationshipInstances, relationship, "preConfiguredSource", true);
                            }
                            Set<tosca.relationships.Root> nodeInstanceTargetRelationships = DeploymentUtil.getRelationshipInstanceByTargetId(relationshipInstances, nodeInstance.getId());
                            for (tosca.relationships.Root relationship : nodeInstanceTargetRelationships) {
                                refreshDeploymentState(nodeInstances, relationshipInstances, relationship, "preConfiguringTarget", false);
                                relationship.preConfigureTarget();
                                refreshDeploymentState(nodeInstances, relationshipInstances, relationship, "preConfiguredTarget", true);
                            }
                            nodeInstance.configure();
                            nodeInstance.setState("configured");
                            for (tosca.relationships.Root relationship : nodeInstanceSourceRelationships) {
                                refreshDeploymentState(nodeInstances, relationshipInstances, relationship, "postConfiguringSource", false);
                                relationship.postConfigureSource();
                                refreshDeploymentState(nodeInstances, relationshipInstances, relationship, "postConfiguredSource", true);
                            }
                            for (tosca.relationships.Root relationship : nodeInstanceTargetRelationships) {
                                refreshDeploymentState(nodeInstances, relationshipInstances, relationship, "postConfiguringTarget", false);
                                relationship.postConfigureTarget();
                                refreshDeploymentState(nodeInstances, relationshipInstances, relationship, "postConfiguredTarget", true);
                            }
                        }
                    });
                    processedCreated.add(nodeInstance);
                }
            }
        }
        waitForCreatedQueue.removeAll(processedCreated);
        if (!createParallel.getActionList().isEmpty()) {
            step.getActionList().add(createParallel);
        }
        Parallel startParallel = new Parallel();
        Set<Root> processedStarted = new HashSet<>();
        for (final tosca.nodes.Root nodeInstance : waitForStartedQueue) {
            if (!waitForCreatedQueue.contains(nodeInstance)) {
                startParallel.getActionList().add(new Task() {
                    @Override
                    public void run() {
                        nodeInstance.start();
                        nodeInstance.setState("started");
                        Set<tosca.relationships.Root> nodeInstanceSourceRelationships = DeploymentUtil.getRelationshipInstanceBySourceId(relationshipInstances, nodeInstance.getId());
                        for (tosca.relationships.Root relationship : nodeInstanceSourceRelationships) {
                            if (!waitForStartedQueue.contains(relationship.getTarget())) {
                                refreshDeploymentState(nodeInstances, relationshipInstances, relationship, "addingSource", false);
                                relationship.addSource();
                                refreshDeploymentState(nodeInstances, relationshipInstances, relationship, "addedSource", true);
                                refreshDeploymentState(nodeInstances, relationshipInstances, relationship, "addingTarget", false);
                                relationship.addTarget();
                                refreshDeploymentState(nodeInstances, relationshipInstances, relationship, "addedTarget", true);
                            }
                        }
                    }
                });
                processedStarted.add(nodeInstance);
            }
        }
        waitForStartedQueue.removeAll(processedStarted);
        if (!startParallel.getActionList().isEmpty()) {
            step.getActionList().add(startParallel);
        }
        return step;
    }

    public void uninstall(Map<String, tosca.nodes.Root> nodeInstances,
                          Set<tosca.relationships.Root> relationshipInstances) {
        log.info("Begin to run uninstall workflow");
        Sequence uninstallSequence = new Sequence();
        Set<tosca.nodes.Root> waitForStoppedQueue = new HashSet<>(nodeInstances.values());
        Set<tosca.nodes.Root> waitForDeletedQueue = new HashSet<>(nodeInstances.values());
        int waitForStoppedQueueSize = nodeInstances.size();
        int waitForDeletedQueueSize = nodeInstances.size();
        while (waitForStoppedQueueSize > 0 || waitForDeletedQueueSize > 0) {
            uninstallSequence.getActionList().add(buildUnInstallWorkflowStep(nodeInstances, relationshipInstances, waitForStoppedQueue, waitForDeletedQueue));
            if (waitForStoppedQueue.size() >= waitForStoppedQueueSize && waitForDeletedQueue.size() >= waitForDeletedQueueSize) {
                throw new ToscaRuntimeException("Detected cyclic dependencies in topology");
            } else {
                waitForDeletedQueueSize = waitForDeletedQueue.size();
                waitForStoppedQueueSize = waitForStoppedQueue.size();
            }
        }
        TaskExecutorFactory.getSequenceExecutor().execute(uninstallSequence);
        log.info("Finished to run uninstall workflow");
    }

    private Sequence buildUnInstallWorkflowStep(Map<String, tosca.nodes.Root> nodeInstances,
                                                Set<tosca.relationships.Root> relationshipInstances,
                                                final Set<Root> waitForStoppedQueue,
                                                final Set<tosca.nodes.Root> waitForDeletedQueue) {
        Sequence step = new Sequence();
        Parallel stopParallel = new Parallel();
        Set<Root> processedStopped = new HashSet<>();
        for (final tosca.nodes.Root nodeInstance : waitForStoppedQueue) {
            // Only run stop unless if the node has no children or all of its children has been deleted
            if (nodeInstance.getChildren() == null || nodeInstance.getChildren().isEmpty()
                    || Collections.disjoint(waitForDeletedQueue, nodeInstance.getChildren())) {
                // Check if no other nodes depend on this one before to stop
                boolean notDependedByAnyNode = true;
                for (String dependedByNode : nodeInstance.getNode().getDependedByNodes()) {
                    if (waitForStoppedQueue.stream().anyMatch(instance -> instance.getName().equals(dependedByNode))) {
                        notDependedByAnyNode = false;
                        break;
                    }
                }
                if (notDependedByAnyNode) {
                    stopParallel.getActionList().add(new Task() {
                        @Override
                        public void run() {
                            try {
                                nodeInstance.stop();
                            } catch (Exception e) {
                                log.warn(nodeInstance + " stop failed", e);
                            }
                            nodeInstance.setState("stopped");
                            Set<tosca.relationships.Root> nodeInstanceSourceRelationships = DeploymentUtil.getRelationshipInstanceBySourceId(relationshipInstances, nodeInstance.getId());
                            for (tosca.relationships.Root relationship : nodeInstanceSourceRelationships) {
                                refreshDeploymentState(nodeInstances, relationshipInstances, relationship, "removingSource", false);
                                try {
                                    relationship.removeSource();
                                } catch (Exception e) {
                                    log.warn(relationship + " removedSource failed", e);
                                }
                                refreshDeploymentState(nodeInstances, relationshipInstances, relationship, "removedSource", true);
                            }
                            Set<tosca.relationships.Root> nodeInstanceTargetRelationships = DeploymentUtil.getRelationshipInstanceByTargetId(relationshipInstances, nodeInstance.getId());
                            for (tosca.relationships.Root relationship : nodeInstanceTargetRelationships) {
                                refreshDeploymentState(nodeInstances, relationshipInstances, relationship, "removingTarget", false);
                                try {
                                    relationship.removeTarget();
                                } catch (Exception e) {
                                    log.warn(relationship + " removeTarget failed", e);
                                }
                                refreshDeploymentState(nodeInstances, relationshipInstances, relationship, "removedTarget", true);
                            }
                        }
                    });
                    processedStopped.add(nodeInstance);
                }
            }
        }
        waitForStoppedQueue.removeAll(processedStopped);
        step.getActionList().add(stopParallel);
        Parallel deleteParallel = new Parallel();
        Set<Root> processedDeleted = new HashSet<>();
        for (final tosca.nodes.Root nodeInstance : waitForDeletedQueue) {
            // Only run delete if the node instance has been stopped
            if (!waitForStoppedQueue.contains(nodeInstance)) {
                deleteParallel.getActionList().add(new Task() {
                    @Override
                    public void run() {
                        refreshDeploymentState(nodeInstances, relationshipInstances, nodeInstance, "deleting", false);
                        try {
                            nodeInstance.delete();
                        } catch (Exception e) {
                            log.warn(nodeInstance + " delete failed", e);
                        }
                        refreshDeploymentState(nodeInstances, relationshipInstances, nodeInstance, "deleted", true);
                    }
                });
                processedDeleted.add(nodeInstance);
            }
        }
        waitForDeletedQueue.removeAll(processedDeleted);
        step.getActionList().add(deleteParallel);
        return step;
    }
}
