package com.mkv.tosca.sdk;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mkv.exception.NonRecoverableException;
import com.mkv.tosca.constant.CompilerConstant;
import com.mkv.tosca.sdk.workflow.Parallel;
import com.mkv.tosca.sdk.workflow.Sequence;
import com.mkv.tosca.sdk.workflow.Task;
import com.mkv.tosca.sdk.workflow.TaskExecutorFactory;

import tosca.nodes.Root;

/**
 * This represents a runtime topology
 *
 * @author Minh Khang VU
 */
public abstract class Deployment {

    private static final Logger log = LoggerFactory.getLogger(Deployment.class);

    protected DeploymentConfig config;

    /**
     * id to node instance : A node instance is a physical component of the topology at runtime.
     */
    protected Map<String, tosca.nodes.Root> nodeInstances = Maps.newLinkedHashMap();

    /**
     * A relationship instance is a link between 2 physical components of the topology
     */
    protected List<tosca.relationships.Root> relationshipInstances = Lists.newArrayList();

    protected Map<String, DeploymentNode> nodes = Maps.newHashMap();

    protected List<DeploymentRelationshipNode> relationshipNodes = Lists.newArrayList();

    public List<DeploymentNode> getNodes() {
        return Lists.newArrayList(nodes.values());
    }

    public List<DeploymentRelationshipNode> getRelationshipNodes() {
        return relationshipNodes;
    }

    public void initializeDeployment(Path recipePath, Map<String, Object> inputs, boolean bootstrap) {
        this.config = new DeploymentConfig();
        this.config.setInputs(inputs);
        this.config.setRecipePath(recipePath);
        this.config.setBootstrap(bootstrap);
        this.config.setArtifactsPath(recipePath.resolve(CompilerConstant.ARCHIVE_FOLDER()));
    }

    protected void initializeNode(String nodeName, Map<String, Object> properties) {
        DeploymentNode deploymentNode = new DeploymentNode();
        deploymentNode.setId(nodeName);
        deploymentNode.setProperties(properties);
        deploymentNode.setInstances(Lists.newArrayList());
        this.nodes.put(nodeName, deploymentNode);
    }

    protected void initializeInstance(tosca.nodes.Root instance) {
        instance.setConfig(this.config);
        this.nodes.get(instance.getName()).getInstances().add(instance);
    }

    protected void setDependencies(String nodeName, String... dependencies) {
        Set<Root> instances = getNodeInstancesByNodeName(nodeName);
        for (Root instance : instances) {
            Set<Root> allDependencyInstances = Sets.newHashSet();
            for (String dependency : dependencies) {
                allDependencyInstances.addAll(getNodeInstancesByNodeName(dependency));
            }
            instance.setDependsOnNodes(allDependencyInstances);
        }
    }

    protected void generateRelationships(String sourceName, String targetName, Class<? extends tosca.relationships.Root> relationshipType) {
        DeploymentRelationshipNode relationshipNode = new DeploymentRelationshipNode();
        relationshipNode.setSourceNodeId(sourceName);
        relationshipNode.setTargetNodeId(targetName);
        relationshipNode.setRelationshipInstances(Lists.newArrayList());
        this.relationshipNodes.add(relationshipNode);
        for (Root sourceInstance : getNodeInstancesByNodeName(sourceName)) {
            for (Root targetInstance : getNodeInstancesByNodeName(targetName)) {
                try {
                    tosca.relationships.Root relationshipInstance = relationshipType.newInstance();
                    relationshipInstance.setSource(sourceInstance);
                    relationshipInstance.setTarget(targetInstance);
                    relationshipInstances.add(relationshipInstance);
                    relationshipNode.getRelationshipInstances().add(relationshipInstance);
                } catch (InstantiationException | IllegalAccessException e) {
                    throw new NonRecoverableException("Could not create relationship instance of type " + relationshipType.getName(), e);
                }
            }
        }
    }

    public Set<tosca.nodes.Root> getNodeInstancesByNodeName(String nodeName) {
        Set<tosca.nodes.Root> result = Sets.newHashSet();
        for (tosca.nodes.Root nodeInstance : nodeInstances.values()) {
            if (nodeInstance.getName().equals(nodeName)) {
                result.add(nodeInstance);
            }
        }
        return result;
    }

    public <T extends tosca.nodes.Root> Set<T> getNodeInstancesByType(Class<T> type) {
        Set<T> result = Sets.newHashSet();
        for (tosca.nodes.Root nodeInstance : nodeInstances.values()) {
            if (type.isAssignableFrom(nodeInstance.getClass())) {
                result.add((T) nodeInstance);
            }
        }
        return result;
    }

    public <T extends tosca.relationships.Root> Set<T> getRelationshipInstancesByType(String sourceId, Class<T> type) {
        Set<T> result = Sets.newHashSet();
        for (tosca.relationships.Root relationshipInstance : getRelationshipInstanceBySourceId(sourceId)) {
            if (type.isAssignableFrom(relationshipInstance.getClass())) {
                result.add((T) relationshipInstance);
            }
        }
        return result;
    }

    public <T extends tosca.nodes.Root, U extends tosca.relationships.Root> Set<T> getNodeInstancesByRelationship(String sourceId, Class<U> relationshipType, Class<T> targetType) {
        Set<U> relationships = getRelationshipInstancesByType(sourceId, relationshipType);
        Set<T> targets = Sets.newHashSet();
        for (U relationship : relationships) {
            if (targetType.isAssignableFrom(relationship.getTarget().getClass())) {
                targets.add((T) relationship.getTarget());
            }
        }
        return targets;
    }

    public Set<tosca.relationships.Root> getRelationshipInstanceBySourceId(String sourceId) {
        Set<tosca.relationships.Root> result = Sets.newHashSet();
        for (tosca.relationships.Root relationshipInstance : relationshipInstances) {
            if (relationshipInstance.getSource().getId().equals(sourceId)) {
                result.add(relationshipInstance);
            }
        }
        return result;
    }

    public Set<tosca.relationships.Root> getRelationshipInstanceByTargetId(String targetId) {
        Set<tosca.relationships.Root> result = Sets.newHashSet();
        for (tosca.relationships.Root relationshipInstance : relationshipInstances) {
            if (relationshipInstance.getTarget().getId().equals(targetId)) {
                result.add(relationshipInstance);
            }
        }
        return result;
    }

    public void install() {
        log.info("Begin to run install workflow");
        Sequence installSequence = new Sequence();
        Set<tosca.nodes.Root> waitForCreatedQueue = Sets.newHashSet(nodeInstances.values());
        Set<tosca.nodes.Root> waitForStartedQueue = Sets.newHashSet(nodeInstances.values());
        int waitForCreatedQueueSize = nodeInstances.size();
        int waitForStartedQueueSize = nodeInstances.size();
        while (waitForCreatedQueueSize > 0 || waitForStartedQueueSize > 0) {
            installSequence.getActionList().add(buildInstallWorkflowStep(waitForCreatedQueue, waitForStartedQueue));
            if (waitForCreatedQueue.size() >= waitForCreatedQueueSize && waitForStartedQueue.size() >= waitForStartedQueueSize) {
                throw new NonRecoverableException("Detected cyclic dependencies in topology");
            } else {
                waitForStartedQueueSize = waitForStartedQueue.size();
                waitForCreatedQueueSize = waitForCreatedQueue.size();
            }
        }
        TaskExecutorFactory.getSequenceExecutor().execute(installSequence);
        log.info("Finished to run install workflow");
    }

    private Sequence buildInstallWorkflowStep(final Set<Root> waitForCreatedQueue,
                                              final Set<tosca.nodes.Root> waitForStartedQueue) {
        Sequence step = new Sequence();
        Parallel createParallel = new Parallel();
        Set<Root> processedCreated = Sets.newHashSet();
        for (final tosca.nodes.Root nodeInstance : waitForCreatedQueue) {
            // Only run create + configure unless if the node has no parent or its parent has been started
            if (nodeInstance.getParent() == null || !waitForStartedQueue.contains(nodeInstance.getParent())) {
                // Check if all dependencies have been satisfied in order to create
                boolean dependenciesSatisfied = true;
                if (nodeInstance.getDependsOnNodes() != null) {
                    for (tosca.nodes.Root dependsOnNode : nodeInstance.getDependsOnNodes()) {
                        if (waitForStartedQueue.contains(dependsOnNode)) {
                            dependenciesSatisfied = false;
                            break;
                        }
                    }
                }
                if (dependenciesSatisfied) {
                    createParallel.getActionList().add(new Task() {
                        @Override
                        public void run() {
                            nodeInstance.create();
                            nodeInstance.setState("created");
                            Set<tosca.relationships.Root> nodeInstanceSourceRelationships = getRelationshipInstanceBySourceId(nodeInstance.getId());
                            for (tosca.relationships.Root relationship : nodeInstanceSourceRelationships) {
                                relationship.preConfigureSource();
                                relationship.setState("preConfiguredSource");
                            }
                            Set<tosca.relationships.Root> nodeInstanceTargetRelationships = getRelationshipInstanceByTargetId(nodeInstance.getId());
                            for (tosca.relationships.Root relationship : nodeInstanceTargetRelationships) {
                                relationship.preConfigureTarget();
                                relationship.setState("preConfiguredTarget");
                            }
                            nodeInstance.configure();
                            nodeInstance.setState("configured");
                            for (tosca.relationships.Root relationship : nodeInstanceSourceRelationships) {
                                relationship.postConfigureSource();
                                relationship.setState("postConfiguredSource");
                            }
                            for (tosca.relationships.Root relationship : nodeInstanceTargetRelationships) {
                                relationship.postConfigureTarget();
                                relationship.setState("postConfiguredTarget");
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
        Set<Root> processedStarted = Sets.newHashSet();
        for (final tosca.nodes.Root nodeInstance : waitForStartedQueue) {
            if (!waitForCreatedQueue.contains(nodeInstance)) {
                startParallel.getActionList().add(new Task() {
                    @Override
                    public void run() {
                        nodeInstance.start();
                        nodeInstance.setState("started");
                        Set<tosca.relationships.Root> nodeInstanceSourceRelationships = getRelationshipInstanceBySourceId(nodeInstance.getId());
                        for (tosca.relationships.Root relationship : nodeInstanceSourceRelationships) {
                            relationship.addTarget();
                            relationship.setState("addedTarget");
                        }
                        Set<tosca.relationships.Root> nodeInstanceTargetRelationships = getRelationshipInstanceByTargetId(nodeInstance.getId());
                        for (tosca.relationships.Root relationship : nodeInstanceTargetRelationships) {
                            relationship.addSource();
                            relationship.setState("addedSource");
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

    public void uninstall() {
        log.info("Begin to run uninstall workflow");
        Sequence uninstallSequence = new Sequence();
        Set<tosca.nodes.Root> waitForStoppedQueue = Sets.newHashSet(nodeInstances.values());
        Set<tosca.nodes.Root> waitForDeletedQueue = Sets.newHashSet(nodeInstances.values());
        int waitForStoppedQueueSize = nodeInstances.size();
        int waitForDeletedQueueSize = nodeInstances.size();
        while (waitForStoppedQueueSize > 0 || waitForDeletedQueueSize > 0) {
            uninstallSequence.getActionList().add(buildUnInstallWorkflowStep(waitForStoppedQueue, waitForDeletedQueue));
            if (waitForStoppedQueue.size() >= waitForStoppedQueueSize && waitForDeletedQueue.size() >= waitForDeletedQueueSize) {
                throw new NonRecoverableException("Detected cyclic dependencies in topology");
            } else {
                waitForDeletedQueueSize = waitForDeletedQueue.size();
                waitForStoppedQueueSize = waitForStoppedQueue.size();
            }
        }
        TaskExecutorFactory.getSequenceExecutor().execute(uninstallSequence);
        log.info("Finished to run uninstall workflow");
    }

    private Sequence buildUnInstallWorkflowStep(final Set<Root> waitForStoppedQueue,
                                                final Set<tosca.nodes.Root> waitForDeletedQueue) {
        Sequence step = new Sequence();
        Parallel stopParallel = new Parallel();
        Set<Root> processedStopped = Sets.newHashSet();
        for (final tosca.nodes.Root nodeInstance : waitForStoppedQueue) {
            // Only run stop unless if the node has no children or all of its children has been deleted
            if (nodeInstance.getChildren() == null || nodeInstance.getChildren().isEmpty()
                    || !Collections.disjoint(waitForStoppedQueue, nodeInstance.getChildren())) {
                // TODO We also must verify that components that depends on the nodes have been stopped in order to have a clean stop
                stopParallel.getActionList().add(new Task() {
                    @Override
                    public void run() {
                        nodeInstance.stop();
                        nodeInstance.setState("stopped");
                        Set<tosca.relationships.Root> nodeInstanceSourceRelationships = getRelationshipInstanceBySourceId(nodeInstance.getId());
                        for (tosca.relationships.Root relationship : nodeInstanceSourceRelationships) {
                            relationship.removeTarget();
                            relationship.setState("removedTarget");
                        }
                        Set<tosca.relationships.Root> nodeInstanceTargetRelationships = getRelationshipInstanceByTargetId(nodeInstance.getId());
                        for (tosca.relationships.Root relationship : nodeInstanceTargetRelationships) {
                            relationship.removeSource();
                            relationship.setState("removedSource");
                        }
                    }
                });
                processedStopped.add(nodeInstance);
            }
        }
        waitForStoppedQueue.removeAll(processedStopped);
        step.getActionList().add(stopParallel);
        Parallel deleteParallel = new Parallel();
        Set<Root> processedDeleted = Sets.newHashSet();
        for (final tosca.nodes.Root nodeInstance : waitForDeletedQueue) {
            // Only run delete if the node instance has been stopped
            if (!waitForStoppedQueue.contains(nodeInstance)) {
                deleteParallel.getActionList().add(new Task() {
                    @Override
                    public void run() {
                        nodeInstance.delete();
                        nodeInstance.setState("deleted");
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
