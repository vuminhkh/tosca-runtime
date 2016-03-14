package com.toscaruntime.sdk;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.toscaruntime.sdk.util.DeploymentUtil;
import com.toscaruntime.sdk.util.WorkflowUtil;
import com.toscaruntime.sdk.workflow.tasks.InstallLifeCycleTasks;
import com.toscaruntime.sdk.workflow.tasks.UninstallLifeCycleTasks;

import tosca.nodes.BlockStorage;
import tosca.nodes.Compute;
import tosca.nodes.Network;
import tosca.nodes.Root;
import tosca.relationships.AttachTo;

/**
 * Base class for provider hook which perform default workflow processing for native nodes
 *
 * @author Minh Khang VU
 */
public abstract class AbstractProviderHook implements ProviderHook {

    @Override
    public ProviderWorkflowProcessingResult postConstructUninstallWorkflow(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Map<Root, UninstallLifeCycleTasks> nodesLifeCycles) {
        ProviderWorkflowProcessingResult workflowProcessingResult = getProviderWorkflowProcessingResult(nodeInstances, relationshipInstances);
        Set<tosca.relationships.Root> otherRelationshipInstances = relationshipInstances.stream().filter(relationship -> !workflowProcessingResult.getRelationshipInstances().contains(relationship)).collect(Collectors.toSet());
        for (Root processedInstance : workflowProcessingResult.getNodeInstances().values()) {
            WorkflowUtil.declareNodeUninstallDependenciesWithRelationship(processedInstance, nodesLifeCycles.get(processedInstance), nodesLifeCycles, otherRelationshipInstances);
        }
        // Custom processing for life cycle of computes
        for (Compute compute : workflowProcessingResult.getComputes()) {
            UninstallLifeCycleTasks computeLifeCycle = nodesLifeCycles.get(compute);
            // Use default task dependencies for a node for compute
            // It means that a provider can override this behaviour if it wishes so
            WorkflowUtil.declareNodeUninstallDependencies(computeLifeCycle);
            // Network and volume must be stopped and deleted after the compute has been deleted
            Set<Network> networksOfCompute = DeploymentUtil.getTargetInstancesOfRelationship(relationshipInstances, compute.getId(), tosca.relationships.Network.class, Network.class);
            for (Network networkOfCompute : networksOfCompute) {
                UninstallLifeCycleTasks networkOfComputeLifeCycle = nodesLifeCycles.get(networkOfCompute);
                networkOfComputeLifeCycle.getStopTask().dependsOn(computeLifeCycle.getDeleteTask());
                computeLifeCycle.getStopTask().dependsOn(networkOfComputeLifeCycle.getRemoveSourceTask());
                computeLifeCycle.getStopTask().dependsOn(networkOfComputeLifeCycle.getRemoveTargetTask());
            }
            Set<BlockStorage> blockStoragesOfCompute = DeploymentUtil.getSourceInstancesOfRelationship(relationshipInstances, compute.getId(), tosca.relationships.AttachTo.class, BlockStorage.class);
            for (BlockStorage blockStorageOfCompute : blockStoragesOfCompute) {
                UninstallLifeCycleTasks blockStorageOfComputeLifeCycle = nodesLifeCycles.get(blockStorageOfCompute);
                blockStorageOfComputeLifeCycle.getStopTask().dependsOn(computeLifeCycle.getDeleteTask());
                computeLifeCycle.getStopTask().dependsOn(blockStorageOfComputeLifeCycle.getRemoveSourceTask());
                computeLifeCycle.getStopTask().dependsOn(blockStorageOfComputeLifeCycle.getRemoveTargetTask());
            }
        }
        return workflowProcessingResult;
    }

    @Override
    public ProviderWorkflowProcessingResult postConstructInstallWorkflow(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Map<Root, InstallLifeCycleTasks> nodesLifeCycles) {
        ProviderWorkflowProcessingResult workflowProcessingResult = getProviderWorkflowProcessingResult(nodeInstances, relationshipInstances);
        Set<tosca.relationships.Root> otherRelationshipInstances = relationshipInstances.stream().filter(relationship -> !workflowProcessingResult.getRelationshipInstances().contains(relationship)).collect(Collectors.toSet());
        for (Root processedInstance : workflowProcessingResult.getNodeInstances().values()) {
            WorkflowUtil.declareNodeInstallDependenciesWithRelationship(processedInstance, nodesLifeCycles.get(processedInstance), nodesLifeCycles, otherRelationshipInstances);
        }
        // Custom processing for life cycle of computes
        for (Compute compute : workflowProcessingResult.getComputes()) {
            InstallLifeCycleTasks computeLifeCycle = nodesLifeCycles.get(compute);
            // Network and volume must be created before the compute is created
            Set<Network> networksOfCompute = DeploymentUtil.getTargetInstancesOfRelationship(relationshipInstances, compute.getId(), tosca.relationships.Network.class, Network.class);
            for (Network networkOfCompute : networksOfCompute) {
                InstallLifeCycleTasks networkOfComputeLifeCycle = nodesLifeCycles.get(networkOfCompute);
                computeLifeCycle.getCreateTask().dependsOn(networkOfComputeLifeCycle.getStartTask());
                networkOfComputeLifeCycle.getAddSourceTask().dependsOn(computeLifeCycle.getStartTask());
                networkOfComputeLifeCycle.getAddTargetTask().dependsOn(computeLifeCycle.getStartTask());
            }
            Set<BlockStorage> blockStoragesOfCompute = DeploymentUtil.getSourceInstancesOfRelationship(relationshipInstances, compute.getId(), tosca.relationships.AttachTo.class, BlockStorage.class);
            for (BlockStorage blockStorageOfCompute : blockStoragesOfCompute) {
                InstallLifeCycleTasks blockStorageOfComputeLifeCycle = nodesLifeCycles.get(blockStorageOfCompute);
                computeLifeCycle.getCreateTask().dependsOn(blockStorageOfComputeLifeCycle.getStartTask());
                blockStorageOfComputeLifeCycle.getAddTargetTask().dependsOn(computeLifeCycle.getStartTask());
                blockStorageOfComputeLifeCycle.getAddSourceTask().dependsOn(computeLifeCycle.getStartTask());
            }
        }
        // Tosca Runtime do not implement native relationships AttachTo and Network for simplicity
        // All attach of resources should be handled by native components lifecycle
        return workflowProcessingResult;
    }

    private ProviderWorkflowProcessingResult getProviderWorkflowProcessingResult(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances) {
        Map<String, Root> processedNodes = new HashMap<>();
        Set<Compute> computes = DeploymentUtil.getNodeInstancesByType(nodeInstances, Compute.class);
        processedNodes.putAll(DeploymentUtil.toMap(computes));
        Set<Network> networks = DeploymentUtil.getNodeInstancesByType(nodeInstances, Network.class);
        processedNodes.putAll(DeploymentUtil.toMap(networks));
        Set<BlockStorage> blockStorages = DeploymentUtil.getNodeInstancesByType(nodeInstances, BlockStorage.class);
        processedNodes.putAll(DeploymentUtil.toMap(blockStorages));
        Set<tosca.relationships.Root> processedRelationships = new HashSet<>();
        processedRelationships.addAll(DeploymentUtil.getRelationshipInstancesByTypes(relationshipInstances, BlockStorage.class, Compute.class, AttachTo.class));
        processedRelationships.addAll(DeploymentUtil.getRelationshipInstancesByTypes(relationshipInstances, Compute.class, Network.class, tosca.relationships.Network.class));
        return new ProviderWorkflowProcessingResult(processedNodes, processedRelationships, computes, blockStorages, networks);
    }

    @Override
    public boolean isNativeType(Class<?> type) {
        return Compute.class.isAssignableFrom(type) || BlockStorage.class.isAssignableFrom(type) || Network.class.isAssignableFrom(type);
    }
}
