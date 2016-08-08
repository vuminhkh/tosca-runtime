package com.toscaruntime.sdk;

import com.toscaruntime.deployment.*;
import com.toscaruntime.exception.UnexpectedException;
import com.toscaruntime.exception.deployment.configuration.IllegalFunctionException;
import com.toscaruntime.exception.deployment.execution.RunningExecutionNotFound;
import com.toscaruntime.exception.deployment.workflow.InvalidInstancesCountException;
import com.toscaruntime.exception.deployment.workflow.InvalidWorkflowArgumentException;
import com.toscaruntime.exception.deployment.workflow.NodeNotFoundException;
import com.toscaruntime.sdk.model.*;
import com.toscaruntime.sdk.util.DeploymentUtil;
import com.toscaruntime.sdk.workflow.DefaultListener;
import com.toscaruntime.sdk.workflow.WorkflowEngine;
import com.toscaruntime.sdk.workflow.WorkflowExecution;
import com.toscaruntime.sdk.workflow.tasks.AbstractGenericTask;
import com.toscaruntime.util.CodeGeneratorUtil;
import com.toscaruntime.util.FunctionUtil;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tosca.nodes.Compute;
import tosca.nodes.Root;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

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

    private void setRunningExecution(WorkflowExecution workflowExecution) {
        if (!workflowExecution.isTransient()) {
            if (runningWorkflowExecution.get() != null) {
                log.error("This running execution is being overwritten \n" + runningWorkflowExecution.get());
            }
            runningWorkflowExecution.set(workflowExecution);
        } else {
            log.info("Workflow execution {} is transient so it's not necessary to set new running execution", workflowExecution.getWorkflowId());
        }
    }

    private void setNullRunningExecution(WorkflowExecution workflowExecution) {
        if (!workflowExecution.isTransient()) {
            if (runningWorkflowExecution.get() == null) {
                log.error("No existing running execution to set null");
            } else if (runningWorkflowExecution.get() != workflowExecution) {
                log.error("This unknown running execution is being set to null \n" + runningWorkflowExecution.get() + "\n by \n" + workflowExecution);
            }
            runningWorkflowExecution.set(null);
        } else {
            log.info("Workflow execution {} is transient so it's not necessary to reset running execution", workflowExecution.getWorkflowId());
        }
    }

    /**
     * This method is overridden by auto-generated code to help initialize the deployment
     */
    protected abstract void addNodes();

    protected abstract void addRelationships();

    protected abstract void postInitializeConfig();

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
                                 List<PluginHook> pluginHooks,
                                 DeploymentPersister deploymentPersister,
                                 boolean bootstrap) {
        this.config = new DeploymentConfig();
        this.config.setDeploymentName(deploymentName);
        this.config.setRecipePath(recipePath);
        this.config.setBootstrap(bootstrap);
        this.config.setBootstrapContext(bootstrapContext);
        this.config.setArtifactsPath(recipePath.resolve("src").resolve("main").resolve("resources"));
        this.config.setProviderProperties(providerProperties);
        this.config.setPluginHooks(pluginHooks);
        this.deploymentPersister = deploymentPersister;
        this.providerHook = providerHook;
        this.workflowEngine.setProviderHook(providerHook);
        this.workflowEngine.setDeploymentPersister(deploymentPersister);
        postInitializeConfig();
        this.config.getInputs().putAll(inputs);
        addNodes();
        addRelationships();
        this.providerHook.postConstruct(this, providerProperties, bootstrapContext);
        if (deploymentPersister.hasExistingData()) {
            nodes.values().forEach(DeploymentNode::initialLoad);
            createInstances();
            nodeInstances.values().forEach(Root::initialLoad);
            relationshipInstances.forEach(tosca.relationships.Root::initialLoad);
            RunningExecutionDTO runningExecution = deploymentPersister.syncGetRunningExecution();
            if (runningExecution != null) {
                Map<NodeTaskDTO, String> nodeTaskDTOs = deploymentPersister.syncGetRunningExecutionNodeTasks();
                Map<RelationshipTaskDTO, String> relationshipTaskDTOs = deploymentPersister.syncGetRunningExecutionRelationshipTasks();
                Map<TaskDTO, String> taskDTOs = deploymentPersister.syncGetRunningExecutionTasks();
                WorkflowExecution execution;
                switch (runningExecution.getWorkflowId()) {
                    case "install":
                        execution = createInstallWorkflow(nodeInstances, relationshipInstances);
                        break;
                    case "uninstall":
                        execution = createUninstallWorkflow(nodeInstances, relationshipInstances);
                        break;
                    case "execute_node_operation":
                        String executedNodeId = (String) runningExecution.getInputs().get("node_id");
                        String executedInstanceId = (String) runningExecution.getInputs().get("instance_id");
                        String interfaceName = (String) runningExecution.getInputs().get("interface_name");
                        String operationName = (String) runningExecution.getInputs().get("operation_name");
                        Map<String, Object> executedInputs = (Map<String, Object>) runningExecution.getInputs().get("inputs");
                        execution = executeNodeOperation(executedNodeId, executedInstanceId, interfaceName, operationName, executedInputs, true);
                        break;
                    case "execute_relationship_operation":
                        String executedSourceNodeId = (String) runningExecution.getInputs().get("source_node_id");
                        String executedSourceInstanceId = (String) runningExecution.getInputs().get("source_instance_id");
                        String executedTargetNodeId = (String) runningExecution.getInputs().get("source_target_id");
                        String executedTargetInstanceId = (String) runningExecution.getInputs().get("target_instance_id");
                        String relationshipType = (String) runningExecution.getInputs().get("relationship_type");
                        String relationshipInterfaceName = (String) runningExecution.getInputs().get("interface_name");
                        String relationshipOperationName = (String) runningExecution.getInputs().get("operation_name");
                        Map<String, Object> relationshipExecutedInputs = (Map<String, Object>) runningExecution.getInputs().get("inputs");
                        execution = executeRelationshipOperation(executedSourceNodeId, executedSourceInstanceId, executedTargetNodeId, executedTargetInstanceId, relationshipType, relationshipInterfaceName, relationshipOperationName, relationshipExecutedInputs, false);
                        break;
                    case "scale":
                        Set<String> concernedNodeInstanceIds = nodeTaskDTOs.keySet().stream().map(NodeTaskDTO::getNodeInstanceId).collect(Collectors.toSet());
                        Set<RelationshipTaskDTO.Relationship> concernedRelationshipInstanceIds = relationshipTaskDTOs.keySet().stream().map(RelationshipTaskDTO::getRelationship).collect(Collectors.toSet());
                        Map<String, Root> concernedNodeInstances = nodeInstances.entrySet().stream().filter(entry -> concernedNodeInstanceIds.contains(entry.getKey())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                        Set<tosca.relationships.Root> concernedRelationshipInstances = relationshipInstances.stream().filter(relIns -> concernedRelationshipInstanceIds.contains(new RelationshipTaskDTO.Relationship(relIns.getSource().getId(), relIns.getTarget().getId(), relIns.getNode().getRelationshipName()))).collect(Collectors.toSet());
                        String scaledNodeId = (String) runningExecution.getInputs().get("node_id");
                        Integer newNodeInstancesCount = (Integer) runningExecution.getInputs().get("new_instances_count");
                        Integer originalNodeInstancesCount = (Integer) runningExecution.getInputs().get("original_instances_count");
                        if (newNodeInstancesCount > originalNodeInstancesCount) {
                            execution = createScaleUpWorkflow(concernedNodeInstances, concernedRelationshipInstances, nodes.get(scaledNodeId), newNodeInstancesCount);
                        } else {
                            execution = createScaleDownWorkflow(concernedNodeInstances, concernedRelationshipInstances, nodes.get(scaledNodeId), newNodeInstancesCount);
                        }
                        break;
                    default:
                        throw new UnexpectedException("Not expecting workflow " + runningExecution.getWorkflowId());
                }
                execution.initialLoad(nodeTaskDTOs, relationshipTaskDTOs, taskDTOs);
                execution.addListener(new DefaultListener(deploymentPersister, execution));
                setRunningExecution(execution);
            }
        } else {
            for (DeploymentNode node : nodes.values()) {
                deploymentPersister.syncInsertNodeIfNotExist(node.getId(), node.getInstancesCount());
            }
            for (DeploymentRelationshipNode relationship : relationshipNodes) {
                deploymentPersister.syncInsertRelationshipIfNotExist(relationship.getSourceNodeId(), relationship.getTargetNodeId(), relationship.getRelationshipName());
            }
        }
    }

    /**
     * This method is called by auto-generated code to add a node to the deployment
     *
     * @param nodeName               name of the node
     * @param type                   type of the node
     * @param parentName             name of the parent
     * @param hostName               name of the host
     * @param properties             properties of the node
     * @param capabilitiesProperties capabilities properties of the node
     */
    protected void addNode(String nodeName,
                           Class<? extends Root> type,
                           String parentName,
                           String hostName,
                           Map<String, Object> properties,
                           Map<String, Map<String, Object>> capabilitiesProperties) {
        DeploymentNode deploymentNode = this.deploymentInitializer.createNode(nodeName, type, parentName, hostName, this, properties, capabilitiesProperties);
        nodes.put(nodeName, deploymentNode);
        if (parentName != null) {
            nodes.get(parentName).getChildren().add(nodeName);
        }
    }

    /**
     * This method is called by auto-generated code to add a relationship to the deployment
     *
     * @param sourceName       name of the source
     * @param targetName       name of the target
     * @param properties       relationship properties
     * @param relationshipType type of relationship
     */
    protected void addRelationship(String sourceName,
                                   String targetName,
                                   Map<String, Object> properties,
                                   Class<? extends tosca.relationships.Root> relationshipType) {
        this.relationshipNodes.add(this.deploymentInitializer.createRelationship(sourceName, targetName, properties, relationshipType));
    }

    public WorkflowExecution run(WorkflowExecution workflowExecution) {
        workflowExecution.addListener(new DefaultListener(deploymentPersister, workflowExecution));
        workflowExecution.launch();
        setRunningExecution(workflowExecution);
        return workflowExecution;
    }

    private void persistOriginalInstancesCount(int instancesCount) {
        Map<String, Object> originalNodeCount = new HashMap<>();
        originalNodeCount.put("original_instances_count", instancesCount);
        deploymentPersister.syncInsertExecutionInputs(originalNodeCount);
    }

    /**
     * Attach created instances to the deployment
     *
     * @param addedNodeInstances         node instances to add
     * @param addedRelationshipInstances relationship instances to add
     */
    private void attachCreatedInstancesToDeployment(Map<String, Root> addedNodeInstances, Set<tosca.relationships.Root> addedRelationshipInstances) {
        // Add node instance to deployment
        nodeInstances.putAll(addedNodeInstances);
        // add relationship instance to deployment
        relationshipInstances.addAll(addedRelationshipInstances);
        // Add node instance to its corresponding node
        for (DeploymentNode node : nodes.values()) {
            node.getInstances().addAll(DeploymentUtil.getNodeInstancesByNodeName(addedNodeInstances, node.getId()));
        }
        // Add relationship instance to its corresponding node
        for (DeploymentRelationshipNode relationshipNode : relationshipNodes) {
            relationshipNode.getRelationshipInstances().addAll(DeploymentUtil.getRelationshipInstancesByNamesAndType(addedRelationshipInstances, relationshipNode.getSourceNodeId(), relationshipNode.getTargetNodeId(), relationshipNode.getRelationshipType()));
        }
    }

    private WorkflowExecution createScaleUpWorkflow(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, DeploymentNode node, int newInstancesCount) {
        AbstractGenericTask persistTask = new AbstractGenericTask("persistence") {
            @Override
            protected void doRun() {
                persistOriginalInstancesCount(node.getInstancesCount());
                node.setInstancesCount(newInstancesCount);
                deploymentPersister.syncSaveNodeInstancesCount(node.getId(), newInstancesCount);
                persistCreatedInstances(nodeInstances, relationshipInstances);
                attachCreatedInstancesToDeployment(nodeInstances, relationshipInstances);
                getWorkflowExecution().persist();
            }
        };
        AbstractGenericTask postInstallTask = new AbstractGenericTask("post_install") {
            @Override
            protected void doRun() {
                setNullRunningExecution(workflowExecution);
            }
        };
        return this.workflowEngine.buildInstallWorkflow(Collections.singletonList(persistTask), Collections.singletonList(postInstallTask), nodeInstances, relationshipInstances, "scale");
    }


    /**
     * Delete uninstalled instances from deployment
     *
     * @param deletedNodeInstances         node instances to delete
     * @param deletedRelationshipInstances relationship instances to delete
     */
    private void deleteUninstalledInstancesFromDeployment(Map<String, Root> deletedNodeInstances, Set<tosca.relationships.Root> deletedRelationshipInstances) {
        // Delete uninstalled node instances from deployment
        nodeInstances.keySet().removeAll(deletedNodeInstances.keySet());
        // Delete uninstalled relationship instances from deployment
        relationshipInstances.removeAll(deletedRelationshipInstances);
        // Unlinked deleted instances from its corresponding node
        for (DeploymentNode node : nodes.values()) {
            node.getInstances().removeAll(DeploymentUtil.getNodeInstancesByNodeName(deletedNodeInstances, node.getId()));
        }
        // Unlinked deleted relationship instances from its corresponding node
        for (DeploymentRelationshipNode relationshipNode : relationshipNodes) {
            relationshipNode.getRelationshipInstances().removeAll(DeploymentUtil.getRelationshipInstancesByNamesAndType(deletedRelationshipInstances, relationshipNode.getSourceNodeId(), relationshipNode.getTargetNodeId(), relationshipNode.getRelationshipType()));
        }
    }

    private WorkflowExecution createScaleDownWorkflow(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, DeploymentNode node, int newInstancesCount) {
        AbstractGenericTask persistTask = new AbstractGenericTask("persistence") {
            @Override
            protected void doRun() {
                getWorkflowExecution().persist();
            }
        };
        AbstractGenericTask postUninstallTask = new AbstractGenericTask("post_install") {
            @Override
            protected void doRun() {
                persistOriginalInstancesCount(node.getInstancesCount());
                node.setInstancesCount(newInstancesCount);
                deploymentPersister.syncSaveNodeInstancesCount(node.getId(), newInstancesCount);
                persistDeletedInstances(nodeInstances, relationshipInstances);
                deleteUninstalledInstancesFromDeployment(nodeInstances, relationshipInstances);
                setNullRunningExecution(workflowExecution);
            }
        };
        return this.workflowEngine.buildUninstallWorkflow(Collections.singletonList(persistTask), Collections.singletonList(postUninstallTask), nodeInstances, relationshipInstances, "scale");
    }

    /**
     * Create the scale workflow to scale the given node to the given instances count
     *
     * @param nodeName          name of the node to scale
     * @param newInstancesCount new instances count
     */
    public WorkflowExecution scale(String nodeName, int newInstancesCount) {
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
        if (newInstancesCount > node.getInstancesCount()) {
            log.info("Scaling up node " + nodeName + "from [" + node.getInstancesCount() + "] to [" + newInstancesCount + "]");
            DeploymentAddInstancesModification modification = deploymentImpacter.addNodeInstances(this, node, newInstancesCount - node.getInstancesCount());
            return createScaleUpWorkflow(modification.getInstancesToAdd(), modification.getRelationshipInstancesToAdd(), node, newInstancesCount);
        } else {
            log.info("Scaling down node " + nodeName + " from [" + node.getInstancesCount() + "] to [" + newInstancesCount + "]");
            DeploymentDeleteInstancesModification modification = deploymentImpacter.deleteNodeInstances(this, node, node.getInstancesCount() - newInstancesCount);
            return createScaleDownWorkflow(modification.getInstancesToDelete(), modification.getRelationshipInstancesToDelete(), node, newInstancesCount);
        }
    }

    public WorkflowExecution executeRelationshipOperation(String sourceName,
                                                          String sourceInstanceId,
                                                          String targetName,
                                                          String targetInstanceId,
                                                          String relationshipType,
                                                          String interfaceName,
                                                          String operationName,
                                                          Map<String, Object> inputs,
                                                          boolean transientExecution) {
        Set<tosca.relationships.Root> concernedRelationshipInstances;
        if (StringUtils.isNotBlank(sourceName) && StringUtils.isNotBlank(targetName)) {
            DeploymentRelationshipNode relationshipNode = DeploymentUtil.getRelationshipNodeBySourceNameTargetName(relationshipNodes, sourceName, targetName, relationshipType);
            if (relationshipNode == null) {
                throw new NodeNotFoundException("Relationship with source [" + sourceName + "] and target [" + targetName + "] and type [" + relationshipType + "] do not exist in the deployment");
            }
            concernedRelationshipInstances = relationshipNode.getRelationshipInstances();
        } else if (StringUtils.isNotBlank(sourceInstanceId) && StringUtils.isNotBlank(targetInstanceId)) {
            concernedRelationshipInstances = relationshipInstances.stream().filter(instance -> instance.getSource().getId().equals(sourceInstanceId) && instance.getTarget().getId().equals(targetInstanceId) && instance.getNode().getRelationshipName().equals(relationshipType)).collect(Collectors.toSet());
        } else {
            throw new InvalidWorkflowArgumentException("source/target instance id or source/target node name are needed to launch the workflow");
        }
        String javaMethodName = CodeGeneratorUtil.getGeneratedMethodName(interfaceName, operationName);
        final Map<tosca.relationships.Root, Map<String, OperationInputDefinition>> currentOperationInputs;
        if (inputs != null && !inputs.isEmpty()) {
            currentOperationInputs = concernedRelationshipInstances.stream().collect(Collectors.toMap(instance -> instance, instance -> instance.getOperationInputs().get(javaMethodName)));
        } else {
            currentOperationInputs = null;
        }
        AbstractGenericTask persistTask = new AbstractGenericTask("persistence") {
            @Override
            protected void doRun() {
                if (currentOperationInputs != null) {
                    Map<String, OperationInputDefinition> convertedInputs = inputs.entrySet().stream().collect(Collectors.toMap(
                            Map.Entry::getKey, entry -> (OperationInputDefinition) entry::getValue
                    ));
                    concernedRelationshipInstances.stream().forEach(instance -> {
                                Map<String, OperationInputDefinition> operationInputs = new HashMap<>(currentOperationInputs.get(instance));
                                operationInputs.putAll(convertedInputs);
                                instance.getOperationInputs().put(javaMethodName, operationInputs);
                            }
                    );
                }
                getWorkflowExecution().persist();
            }
        };
        AbstractGenericTask postInstallTask = new AbstractGenericTask("post_execute") {
            @Override
            protected void doRun() {
                if (currentOperationInputs != null) {
                    concernedRelationshipInstances.stream().forEach(relationshipInstance -> relationshipInstance.getOperationInputs().put(javaMethodName, currentOperationInputs.get(relationshipInstance)));
                }
                setNullRunningExecution(workflowExecution);
            }
        };
        return workflowEngine.buildExecuteRelationshipOperationWorkflow(Collections.singletonList(persistTask), Collections.singletonList(postInstallTask), nodeInstances, concernedRelationshipInstances, concernedRelationshipInstances, interfaceName, operationName, "execute_relationship_operation", transientExecution);
    }

    public WorkflowExecution executeNodeOperation(String nodeName, String instanceId, String interfaceName, String operationName, Map<String, Object> inputs, boolean transientExecution) {
        if (StringUtils.isBlank(nodeName) && StringUtils.isBlank(instanceId)) {
            throw new InvalidWorkflowArgumentException("Both node and instance id are empty");
        }
        Set<Root> concernedInstances;
        if (StringUtils.isNotBlank(instanceId)) {
            Root instance = nodeInstances.get(instanceId);
            if (instance == null) {
                throw new NodeNotFoundException("Instance with id [" + instanceId + "] do not exist in the deployment");
            }
            concernedInstances = Collections.singleton(instance);
        } else {
            DeploymentNode node = nodes.get(nodeName);
            if (node == null) {
                throw new NodeNotFoundException("Node with id [" + nodeName + "] do not exist in the deployment");
            }
            concernedInstances = node.getInstances();
            if (concernedInstances.isEmpty()) {
                throw new NodeNotFoundException("Node [" + nodeName + "] do not have any living instance");
            }
        }
        String javaMethodName = CodeGeneratorUtil.getGeneratedMethodName(interfaceName, operationName);
        final Map<String, Map<String, OperationInputDefinition>> currentOperationInputs;
        if (inputs != null && !inputs.isEmpty()) {
            currentOperationInputs = concernedInstances.stream().collect(Collectors.toMap(Root::getId, instance -> instance.getOperationInputs().get(javaMethodName)));
        } else {
            currentOperationInputs = null;
        }
        AbstractGenericTask persistTask = new AbstractGenericTask("persistence") {
            @Override
            protected void doRun() {
                if (currentOperationInputs != null) {
                    Map<String, OperationInputDefinition> convertedInputs = inputs.entrySet().stream().collect(Collectors.toMap(
                            Map.Entry::getKey, entry -> (OperationInputDefinition) entry::getValue
                    ));
                    concernedInstances.stream().forEach(instance -> {
                                Map<String, OperationInputDefinition> operationInputs = new HashMap<>(currentOperationInputs.get(instance.getId()));
                                operationInputs.putAll(convertedInputs);
                                instance.getOperationInputs().put(javaMethodName, operationInputs);
                            }
                    );
                }
                getWorkflowExecution().persist();
            }
        };
        AbstractGenericTask postInstallTask = new AbstractGenericTask("post_execute") {
            @Override
            protected void doRun() {
                if (currentOperationInputs != null) {
                    concernedInstances.stream().forEach(instance -> instance.getOperationInputs().put(javaMethodName, currentOperationInputs.get(instance.getId())));
                }
                setNullRunningExecution(workflowExecution);
            }
        };
        return workflowEngine.buildExecuteNodeOperationWorkflow(Collections.singletonList(persistTask), Collections.singletonList(postInstallTask), nodeInstances, relationshipInstances, concernedInstances, interfaceName, operationName, "execute_node_operation", transientExecution);
    }

    private Map<String, Root> createInstanceTree(DeploymentNode node, Root parent, int index) {
        Root createdInstance = deploymentInitializer.createInstance(this, node, parent, index);
        Map<String, Root> tree = new HashMap<>();
        tree.put(createdInstance.getId(), createdInstance);
        for (String child : node.getChildren()) {
            DeploymentNode childNode = nodes.get(child);
            for (int i = 1; i <= childNode.getInstancesCount(); i++) {
                tree.putAll(createInstanceTree(nodes.get(child), createdInstance, i));
            }
        }
        return tree;
    }

    public void createInstances() {
        nodes.entrySet().stream().filter(nodeEntry -> nodeEntry.getValue().getParent() == null).forEach(nodeEntry -> {
            for (int i = 1; i <= nodeEntry.getValue().getInstancesCount(); i++) {
                nodeInstances.putAll(createInstanceTree(nodeEntry.getValue(), null, i));
            }
        });
        // Add node instance to its corresponding node
        for (DeploymentNode node : nodes.values()) {
            node.getInstances().addAll(DeploymentUtil.getNodeInstancesByNodeName(nodeInstances, node.getId()));
        }
        relationshipInstances.addAll(relationshipNodes.stream().flatMap(relationship -> {
            Set<Root> sources = nodes.get(relationship.getSourceNodeId()).getInstances();
            Set<Root> targets = nodes.get(relationship.getTargetNodeId()).getInstances();
            return deploymentInitializer.generateRelationshipsInstances(sources, targets, relationship, this).stream();
        }).collect(Collectors.toSet()));
        // Add relationship instance to its corresponding node
        for (DeploymentRelationshipNode relationshipNode : relationshipNodes) {
            relationshipNode.getRelationshipInstances().addAll(DeploymentUtil.getRelationshipInstancesByNamesAndType(relationshipInstances, relationshipNode.getSourceNodeId(), relationshipNode.getTargetNodeId(), relationshipNode.getRelationshipType()));
        }
        providerHook.postConstructInstances(nodeInstances, relationshipInstances);
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
            setNullRunningExecution(execution);
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
     * Trigger upload of the recipe on all computes in order to refresh the deployment's recipe on the remote servers
     */
    public void updateRecipe() {
        getNodeInstancesByType(Compute.class).stream().parallel().forEach(Compute::uploadRecipe);
    }

    private WorkflowExecution createInstallWorkflow(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances) {
        AbstractGenericTask persistTask = new AbstractGenericTask("persistence") {
            @Override
            protected void doRun() {
                persistCreatedInstances(nodeInstances, relationshipInstances);
                getWorkflowExecution().persist();
            }
        };
        AbstractGenericTask postInstallTask = new AbstractGenericTask("post_install") {
            @Override
            protected void doRun() {
                setNullRunningExecution(workflowExecution);
            }
        };
        return this.workflowEngine.buildInstallWorkflow(Collections.singletonList(persistTask), Collections.singletonList(postInstallTask), nodeInstances, relationshipInstances, "install");
    }

    /**
     * Create install workflow for all nodes of the deployment.
     *
     * @return workflow execution
     */
    public WorkflowExecution install() {
        createInstances();
        return createInstallWorkflow(nodeInstances, relationshipInstances);
    }

    /**
     * Persist the created instances
     *
     * @param addedNodeInstances         node instances to persist
     * @param addedRelationshipInstances relationship instances to persist
     */
    private void persistCreatedInstances(Map<String, Root> addedNodeInstances, Set<tosca.relationships.Root> addedRelationshipInstances) {
        // Persist node instance
        for (Root instance : addedNodeInstances.values()) {
            deploymentPersister.syncInsertInstanceIfNotExist(instance.getId(), instance.getName(), instance.getState());
            instance.setAttribute("tosca_id", instance.getId());
            instance.setAttribute("tosca_name", instance.getName());
        }
        // Persist relationship instance
        for (tosca.relationships.Root relationshipInstance : addedRelationshipInstances) {
            deploymentPersister.syncInsertRelationshipInstanceIfNotExist(relationshipInstance.getSource().getId(), relationshipInstance.getTarget().getId(), relationshipInstance.getSource().getName(), relationshipInstance.getTarget().getName(), relationshipInstance.getNode().getRelationshipName(), relationshipInstance.getState());
        }
    }

    /**
     * Clean all deployment's instances
     */
    private void deleteInstances() {
        nodeInstances.clear();
        relationshipInstances.clear();
        nodes.values().stream().forEach(node -> node.getInstances().clear());
        relationshipNodes.stream().forEach(relationship -> relationship.getRelationshipInstances().clear());
    }

    private void persistDeletedInstances(Map<String, Root> deletedNodeInstances, Set<tosca.relationships.Root> deletedRelationshipInstances) {
        // Delete uninstalled node instances from persistence
        for (Root instance : deletedNodeInstances.values()) {
            deploymentPersister.syncDeleteInstance(instance.getId());
        }
        // Delete uninstalled relationship instances from persistence
        for (tosca.relationships.Root relationshipInstance : deletedRelationshipInstances) {
            deploymentPersister.syncDeleteRelationshipInstance(relationshipInstance.getSource().getId(), relationshipInstance.getTarget().getId(), relationshipInstance.getNode().getRelationshipName());
        }
    }

    private WorkflowExecution createUninstallWorkflow(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances) {
        AbstractGenericTask persistTask = new AbstractGenericTask("persistence") {
            @Override
            protected void doRun() {
                getWorkflowExecution().persist();
            }
        };
        AbstractGenericTask postUninstallTask = new AbstractGenericTask("post_install") {
            @Override
            protected void doRun() {
                persistDeletedInstances(nodeInstances, relationshipInstances);
                deleteInstances();
                setNullRunningExecution(workflowExecution);
            }
        };
        return this.workflowEngine.buildUninstallWorkflow(Collections.singletonList(persistTask), Collections.singletonList(postUninstallTask), nodeInstances, relationshipInstances, "uninstall");
    }

    /**
     * Create uninstall workflow for all nodes of the deployment
     *
     * @return workflow execution
     */
    public WorkflowExecution uninstall() {
        return createUninstallWorkflow(nodeInstances, relationshipInstances);
    }

    /**
     * Teardown will not take into account uninstall life cycle of software components, it will delete by force the infrastructure of the deployment
     *
     * @return workflow execution
     */
    public WorkflowExecution teardown() {
        Map<String, Root> nativeNodeInstances = nodeInstances.entrySet().stream().filter(entry -> providerHook.isNativeType(entry.getValue().getClass())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Set<tosca.relationships.Root> nativeRelationshipInstances = relationshipInstances.stream().filter(relationship -> nativeNodeInstances.containsKey(relationship.getSource().getId()) && nativeNodeInstances.containsKey(relationship.getTarget().getId())).collect(Collectors.toSet());
        return createUninstallWorkflow(nativeNodeInstances, nativeRelationshipInstances);
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

    public Set<Root> getChildren(Root ofInstance) {
        return DeploymentUtil.getChildren(nodeInstances, ofInstance);
    }

    public Set<tosca.relationships.Root> getRelationshipInstances() {
        return relationshipInstances;
    }

    public DeploymentPersister getDeploymentPersister() {
        return deploymentPersister;
    }
}
