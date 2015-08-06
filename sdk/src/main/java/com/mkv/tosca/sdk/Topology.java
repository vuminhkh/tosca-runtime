package com.mkv.tosca.sdk;

import java.util.Set;

import tosca.nodes.Root;

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
     * A node instance is a physical component of the topology at runtime.
     */
    protected Set<tosca.nodes.Root> nodeInstances = Sets.newHashSet();

    /**
     * A relationship instance is a link between 2 physical components of the topology
     */
    protected Set<tosca.relationships.Root> relationshipInstances = Sets.newHashSet();

    public Set<tosca.nodes.Root> getNodeInstancesByNodeName(String nodeName) {
        Set<tosca.nodes.Root> result = Sets.newHashSet();
        for (tosca.nodes.Root nodeInstance : nodeInstances) {
            if (nodeInstance.getName().equals(nodeName)) {
                result.add(nodeInstance);
            }
        }
        return result;
    }

    public tosca.nodes.Root getNodeInstanceById(String nodeInstanceId) {
        for (tosca.nodes.Root nodeInstance : nodeInstances) {
            if (nodeInstance.getId().equals(nodeInstanceId)) {
                return nodeInstance;
            }
        }
        return null;
    }

    public Set<tosca.relationships.Root> getRelationshipInstancesBySourceName(String sourceName) {
        Set<tosca.relationships.Root> result = Sets.newHashSet();
        for (tosca.relationships.Root relationshipInstance : relationshipInstances) {
            if (relationshipInstance.getSource().getName().equals(sourceName)) {
                result.add(relationshipInstance);
            }
        }
        return result;
    }

    public Set<tosca.relationships.Root> getRelationshipInstancesByTargetName(String targetName) {
        Set<tosca.relationships.Root> result = Sets.newHashSet();
        for (tosca.relationships.Root relationshipInstance : relationshipInstances) {
            if (relationshipInstance.getTarget().getName().equals(targetName)) {
                result.add(relationshipInstance);
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
        Set<tosca.nodes.Root> waitForCreatedQueue = Sets.newHashSet(nodeInstances);
        Set<tosca.nodes.Root> waitForStartedQueue = Sets.newHashSet(nodeInstances);
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
                waitForCreatedQueue.remove(nodeInstance);
            }
        }
        step.getActionList().add(createParallel);
        Parallel startParallel = new Parallel();
        for (final tosca.nodes.Root nodeInstance : waitForStartedQueue) {
            if (!waitForCreatedQueue.contains(nodeInstance)) {
                boolean dependenciesSatisfied = true;
                for (tosca.nodes.Root dependsOnNode : nodeInstance.getDependsOnNodes()) {
                    if (waitForStartedQueue.contains(dependsOnNode)) {
                        dependenciesSatisfied = false;
                        break;
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
                    waitForStartedQueue.remove(nodeInstance);
                }
            }
        }
        return step;
    }
}
