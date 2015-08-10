package com.mkv.tosca.sdk;

import java.util.Map;
import java.util.Set;

import tosca.nodes.Root;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mkv.exception.NonRecoverableException;
import com.mkv.tosca.sdk.workflow.Parallel;
import com.mkv.tosca.sdk.workflow.Sequence;
import com.mkv.tosca.sdk.workflow.Task;
import com.mkv.tosca.sdk.workflow.TaskExecutorFactory;

/**
 * This represents a runtime topology
 * 
 * @author Minh Khang VU
 */
public class Topology {

    /**
     * Inputs for a topology
     */
    protected Map<String, String> inputs;

    /**
     * id => node instance : A node instance is a physical component of the topology at runtime.
     */
    protected Map<String, tosca.nodes.Root> nodeInstances = Maps.newHashMap();

    /**
     * A relationship instance is a link between 2 physical components of the topology
     */
    protected Set<tosca.relationships.Root> relationshipInstances = Sets.newHashSet();

    public Topology() {
        this.inputs = Maps.newHashMap();
    }

    public Topology(Map<String, String> inputs) {
        this.inputs = inputs;
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
        for (Root sourceInstance : getNodeInstancesByNodeName(sourceName)) {
            for (Root targetInstance : getNodeInstancesByNodeName(targetName)) {
                try {
                    tosca.relationships.Root relationshipInstance = relationshipType.newInstance();
                    relationshipInstance.setSource(sourceInstance);
                    relationshipInstance.setTarget(targetInstance);
                    relationshipInstances.add(relationshipInstance);
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
                waitForCreatedQueueSize = waitForStartedQueue.size();
                waitForStartedQueueSize = waitForCreatedQueue.size();
            }
        }
        TaskExecutorFactory.getSequenceExecutor().execute(installSequence);
    }

    private Sequence buildInstallWorkflowStep(final Set<Root> waitForCreatedQueue,
            final Set<tosca.nodes.Root> waitForStartedQueue) {
        Sequence step = new Sequence();
        Parallel createParallel = new Parallel();
        Set<Root> processedCreated = Sets.newHashSet();
        for (final tosca.nodes.Root nodeInstance : waitForCreatedQueue) {
            // Only run create + configure unless if the node has no parent or its parent has been started
            if (nodeInstance.getParent() == null || !waitForStartedQueue.contains(nodeInstance.getParent())) {
                createParallel.getActionList().add(new Task() {
                    @Override
                    public void run() {
                        nodeInstance.create();
                        Set<tosca.relationships.Root> nodeInstanceSourceRelationships = getRelationshipInstanceBySourceId(nodeInstance.getId());
                        for (tosca.relationships.Root relationship : nodeInstanceSourceRelationships) {
                            relationship.preConfigureSource();
                        }
                        Set<tosca.relationships.Root> nodeInstanceTargetRelationships = getRelationshipInstanceByTargetId(nodeInstance.getId());
                        for (tosca.relationships.Root relationship : nodeInstanceTargetRelationships) {
                            relationship.preConfigureTarget();
                        }
                        nodeInstance.configure();
                        for (tosca.relationships.Root relationship : nodeInstanceSourceRelationships) {
                            relationship.postConfigureSource();
                        }
                        for (tosca.relationships.Root relationship : nodeInstanceTargetRelationships) {
                            relationship.postConfigureTarget();
                        }
                    }
                });
                processedCreated.add(nodeInstance);
            }
        }
        waitForCreatedQueue.removeAll(processedCreated);
        step.getActionList().add(createParallel);
        Parallel startParallel = new Parallel();
        Set<Root> processedStarted = Sets.newHashSet();
        for (final tosca.nodes.Root nodeInstance : waitForStartedQueue) {
            if (!waitForCreatedQueue.contains(nodeInstance)) {
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
                    startParallel.getActionList().add(new Task() {
                        @Override
                        public void run() {
                            nodeInstance.start();
                            Set<tosca.relationships.Root> nodeInstanceSourceRelationships = getRelationshipInstanceBySourceId(nodeInstance.getId());
                            for (tosca.relationships.Root relationship : nodeInstanceSourceRelationships) {
                                relationship.addTarget();
                            }
                            Set<tosca.relationships.Root> nodeInstanceTargetRelationships = getRelationshipInstanceByTargetId(nodeInstance.getId());
                            for (tosca.relationships.Root relationship : nodeInstanceTargetRelationships) {
                                relationship.addSource();
                            }
                        }
                    });
                    processedStarted.add(nodeInstance);
                }
            }
        }
        waitForStartedQueue.removeAll(processedStarted);
        return step;
    }
}
