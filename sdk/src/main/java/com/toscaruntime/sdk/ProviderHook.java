package com.toscaruntime.sdk;

import java.util.Map;
import java.util.Set;

import com.toscaruntime.sdk.workflow.WorkflowExecution;
import com.toscaruntime.sdk.workflow.tasks.InstallLifeCycleTasks;
import com.toscaruntime.sdk.workflow.tasks.RelationshipInstallLifeCycleTasks;
import com.toscaruntime.sdk.workflow.tasks.RelationshipUninstallLifeCycleTasks;
import com.toscaruntime.sdk.workflow.tasks.UninstallLifeCycleTasks;

import tosca.nodes.Root;

public interface ProviderHook {

    /**
     * Post construct a deployment. This provides a hook to inject provider's configuration and bootstrap context's information into the deployment.
     * This method is executed just after the initialization of the deployment but before any workflow execution.
     *
     * @param deployment         the deployment to post process
     * @param providerProperties properties of the provider
     * @param bootstrapContext   bootstrap information
     */
    void postConstruct(Deployment deployment, Map<String, String> providerProperties, Map<String, Object> bootstrapContext);

    /**
     * Post construct created instances for the deployment. This method is used to post construct newly created instances for the deployment.
     *
     * @param nodeInstances         nodes to initialize
     * @param relationshipInstances relationships to initialize
     */
    void postConstructInstances(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances);

    /**
     * This hook permit to process the install workflow of native nodes as Compute, BlockStorage, Network (the provider can override the workflow of others nodes if it wishes so)
     *
     * @param nodeInstances                   all nodes impacted by the workflow
     * @param relationshipInstances           all relationship instances impacted by the workflow
     * @param nodeInstancesLifeCycles         install life cycle tasks of nodes
     * @param relationshipInstancesLifeCycles install life cycle tasks of the relationships
     * @param workflowExecution               workflow execution
     * @return all nodes, relationships that the provider processed workflow for and so will not be processed by the workflow engine
     */
    ProviderWorkflowProcessingResult postConstructInstallWorkflow(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Map<Root, InstallLifeCycleTasks> nodeInstancesLifeCycles, Map<tosca.relationships.Root, RelationshipInstallLifeCycleTasks> relationshipInstancesLifeCycles, WorkflowExecution workflowExecution);

    /**
     * This hook permit to process the uninstall workflow of native nodes as Compute, BlockStorage, Network (the provider can override the workflow of others nodes if it wishes so)
     *
     * @param nodeInstances         all nodes impacted by the workflow
     * @param relationshipInstances all relationship instances impacted by the workflow
     * @param nodesLifeCycles       uninstall life cycle tasks of nodes
     * @param workflowExecution     workflow execution
     * @return all nodes, relationships that the provider processed workflow for and so will not be processed by the workflow engine
     */
    ProviderWorkflowProcessingResult postConstructUninstallWorkflow(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Map<Root, UninstallLifeCycleTasks> nodesLifeCycles, Map<tosca.relationships.Root, RelationshipUninstallLifeCycleTasks> relationshipInstancesLifeCycles, WorkflowExecution workflowExecution);

    /**
     * Check whether the given type is natively managed by the provider (compute, block storage, network)
     *
     * @param type the type to test
     * @return true if native false otherwise
     */
    boolean isNativeType(Class<?> type);
}
