package com.toscaruntime.sdk;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.toscaruntime.deployment.DeploymentPersister;
import com.toscaruntime.exception.deployment.configuration.IllegalFunctionException;
import com.toscaruntime.exception.deployment.execution.RunningExecutionNotFound;
import com.toscaruntime.exception.deployment.workflow.InvalidInstancesCountException;
import com.toscaruntime.exception.deployment.workflow.NodeNotFoundException;
import com.toscaruntime.sdk.model.DeploymentAddInstancesModification;
import com.toscaruntime.sdk.model.DeploymentConfig;
import com.toscaruntime.sdk.model.DeploymentDeleteInstancesModification;
import com.toscaruntime.sdk.model.DeploymentNode;
import com.toscaruntime.sdk.model.DeploymentRelationshipNode;
import com.toscaruntime.sdk.util.DeploymentUtil;
import com.toscaruntime.sdk.workflow.WorkflowEngine;
import com.toscaruntime.sdk.workflow.WorkflowExecution;
import com.toscaruntime.sdk.workflow.WorkflowExecutionListener;
import com.toscaruntime.util.FunctionUtil;

import tosca.nodes.Root;

/**
 * This represents a topology at runtime which contains instances of nodes defined in the tosca topology and relationship instances between them.
 *
 * @author Minh Khang VU
 */
@SuppressWarnings("unchecked")
public abstract class Deployment {

    private static final Logger log = LoggerFactory.getLogger(Deployment.class);

    /**
     * All configuration of the deployment is stored here
     */
    protected DeploymentConfig config;

    /**
     * id to node instance : A node instance is a physical component of the topology at runtime.
     */
    protected Map<String, tosca.nodes.Root> nodeInstances = new LinkedHashMap<>();

    /**
     * A relationship instance is a link between 2 physical components of the topology
     */
    protected Set<tosca.relationships.Root> relationshipInstances = new HashSet<>();

    /**
     * A node is an abstract layer over an instance, think of it as a java class and node instance as an instantiation of the java class.
     */
    protected Map<String, DeploymentNode> nodes = new HashMap<>();

    /**
     * Same concept as a node but for a relationship
     */
    protected Set<DeploymentRelationshipNode> relationshipNodes = new HashSet<>();

    /**
     * The workflow engine to orchestrate the deployment
     */
    private WorkflowEngine workflowEngine = new WorkflowEngine();

    /**
     * Utility class to help to perform modification on the deployment
     */
    private DeploymentImpacter deploymentImpacter = new DeploymentImpacter();

    /**
     * Utility class to help to initialize nodes and instances of the deployment
     */
    private DeploymentInitializer deploymentInitializer = new DeploymentInitializer();

    /**
     * Hold reference to the provider that was used to initialize the deployment, it's used later to modify the deployment if needed.
     */
    private ProviderHook providerHook;

    /**
     * Deployment persister save deployment's state to database
     */
    private DeploymentPersister deploymentPersister;

    /**
     * Hold current running workflow execution
     */
    private AtomicReference<WorkflowExecution> runningWorkflowExecution = new AtomicReference<>();

    private void attachCreatedInstancesToNode(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances) {
        for (DeploymentNode node : nodes.values()) {
            node.getInstances().addAll(DeploymentUtil.getNodeInstancesByNodeName(nodeInstances, node.getId()));
        }
        for (DeploymentRelationshipNode relationshipNode : relationshipNodes) {
            relationshipNode.getRelationshipInstances().addAll(DeploymentUtil.getRelationshipInstancesByNamesAndType(relationshipInstances, relationshipNode.getSourceNodeId(), relationshipNode.getTargetNodeId(), relationshipNode.getRelationshipType()));
        }
    }

    private void unlinkDeletedInstancesFromNode(Map<String, Root> deletedNodeInstances, Set<tosca.relationships.Root> deletedRelationshipInstances) {
        for (DeploymentNode node : nodes.values()) {
            node.getInstances().removeAll(DeploymentUtil.getNodeInstancesByNodeName(deletedNodeInstances, node.getId()));
        }
        for (DeploymentRelationshipNode relationshipNode : relationshipNodes) {
            relationshipNode.getRelationshipInstances().removeAll(DeploymentUtil.getRelationshipInstancesByNamesAndType(deletedRelationshipInstances, relationshipNode.getSourceNodeId(), relationshipNode.getTargetNodeId(), relationshipNode.getRelationshipType()));
        }
    }

    /**
     * This method is overridden by auto-generated code to help initialize the deployment
     */
    protected abstract void initializeNodes();

    protected abstract void initializeInstances();

    protected abstract void postInitializeConfig();

    protected abstract void initializeRelationships();

    protected abstract void initializeRelationshipInstances();

    /**
     * This method is called to initialize the deployment's config. No instances are created yet until install workflow is launched.
     *
     * @param deploymentName     name of the deployment
     * @param recipePath         the path to the recipe which contains the deployment's binary and artifacts
     * @param inputs             the inputs for the deployment
     * @param providerProperties properties of provider
     * @param bootstrapContext   context of the bootstrapped daemon (id of openstack network, docker network etc ...)
     * @param providerHook       hook in order to let provider to inject specific logic to the deployment
     * @param bootstrap          is in bootstrap mode. In bootstrap mode, the provider may perform operations differently.
     */
    public void initializeConfig(String deploymentName,
                                 Path recipePath,
                                 Map<String, Object> inputs,
                                 Map<String, String> providerProperties,
                                 Map<String, Object> bootstrapContext,
                                 ProviderHook providerHook,
                                 DeploymentPersister deploymentPersister,
                                 boolean bootstrap) {
        this.config = new DeploymentConfig();
        this.config.setDeploymentName(deploymentName);
        this.config.setRecipePath(recipePath);
        this.config.setBootstrap(bootstrap);
        this.config.setBootstrapContext(bootstrapContext);
        this.config.setArtifactsPath(recipePath.resolve("src").resolve("main").resolve("resources"));
        this.config.setProviderProperties(providerProperties);
        this.deploymentPersister = deploymentPersister;
        this.providerHook = providerHook;
        this.workflowEngine.setProviderHook(providerHook);
        postInitializeConfig();
        this.config.getInputs().putAll(inputs);
        initializeNodes();
        initializeRelationships();
        if (deploymentPersister.hasExistingData()) {
            nodes.values().forEach(DeploymentNode::initialLoad);
            createInstances();
            nodeInstances.values().forEach(Root::initialLoad);
            relationshipInstances.forEach(tosca.relationships.Root::initialLoad);
        } else {
            for (DeploymentNode node : nodes.values()) {
                deploymentPersister.syncInsertNodeIfNotExist(node.getId(), node.getInstancesCount());
            }
            for (DeploymentRelationshipNode relationship : relationshipNodes) {
                deploymentPersister.syncInsertRelationshipIfNotExist(relationship.getSourceNodeId(), relationship.getTargetNodeId(), relationship.getRelationshipType().getName());
            }
        }
    }

    /**
     * This method is called by auto-generated code to initialize a node
     *
     * @param nodeName               name of the node
     * @param type                   type of the node
     * @param parentName             name of the parent
     * @param hostName               name of the host
     * @param properties             properties of the node
     * @param capabilitiesProperties capabilities properties of the node
     */
    protected void initializeNode(String nodeName,
                                  Class<? extends Root> type,
                                  String parentName,
                                  String hostName,
                                  Map<String, Object> properties,
                                  Map<String, Map<String, Object>> capabilitiesProperties) {
        this.deploymentInitializer.initializeNode(nodeName, type, parentName, hostName, this, properties, capabilitiesProperties, this.nodes);
    }

    /**
     * This method is called by auto-generated code to initialize an instance
     *
     * @param instance the instance it-self to initialize
     * @param name     name of the instance (node name)
     * @param index    index of the instance within its scaling group
     * @param parent   the parent instance
     * @param host     the host instance
     */
    protected void initializeInstance(tosca.nodes.Root instance,
                                      String name,
                                      int index,
                                      tosca.nodes.Root parent,
                                      tosca.nodes.Root host) {
        this.deploymentInitializer.initializeInstance(instance, this, name, index, parent, host);
        this.nodeInstances.put(instance.getId(), instance);
    }

    protected void setDependencies(String nodeName, String... dependencies) {
        this.deploymentInitializer.setDependencies(this.nodes, nodeName, dependencies);
    }

    protected void generateRelationships(String sourceName, String targetName, Map<String, Object> properties, Class<? extends tosca.relationships.Root> relationshipType) {
        this.deploymentInitializer.generateRelationships(sourceName, targetName, properties, relationshipType, relationshipNodes);
    }

    protected void generateRelationshipInstances(String sourceName, String targetName, Class<? extends tosca.relationships.Root> relationshipType) {
        this.deploymentInitializer.generateRelationshipsInstances(sourceName, targetName, nodes, relationshipNodes, relationshipType, relationshipInstances, this);
    }

    /**
     * Scale the given node to the given instances count
     *
     * @param nodeName          name of the node to scale
     * @param newInstancesCount new instances count
     */
    public WorkflowExecution scale(String nodeName, int newInstancesCount) {
        final long before = System.currentTimeMillis();
        DeploymentNode node = this.nodes.get(nodeName);
        if (node == null) {
            throw new NodeNotFoundException("Node with name [" + nodeName + "] do not exist in the deployment");
        }
        if (newInstancesCount < node.getMinInstancesCount()) {
            throw new InvalidInstancesCountException("New instances count [" + newInstancesCount + "] is less than min instances count [" + node.getMinInstancesCount() + "]");
        }
        if (newInstancesCount > node.getMaxInstancesCount()) {
            throw new InvalidInstancesCountException("New instances count [" + newInstancesCount + "] is greater than max instances count [" + node.getMaxInstancesCount() + "]");
        }
        if (newInstancesCount == node.getInstancesCount()) {
            throw new InvalidInstancesCountException("New instances count [" + newInstancesCount + "] is the same as the current instances count [" + node.getInstancesCount() + "]");
        }
        final int oldInstanceCount = node.getInstancesCount();
        if (newInstancesCount > node.getInstancesCount()) {
            log.info("Scaling up node " + nodeName + "from [" + node.getInstancesCount() + "] to [" + newInstancesCount + "]");
            DeploymentAddInstancesModification modification = deploymentImpacter.addNodeInstances(this, node, newInstancesCount - node.getInstancesCount());

            saveInstancesToPersistence(modification.getInstancesToAdd(), modification.getRelationshipInstancesToAdd());
            node.setInstancesCount(newInstancesCount);
            deploymentPersister.syncSaveNodeInstancesCount(node.getId(), newInstancesCount);
            nodeInstances.putAll(modification.getInstancesToAdd());
            relationshipInstances.addAll(modification.getRelationshipInstancesToAdd());
            attachCreatedInstancesToNode(modification.getInstancesToAdd(), modification.getRelationshipInstancesToAdd());

            WorkflowExecution execution = this.workflowEngine.install(modification.getInstancesToAdd(), modification.getRelationshipInstancesToAdd());
            execution.addListener(new WorkflowExecutionListener() {
                @Override
                public void onFinish() {
                    log.info("Finished to scale up node [{}] from [{}] to [{}] after [{}] seconds", nodeName, oldInstanceCount, newInstancesCount, DeploymentUtil.getExecutionTime(before));
                    runningWorkflowExecution.set(null);
                }

                @Override
                public void onFailure(Collection<Throwable> errors) {
                    errors.stream().forEach(e -> log.info("Scale workflow execution failed", e));
                }

                @Override
                public void onStop() {
                    log.info("Scale up node [{}] from [{}] to [{}] has been stopped", nodeName, oldInstanceCount, newInstancesCount);
                }

                @Override
                public void onCancel() {
                    log.info("Scale up node [{}] from [{}] to [{}] has been cancelled", nodeName, oldInstanceCount, newInstancesCount);
                    runningWorkflowExecution.set(null);
                }
            });
            runningWorkflowExecution.set(execution);
            return execution;
        } else {
            log.info("Scaling down node " + nodeName + " from [" + node.getInstancesCount() + "] to [" + newInstancesCount + "]");
            DeploymentDeleteInstancesModification modification = deploymentImpacter.deleteNodeInstances(this, node, node.getInstancesCount() - newInstancesCount);
            node.setInstancesCount(newInstancesCount);
            deploymentPersister.syncSaveNodeInstancesCount(node.getId(), newInstancesCount);

            WorkflowExecution execution = this.workflowEngine.uninstall(modification.getInstancesToDelete(), modification.getRelationshipInstancesToDelete());
            execution.addListener(new WorkflowExecutionListener() {
                @Override
                public void onFinish() {
                    nodeInstances.keySet().removeAll(modification.getInstancesToDelete().keySet());
                    relationshipInstances.removeAll(modification.getRelationshipInstancesToDelete());
                    unlinkDeletedInstancesFromNode(modification.getInstancesToDelete(), modification.getRelationshipInstancesToDelete());
                    deleteInstancesFromPersistence(modification.getInstancesToDelete(), modification.getRelationshipInstancesToDelete());
                    log.info("Finished to scale down node [{}] from [{}] to [{}] after [{}] seconds", nodeName, oldInstanceCount, newInstancesCount, DeploymentUtil.getExecutionTime(before));
                    runningWorkflowExecution.set(null);
                }

                @Override
                public void onFailure(Collection<Throwable> errors) {
                    errors.stream().forEach(e -> log.info("Scale down workflow execution failed", e));
                }

                @Override
                public void onStop() {
                    log.info("Scale down node [{}] from [{}] to [{}] has been stopped", nodeName, oldInstanceCount, newInstancesCount);
                }

                @Override
                public void onCancel() {
                    log.info("Scale down node [{}] from [{}] to [{}] has been cancelled", nodeName, oldInstanceCount, newInstancesCount);
                    runningWorkflowExecution.set(null);
                }
            });
            runningWorkflowExecution.set(execution);
            return execution;
        }
    }

    public void createInstances() {
        initializeInstances();
        initializeRelationshipInstances();
        attachCreatedInstancesToNode(nodeInstances, relationshipInstances);
        providerHook.postConstruct(this, this.config.getProviderProperties(), this.config.getBootstrapContext());
    }

    /**
     * Cancel the current running execution
     */
    public void cancel(boolean force) {
        WorkflowExecution execution = runningWorkflowExecution.get();
        if (execution == null) {
            throw new RunningExecutionNotFound("No running execution is found in memory to cancel");
        } else {
            execution.cancel(force);
        }
    }

    /**
     * Resume the current running execution
     */
    public void resume() {
        WorkflowExecution execution = runningWorkflowExecution.get();
        if (execution == null) {
            throw new RunningExecutionNotFound("No running execution is found in memory to resume");
        } else {
            execution.resume();
        }
    }

    /**
     * Stop the current running execution
     */
    public void stop(boolean force) {
        WorkflowExecution execution = runningWorkflowExecution.get();
        if (execution == null) {
            throw new RunningExecutionNotFound("No running execution is found in memory to resume");
        } else {
            execution.stop(force);
        }
    }

    /**
     * Install the deployment by executing install life cycle of all nodes
     *
     * @return workflow execution
     */
    public WorkflowExecution install() {
        final long before = System.currentTimeMillis();
        createInstances();
        saveInstancesToPersistence(nodeInstances, relationshipInstances);
        WorkflowExecution execution = workflowEngine.install(nodeInstances, relationshipInstances);
        execution.addListener(new WorkflowExecutionListener() {
            @Override
            public void onFinish() {
                log.info("Finished to install the deployment with [{}] instances and [{}] relationship instances after [{}] seconds", nodeInstances.size(), relationshipInstances.size(), DeploymentUtil.getExecutionTime(before));
                runningWorkflowExecution.set(null);
            }

            @Override
            public void onFailure(Collection<Throwable> errors) {
                errors.stream().forEach(e -> log.info("Error happened while trying to install the deployment", e));
            }

            @Override
            public void onStop() {
                log.info("Stopped installation of the deployment with [{}] instances and [{}] relationship instances", nodeInstances.size(), relationshipInstances.size());
            }

            @Override
            public void onCancel() {
                log.info("Cancelled installation of the deployment with [{}] instances and [{}] relationship instances", nodeInstances.size(), relationshipInstances.size());
                runningWorkflowExecution.set(null);
            }
        });
        runningWorkflowExecution.set(execution);
        return execution;
    }

    public void deleteInstances() {
        this.nodeInstances.clear();
        this.relationshipInstances.clear();
        for (DeploymentNode node : nodes.values()) {
            node.getInstances().clear();
        }
        for (DeploymentRelationshipNode relationshipNode : relationshipNodes) {
            relationshipNode.getRelationshipInstances().clear();
        }
    }

    private void saveInstancesToPersistence(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances) {
        for (Root instance : nodeInstances.values()) {
            deploymentPersister.syncInsertInstanceIfNotExist(instance.getId(), instance.getName(), instance.getState());
            instance.setAttribute("tosca_id", instance.getId());
            instance.setAttribute("tosca_name", instance.getName());
        }
        for (tosca.relationships.Root relationshipInstance : relationshipInstances) {
            deploymentPersister.syncInsertRelationshipInstanceIfNotExist(relationshipInstance.getSource().getId(), relationshipInstance.getTarget().getId(), relationshipInstance.getSource().getName(), relationshipInstance.getTarget().getName(), relationshipInstance.getNode().getRelationshipType().getName(), relationshipInstance.getState());
        }
    }

    private void deleteInstancesFromPersistence(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances) {
        for (Root instance : nodeInstances.values()) {
            deploymentPersister.syncDeleteInstance(instance.getId());
        }
        for (tosca.relationships.Root relationshipInstance : relationshipInstances) {
            deploymentPersister.syncDeleteRelationshipInstance(relationshipInstance.getSource().getId(), relationshipInstance.getTarget().getId(), relationshipInstance.getNode().getRelationshipType().toString());
        }
    }

    private WorkflowExecution doUninstall(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances) {
        final long before = System.currentTimeMillis();
        WorkflowExecution execution = workflowEngine.uninstall(nodeInstances, relationshipInstances);
        execution.addListener(new WorkflowExecutionListener() {
            @Override
            public void onFinish() {
                log.info("Finished to uninstall the deployment with [{}] instances and [{}] relationship instances after [{}] seconds", nodeInstances.size(), relationshipInstances.size(), DeploymentUtil.getExecutionTime(before));
                deleteInstances();
                deleteInstancesFromPersistence(nodeInstances, relationshipInstances);
                runningWorkflowExecution.set(null);
            }

            @Override
            public void onFailure(Collection<Throwable> errors) {
                errors.stream().forEach(e -> log.info("Error happened while trying to uninstall the deployment", e));
            }

            @Override
            public void onStop() {
                log.info("Stopped uninstall of the deployment with [{}] instances and [{}] relationship instances", nodeInstances.size(), relationshipInstances.size());
            }

            @Override
            public void onCancel() {
                log.info("Cancelled uninstall of the deployment with [{}] instances and [{}] relationship instances", nodeInstances.size(), relationshipInstances.size());
                runningWorkflowExecution.set(null);
            }
        });
        runningWorkflowExecution.set(execution);
        return execution;
    }

    /**
     * Uninstall the deployment by executing uninstall life cycle of all nodes native or software
     *
     * @return workflow execution
     */
    public WorkflowExecution uninstall() {
        return doUninstall(nodeInstances, relationshipInstances);
    }

    /**
     * Teardown will not take into account uninstall life cycle of software components, it will delete by force the infrastructure of the deployment
     *
     * @return workflow execution
     */
    public WorkflowExecution teardown() {
        Map<String, Root> nativeNodeInstances = nodeInstances.entrySet().stream().filter(entry -> providerHook.isNativeType(entry.getValue().getClass())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Set<tosca.relationships.Root> nativeRelationshipInstances = relationshipInstances.stream().filter(relationship -> nativeNodeInstances.containsKey(relationship.getSource().getId()) && nativeNodeInstances.containsKey(relationship.getTarget().getId())).collect(Collectors.toSet());
        return doUninstall(nativeNodeInstances, nativeRelationshipInstances);
    }

    public Map<String, Object> getOutputs() {
        return new HashMap<>();
    }

    public Object evaluateFunction(String functionName, String... paths) {
        switch (functionName) {
            case "get_input":
                return config.getInputs().get(paths[0]);
            default:
                Set<Root> instances = getNodeInstancesByNodeName(paths[0]);
                if (instances.isEmpty()) {
                    return null;
                } else if (instances.size() == 1) {
                    return instances.iterator().next().evaluateFunction(functionName, FunctionUtil.setEntityToSelf(paths));
                } else {
                    Map<String, Object> outputResult = new HashMap<>();
                    for (Root instance : instances) {
                        outputResult.put(instance.getId(), instance.evaluateFunction(functionName, FunctionUtil.setEntityToSelf(paths)));
                    }
                    return outputResult;
                }
        }
    }

    public Object evaluateCompositeFunction(String functionName, Object... memberValue) {
        if ("concat".equals(functionName)) {
            return FunctionUtil.concat(memberValue);
        } else {
            throw new IllegalFunctionException("Function " + functionName + " is not supported on deployment");
        }
    }

    public Set<tosca.nodes.Root> getNodeInstancesByNodeName(String nodeName) {
        return this.nodes.get(nodeName).getInstances();
    }

    public <T extends tosca.nodes.Root> Set<T> getNodeInstancesByType(Class<T> type) {
        return DeploymentUtil.getNodeInstancesByType(nodeInstances, type);
    }

    public <T extends tosca.nodes.Root, U extends tosca.relationships.Root> Set<T> getTargetInstancesOfRelationship(String sourceId, Class<U> relationshipType, Class<T> targetType) {
        return DeploymentUtil.getTargetInstancesOfRelationship(relationshipInstances, sourceId, relationshipType, targetType);
    }

    public <T extends tosca.nodes.Root, U extends tosca.relationships.Root> Set<T> getSourceInstancesOfRelationship(String targetId, Class<U> relationshipType, Class<T> sourceType) {
        return DeploymentUtil.getSourceInstancesOfRelationship(relationshipInstances, targetId, relationshipType, sourceType);
    }

    public <T extends tosca.relationships.Root> Set<T> getRelationshipInstancesByNamesAndType(String sourceName, String targetName, Class<T> relationshipType) {
        return DeploymentUtil.getRelationshipInstancesByNamesAndType(relationshipInstances, sourceName, targetName, relationshipType);
    }

    public Set<tosca.relationships.Root> getRelationshipInstanceBySourceId(String sourceId) {
        return DeploymentUtil.getRelationshipInstanceBySourceId(relationshipInstances, sourceId);
    }

    public Set<tosca.relationships.Root> getRelationshipInstanceByTargetId(String targetId) {
        return DeploymentUtil.getRelationshipInstanceByTargetId(relationshipInstances, targetId);
    }

    public Set<DeploymentRelationshipNode> getRelationshipNodeBySourceName(String sourceName) {
        return DeploymentUtil.getRelationshipNodeBySourceName(relationshipNodes, sourceName);
    }

    public Set<DeploymentRelationshipNode> getRelationshipNodeByTargetName(String targetName) {
        return DeploymentUtil.getRelationshipNodeByTargetName(relationshipNodes, targetName);
    }

    public DeploymentConfig getConfig() {
        return config;
    }

    public Set<DeploymentNode> getNodes() {
        return new HashSet<>(nodes.values());
    }

    public Map<String, DeploymentNode> getNodeMap() {
        return nodes;
    }

    public Set<DeploymentRelationshipNode> getRelationshipNodes() {
        return relationshipNodes;
    }

    public ProviderHook getProviderHook() {
        return providerHook;
    }

    public Map<String, Root> getNodeInstances() {
        return nodeInstances;
    }

    public Set<tosca.relationships.Root> getRelationshipInstances() {
        return relationshipInstances;
    }

    public DeploymentPersister getDeploymentPersister() {
        return deploymentPersister;
    }
}
