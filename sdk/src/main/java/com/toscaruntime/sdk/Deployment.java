package com.toscaruntime.sdk;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.toscaruntime.exception.IllegalFunctionException;
import com.toscaruntime.exception.InvalidInstancesCountException;
import com.toscaruntime.exception.NodeNotFoundException;
import com.toscaruntime.exception.WorkflowExecutionException;
import com.toscaruntime.sdk.model.DeploymentAddInstancesModification;
import com.toscaruntime.sdk.model.DeploymentConfig;
import com.toscaruntime.sdk.model.DeploymentDeleteInstancesModification;
import com.toscaruntime.sdk.model.DeploymentNode;
import com.toscaruntime.sdk.model.DeploymentRelationshipNode;
import com.toscaruntime.sdk.util.DeploymentUtil;
import com.toscaruntime.sdk.workflow.WorkflowEngine;
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
     * Hold reference to the post constructor that was used to initialize the deployment, it's used later to modify the deployment if needed.
     */
    private List<DeploymentPostConstructor> deploymentPostConstructors;

    public void initialize() {
        initializeInstances();
        initializeRelationshipInstances();
        attachCreatedInstancesToNode(nodeInstances, relationshipInstances);
        for (DeploymentPostConstructor postConstructor : deploymentPostConstructors) {
            postConstructor.postConstruct(this, this.config.getProviderProperties(), this.config.getBootstrapContext());
        }
    }

    private void attachCreatedInstancesToNode(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances) {
        for (DeploymentNode node : nodes.values()) {
            node.getInstances().addAll(DeploymentUtil.getNodeInstancesByNodeName(nodeInstances, node.getId()));
        }
        for (DeploymentRelationshipNode relationshipNode : relationshipNodes) {
            relationshipNode.getRelationshipInstances().addAll(DeploymentUtil.getRelationshipInstancesByNamesAndType(relationshipInstances, relationshipNode.getSourceNodeId(), relationshipNode.getTargetNodeId(), relationshipNode.getRelationshipType()));
        }
    }

    public void destroy() {
        this.nodeInstances.clear();
        this.relationshipInstances.clear();
        for (DeploymentNode node : nodes.values()) {
            node.getInstances().clear();
        }
        for (DeploymentRelationshipNode relationshipNode : relationshipNodes) {
            relationshipNode.getRelationshipInstances().clear();
        }
    }

    private void unlinkDeletedInstancesFromNode(Map<String, Root> deletedNodeInstances, Set<tosca.relationships.Root> deletedRelationshipInstances) {
        for (DeploymentNode node : nodes.values()) {
            node.getInstances().removeAll(DeploymentUtil.getNodeInstancesByNodeName(deletedNodeInstances, node.getId()));
        }
        for (DeploymentRelationshipNode relationshipNode : relationshipNodes) {
            relationshipNode.getRelationshipInstances().removeAll(DeploymentUtil.getRelationshipInstancesByNamesAndType(relationshipInstances, relationshipNode.getSourceNodeId(), relationshipNode.getTargetNodeId(), relationshipNode.getRelationshipType()));
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
     * @param postConstructors   post constructors
     * @param bootstrap          is in bootstrap mode. In bootstrap mode, the provider may perform operations differently.
     */
    public void initializeConfig(String deploymentName,
                                 Path recipePath,
                                 Map<String, Object> inputs,
                                 Map<String, String> providerProperties,
                                 Map<String, Object> bootstrapContext,
                                 List<DeploymentPostConstructor> postConstructors,
                                 boolean bootstrap) {
        this.config = new DeploymentConfig();
        this.config.setDeploymentName(deploymentName);
        this.config.setInputs(inputs);
        this.config.setRecipePath(recipePath);
        this.config.setBootstrap(bootstrap);
        this.config.setBootstrapContext(bootstrapContext);
        this.config.setArtifactsPath(recipePath.resolve("src").resolve("main").resolve("resources"));
        this.config.setProviderProperties(providerProperties);
        this.deploymentPostConstructors = postConstructors;
        postInitializeConfig();
        initializeNodes();
        initializeRelationships();
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
        this.deploymentInitializer.initializeNode(nodeName, type, parentName, hostName, properties, capabilitiesProperties, this.nodes);
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
        this.deploymentInitializer.initializeInstance(instance, this, name, index, parent, host, this.nodeInstances);
    }

    protected void setDependencies(String nodeName, String... dependencies) {
        this.deploymentInitializer.setDependencies(this.nodes, nodeName, dependencies);
    }

    protected void generateRelationships(String sourceName, String targetName, Map<String, Object> properties, Class<? extends tosca.relationships.Root> relationshipType) {
        this.deploymentInitializer.generateRelationships(sourceName, targetName, properties, relationshipType, relationshipNodes);
    }

    protected void generateRelationshipInstances(String sourceName, String targetName, Class<? extends tosca.relationships.Root> relationshipType) {
        this.deploymentInitializer.generateRelationshipsInstances(sourceName, targetName, nodes, relationshipNodes, relationshipType, relationshipInstances);
    }

    /**
     * Scale the given node to the given instances
     *
     * @param nodeName          name of the node to scale
     * @param newInstancesCount new instances count
     */
    public void scale(String nodeName, int newInstancesCount) {
        DeploymentNode node = this.nodes.get(nodeName);
        if (node == null) {
            throw new NodeNotFoundException("Node with name [" + nodeName + "] do not exist in the deployment");
        }
        if (newInstancesCount < node.getMinInstancesCount()) {
            throw new InvalidInstancesCountException("New instances count [" + newInstancesCount + "] is less than min instances count");
        }
        if (newInstancesCount > node.getMaxInstancesCount()) {
            throw new InvalidInstancesCountException("New instances count [" + newInstancesCount + "] is greater than max instances count");
        }
        if (newInstancesCount == node.getInstancesCount()) {
            log.warn("New instances count is equal to current number of instance, do nothing");
        } else if (newInstancesCount > node.getInstancesCount()) {
            log.info("Scaling up node " + nodeName + "from [" + node.getInstancesCount() + "] to [" + newInstancesCount + "]");
            DeploymentAddInstancesModification modification = deploymentImpacter.addNodeInstances(this, node, newInstancesCount - node.getInstancesCount());
            try {
                this.workflowEngine.install(modification.getInstancesToAdd(), modification.getRelationshipInstancesToAdd());
                node.setInstancesCount(newInstancesCount);
                this.nodeInstances.putAll(modification.getInstancesToAdd());
                this.relationshipInstances.addAll(modification.getRelationshipInstancesToAdd());
                attachCreatedInstancesToNode(modification.getInstancesToAdd(), modification.getRelationshipInstancesToAdd());
            } catch (WorkflowExecutionException e) {
                log.info("Scale workflow execution failed, rolling back", e);
                this.workflowEngine.uninstall(modification.getInstancesToAdd(), modification.getRelationshipInstancesToAdd());
                throw e;
            }
        } else {
            log.info("Scaling down node " + nodeName + " from [" + node.getInstancesCount() + "] to [" + newInstancesCount + "]");
            DeploymentDeleteInstancesModification modification = deploymentImpacter.deleteNodeInstances(this, node, node.getInstancesCount() - newInstancesCount);
            this.workflowEngine.uninstall(modification.getInstancesToDelete(), modification.getRelationshipInstancesToDelete());
            node.setInstancesCount(newInstancesCount);
            this.nodeInstances.keySet().removeAll(modification.getInstancesToDelete().keySet());
            this.relationshipInstances.removeAll(modification.getRelationshipInstancesToDelete());
            unlinkDeletedInstancesFromNode(modification.getInstancesToDelete(), modification.getRelationshipInstancesToDelete());
        }
    }

    public void install() {
        initialize();
        workflowEngine.install(nodeInstances, relationshipInstances);
    }

    public void uninstall() {
        workflowEngine.uninstall(nodeInstances, relationshipInstances);
        destroy();
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

    public <T extends tosca.relationships.Root> Set<T> getRelationshipInstancesByType(String sourceId, Class<T> type) {
        return DeploymentUtil.getRelationshipInstancesByType(relationshipInstances, sourceId, type);
    }

    public <T extends tosca.nodes.Root, U extends tosca.relationships.Root> Set<T> getNodeInstancesByRelationship(String sourceId, Class<U> relationshipType, Class<T> targetType) {
        return DeploymentUtil.getNodeInstancesByRelationship(relationshipInstances, sourceId, relationshipType, targetType);
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

    public List<DeploymentPostConstructor> getDeploymentPostConstructors() {
        return deploymentPostConstructors;
    }

    public void setDeploymentPostConstructors(List<DeploymentPostConstructor> deploymentPostConstructors) {
        this.deploymentPostConstructors = deploymentPostConstructors;
    }

    public Map<String, Root> getNodeInstances() {
        return nodeInstances;
    }

    public Set<tosca.relationships.Root> getRelationshipInstances() {
        return relationshipInstances;
    }
}
