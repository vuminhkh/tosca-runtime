package com.toscaruntime.sdk;

import com.toscaruntime.sdk.util.DeploymentUtil;
import com.toscaruntime.sdk.util.WorkflowUtil;
import com.toscaruntime.sdk.workflow.WorkflowExecution;
import com.toscaruntime.sdk.workflow.tasks.InstallLifeCycleTasks;
import com.toscaruntime.sdk.workflow.tasks.RelationshipInstallLifeCycleTasks;
import com.toscaruntime.sdk.workflow.tasks.RelationshipUninstallLifeCycleTasks;
import com.toscaruntime.sdk.workflow.tasks.UninstallLifeCycleTasks;
import tosca.nodes.BlockStorage;
import tosca.nodes.Compute;
import tosca.nodes.Network;
import tosca.nodes.Root;
import tosca.relationships.AttachTo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Base class for provider hook which perform default workflow processing for native nodes
 *
 * @author Minh Khang VU
 */
public abstract class AbstractProviderHook implements ProviderHook {

    @Override
    public ProviderWorkflowProcessingResult postConstructInstallWorkflow(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Map<Root, InstallLifeCycleTasks> nodeInstancesLifeCycles, Map<tosca.relationships.Root, RelationshipInstallLifeCycleTasks> relationshipInstancesLifeCycles, WorkflowExecution workflowExecution) {
        ProviderWorkflowProcessingResult workflowProcessingResult = getProviderWorkflowProcessingResult(nodeInstances, relationshipInstances);
        for (Root nodeInstance : workflowProcessingResult.getNodeInstances().values()) {
            WorkflowUtil.declareNodeInstallDependencies(nodeInstancesLifeCycles.get(nodeInstance), nodeInstancesLifeCycles);
        }
        // This means all relationships are treated as hostedOn
        // For AttachTo the relationship direction is inverted, it means the volume is hosted on the compute
        for (tosca.relationships.Root relationshipInstance : workflowProcessingResult.getRelationshipInstances()) {
            InstallLifeCycleTasks sourceTasks = nodeInstancesLifeCycles.get(relationshipInstance.getSource());
            InstallLifeCycleTasks targetTasks = nodeInstancesLifeCycles.get(relationshipInstance.getTarget());
            RelationshipInstallLifeCycleTasks relationshipInstallLifeCycleTasks = relationshipInstancesLifeCycles.get(relationshipInstance);
            if (relationshipInstance instanceof tosca.relationships.AttachTo) {
                // FIXME Invert relationship for attach to !!
                // FIXME This will be removed in next tosca release !!
                InstallLifeCycleTasks temp = sourceTasks;
                sourceTasks = targetTasks;
                targetTasks = temp;
            }
            WorkflowUtil.declareRelationshipInstallDependencies(relationshipInstallLifeCycleTasks, sourceTasks, targetTasks);
            WorkflowUtil.declareHostedOnInstallDependencies(relationshipInstallLifeCycleTasks, sourceTasks, targetTasks);
        }
        return workflowProcessingResult;
    }

    @Override
    public ProviderWorkflowProcessingResult postConstructUninstallWorkflow(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Map<Root, UninstallLifeCycleTasks> nodeInstancesLifeCycles, Map<tosca.relationships.Root, RelationshipUninstallLifeCycleTasks> relationshipInstancesLifeCycles, WorkflowExecution workflowExecution) {
        ProviderWorkflowProcessingResult workflowProcessingResult = getProviderWorkflowProcessingResult(nodeInstances, relationshipInstances);
        for (Root processedInstance : workflowProcessingResult.getNodeInstances().values()) {
            WorkflowUtil.declareNodeUninstallDependencies(nodeInstancesLifeCycles.get(processedInstance), nodeInstancesLifeCycles);
        }
        // This means all relationships are treated as hostOn
        // For AttachTo the relationship direction is inverted, it means the volume is hosted on the compute
        for (tosca.relationships.Root relationshipInstance : workflowProcessingResult.getRelationshipInstances()) {
            UninstallLifeCycleTasks sourceTasks = nodeInstancesLifeCycles.get(relationshipInstance.getSource());
            UninstallLifeCycleTasks targetTasks = nodeInstancesLifeCycles.get(relationshipInstance.getTarget());
            RelationshipUninstallLifeCycleTasks relationshipInstallLifeCycleTasks = relationshipInstancesLifeCycles.get(relationshipInstance);
            if (relationshipInstance instanceof tosca.relationships.AttachTo) {
                // FIXME Invert relationship for attach to !!
                // FIXME This will be removed in next tosca release !!
                UninstallLifeCycleTasks temp = sourceTasks;
                sourceTasks = targetTasks;
                targetTasks = temp;
            }
            WorkflowUtil.declareRelationshipUninstallDependencies(relationshipInstallLifeCycleTasks, sourceTasks, targetTasks);
            WorkflowUtil.declareHostedOnUninstallDependencies(relationshipInstallLifeCycleTasks, sourceTasks, targetTasks);
        }
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
        return new ProviderWorkflowProcessingResult(processedNodes, processedRelationships);
    }

    @Override
    public boolean isNativeType(Class<?> type) {
        return Compute.class.isAssignableFrom(type) || BlockStorage.class.isAssignableFrom(type) || Network.class.isAssignableFrom(type);
    }
}
