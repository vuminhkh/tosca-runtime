package com.toscaruntime.sdk.workflow;

import com.toscaruntime.deployment.DeploymentPersister;
import com.toscaruntime.exception.deployment.workflow.InvalidWorkflowException;
import com.toscaruntime.sdk.ProviderHook;
import com.toscaruntime.sdk.ProviderWorkflowProcessingResult;
import com.toscaruntime.sdk.util.WorkflowUtil;
import com.toscaruntime.sdk.workflow.tasks.*;
import com.toscaruntime.sdk.workflow.tasks.nodes.GenericNodeTask;
import com.toscaruntime.sdk.workflow.tasks.relationships.GenericRelationshipTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tosca.nodes.Root;
import tosca.relationships.HostedOn;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * The default workflow engine for toscaruntime
 *
 * @author Minh Khang VU
 */
public class WorkflowEngine {

    private ProviderHook providerHook;

    private DeploymentPersister deploymentPersister;

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

    private static ExecutorService createWorkflowExecutorService() {
        return Executors.newCachedThreadPool(new ThreadFactory() {

            private AtomicInteger count = new AtomicInteger();

            @Override
            public Thread newThread(Runnable runnable) {
                Thread t = new Thread(runnable);
                t.setDaemon(true);
                t.setName("WorkflowThread_" + count.incrementAndGet());
                return t;
            }
        });
    }

    private InstallLifeCycleTasksFactory mockInstallLifeCycleTasksFactory = new InstallLifeCycleTasksFactory() {
        @Override
        public InstallLifeCycleTasks create(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance) {
            return WorkflowUtil.mockInstallLifeCycleTasks(nodeInstances, relationshipInstances, nodeInstance);
        }

        @Override
        public RelationshipInstallLifeCycleTasks create(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, tosca.relationships.Root relationshipInstance) {
            return WorkflowUtil.mockRelationshipInstallLifeCycleTasks(nodeInstances, relationshipInstances, relationshipInstance);
        }
    };

    private UninstallLifeCycleTasksFactory mockUninstallLifeCycleTasksFactory = new UninstallLifeCycleTasksFactory() {
        @Override
        public UninstallLifeCycleTasks create(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance) {
            return WorkflowUtil.mockUninstallLifeCycleTasks(nodeInstances, relationshipInstances, nodeInstance);
        }

        @Override
        public RelationshipUninstallLifeCycleTasks create(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, tosca.relationships.Root relationshipInstance) {
            return WorkflowUtil.mockRelationshipUninstallLifeCycleTasks(nodeInstances, relationshipInstances, relationshipInstance);
        }
    };

    private InstallLifeCycleTasksFactory installLifeCycleTasksFactory = new InstallLifeCycleTasksFactory() {
        @Override
        public InstallLifeCycleTasks create(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance) {
            return new InstallLifeCycleTasks(nodeInstances, relationshipInstances, nodeInstance);
        }

        @Override
        public RelationshipInstallLifeCycleTasks create(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, tosca.relationships.Root relationshipInstance) {
            return new RelationshipInstallLifeCycleTasks(nodeInstances, relationshipInstances, relationshipInstance);
        }
    };

    private UninstallLifeCycleTasksFactory uninstallLifeCycleTasksFactory = new UninstallLifeCycleTasksFactory() {
        @Override
        public UninstallLifeCycleTasks create(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance) {
            return new UninstallLifeCycleTasks(nodeInstances, relationshipInstances, nodeInstance);
        }

        @Override
        public RelationshipUninstallLifeCycleTasks create(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, tosca.relationships.Root relationshipInstance) {
            return new RelationshipUninstallLifeCycleTasks(nodeInstances, relationshipInstances, relationshipInstance);
        }
    };

    private void validateDryRun(WorkflowExecution dryRunWorkflowExecution) {
        try {
            // Normally the mock tasks do nothing and so it must be immediate
            boolean dryRunFinished = dryRunWorkflowExecution.waitForCompletion(1, TimeUnit.DAYS);
            if (!dryRunFinished) {
                log.error("Workflow did not finish: \n {}", dryRunWorkflowExecution.toString());
                throw new InvalidWorkflowException("Workflow is invalid and did not finish");
            } else {
                log.info("Workflow dry run finished successfully");
            }
        } catch (InvalidWorkflowException e) {
            throw e;
        } catch (Throwable e) {
            throw new InvalidWorkflowException("Workflow is invalid and did not pass through dry run", e);
        }
    }

    private void augmentWorkflow(WorkflowExecution workflowExecution, List<AbstractTask> beforeTasks, List<AbstractTask> afterTasks) {
        // Make all runnable task depends on before tasks, so that it will be run in the first place
        workflowExecution.getTasksLeft().stream().filter(AbstractTask::canRun).forEach(task -> task.dependsOn(beforeTasks.toArray(new AbstractTask[beforeTasks.size()])));
        // After tasks are run at the end
        afterTasks.forEach(afterTask -> afterTask.dependsOn(workflowExecution.getTasksLeft().toArray(new AbstractTask[workflowExecution.getTasksLeft().size()])));
        workflowExecution.addTasks(beforeTasks);
        workflowExecution.addTasks(afterTasks);
    }

    /**
     * Build an install workflow
     *
     * @param beforeTasks           tasks that should have done before the install workflow
     * @param nodeInstances         the concerned node instances
     * @param relationshipInstances the concerned relationship instances
     * @param workflowId            id of the workflow
     * @return the built workflow execution
     */
    public WorkflowExecution buildInstallWorkflow(List<AbstractTask> beforeTasks,
                                                  List<AbstractTask> afterTasks,
                                                  Map<String, Root> nodeInstances,
                                                  Set<tosca.relationships.Root> relationshipInstances,
                                                  String workflowId) {
        validateDryRun(doInstall(nodeInstances, relationshipInstances, mockInstallLifeCycleTasksFactory, () -> new WorkflowExecution(workflowId, createWorkflowExecutorService(), null)));
        WorkflowExecution workflowExecution = doBuildInstallWorkflow(nodeInstances, relationshipInstances, installLifeCycleTasksFactory, () -> new WorkflowExecution(workflowId, createWorkflowExecutorService(), deploymentPersister));
        augmentWorkflow(workflowExecution, beforeTasks, afterTasks);
        return workflowExecution;
    }

    private interface InstallLifeCycleTasksFactory {

        InstallLifeCycleTasks create(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance);

        RelationshipInstallLifeCycleTasks create(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, tosca.relationships.Root relationshipInstance);
    }

    private interface UninstallLifeCycleTasksFactory {

        UninstallLifeCycleTasks create(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance);

        RelationshipUninstallLifeCycleTasks create(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, tosca.relationships.Root relationshipInstance);
    }

    private interface WorkflowExecutionFactory {
        WorkflowExecution create();
    }

    private WorkflowExecution doBuildInstallWorkflow(Map<String, Root> nodeInstances,
                                                     Set<tosca.relationships.Root> relationshipInstances,
                                                     InstallLifeCycleTasksFactory lifeCycleTasksFactory,
                                                     WorkflowExecutionFactory workflowExecutionFactory) {
        WorkflowExecution workflowExecution = workflowExecutionFactory.create();
        Map<Root, InstallLifeCycleTasks> allNodesTasks = new HashMap<>();
        for (Map.Entry<String, Root> nodeInstanceEntry : nodeInstances.entrySet()) {
            Root nodeInstance = nodeInstanceEntry.getValue();
            InstallLifeCycleTasks installLifeCycleTasks = lifeCycleTasksFactory.create(nodeInstances, relationshipInstances, nodeInstance);
            allNodesTasks.put(nodeInstance, installLifeCycleTasks);
            workflowExecution.addTasks(installLifeCycleTasks.getTasks());
        }
        Map<tosca.relationships.Root, RelationshipInstallLifeCycleTasks> allRelationshipsTasks = new HashMap<>();
        for (tosca.relationships.Root relationship : relationshipInstances) {
            RelationshipInstallLifeCycleTasks relationshipInstallLifeCycleTasks = lifeCycleTasksFactory.create(nodeInstances, relationshipInstances, relationship);
            allRelationshipsTasks.put(relationship, relationshipInstallLifeCycleTasks);
            workflowExecution.addTasks(relationshipInstallLifeCycleTasks.getTasks());
        }
        relationshipInstances.stream().forEach(relationshipInstance -> {
            InstallLifeCycleTasks sourceInstallLifeCycleTasks = allNodesTasks.get(relationshipInstance.getSource());
            if (sourceInstallLifeCycleTasks == null) {
                // Relationship which comes from a node out of the set of nodes (for example when we scale)
                sourceInstallLifeCycleTasks = WorkflowUtil.mockInstallLifeCycleTasks(nodeInstances, relationshipInstances, relationshipInstance.getSource());
                allNodesTasks.put(relationshipInstance.getSource(), sourceInstallLifeCycleTasks);
                workflowExecution.addTasks(sourceInstallLifeCycleTasks.getTasks());
            }
            InstallLifeCycleTasks targetInstallLifeCycleTasks = allNodesTasks.get(relationshipInstance.getTarget());
            if (targetInstallLifeCycleTasks == null) {
                // Relationship which comes out of the set of nodes (for example when we scale)
                targetInstallLifeCycleTasks = WorkflowUtil.mockInstallLifeCycleTasks(nodeInstances, relationshipInstances, relationshipInstance.getTarget());
                allNodesTasks.put(relationshipInstance.getTarget(), targetInstallLifeCycleTasks);
                workflowExecution.addTasks(targetInstallLifeCycleTasks.getTasks());
            }
        });

        // Let the provider process the workflow, for example the provider will handle the workflow of every native IAAS resources and relationships
        ProviderWorkflowProcessingResult providerWorkflowResult = providerHook.postConstructInstallWorkflow(nodeInstances, relationshipInstances, allNodesTasks, allRelationshipsTasks, workflowExecution);
        // Only process life cycles of nodes that were not processed by the provider
        allNodesTasks.entrySet().stream()
                .filter(taskEntry -> !providerWorkflowResult.getNodeInstances().containsKey(taskEntry.getKey().getId()))
                .forEach(taskEntry -> WorkflowUtil.declareNodeInstallDependencies(taskEntry.getValue()));
        // Only process life cycles of relationships that were not processed by the provider
        allRelationshipsTasks.entrySet().stream()
                .filter(taskEntry -> !providerWorkflowResult.getRelationshipInstances().contains(taskEntry.getKey()))
                .forEach(taskEntry -> {
                    tosca.relationships.Root relationshipInstance = taskEntry.getKey();
                    InstallLifeCycleTasks sourceInstallLifeCycleTasks = allNodesTasks.get(relationshipInstance.getSource());
                    InstallLifeCycleTasks targetInstallLifeCycleTasks = allNodesTasks.get(relationshipInstance.getTarget());
                    WorkflowUtil.declareRelationshipInstallDependencies(taskEntry.getValue(), sourceInstallLifeCycleTasks, targetInstallLifeCycleTasks);
                    if (relationshipInstance instanceof HostedOn) {
                        // Specific hosted on task dependencies
                        WorkflowUtil.declareHostedOnInstallDependencies(taskEntry.getValue(), sourceInstallLifeCycleTasks, targetInstallLifeCycleTasks);
                    } else {
                        // Specific depends on task dependencies
                        WorkflowUtil.declareDependsOnInstallDependencies(taskEntry.getValue(), sourceInstallLifeCycleTasks, targetInstallLifeCycleTasks);
                    }
                });
        return workflowExecution;
    }

    private WorkflowExecution doInstall(Map<String, Root> nodeInstances,
                                        Set<tosca.relationships.Root> relationshipInstances,
                                        InstallLifeCycleTasksFactory lifeCycleTasksFactory,
                                        WorkflowExecutionFactory workflowExecutionFactory) {
        WorkflowExecution workflowExecution = doBuildInstallWorkflow(nodeInstances, relationshipInstances, lifeCycleTasksFactory, workflowExecutionFactory);
        workflowExecution.launch();
        return workflowExecution;
    }

    public WorkflowExecution buildExecuteNodeOperationWorkflow(List<AbstractTask> beforeTasks,
                                                               List<AbstractTask> afterTasks,
                                                               Map<String, Root> nodeInstances,
                                                               Set<tosca.relationships.Root> relationshipInstances,
                                                               Set<Root> concernedInstances,
                                                               String interfaceName,
                                                               String operationName,
                                                               String workflowId,
                                                               boolean transientExecution) {
        List<AbstractTask> nodeTasks = concernedInstances.stream().map(instance -> new GenericNodeTask(nodeInstances, relationshipInstances, instance, interfaceName, operationName)).collect(Collectors.toList());

        WorkflowExecution workflowExecution;
        if (transientExecution) {
            workflowExecution = new WorkflowExecution(workflowId, createWorkflowExecutorService());
        } else {
            workflowExecution = new WorkflowExecution(workflowId, createWorkflowExecutorService(), deploymentPersister);
        }
        workflowExecution.addTasks(nodeTasks);
        augmentWorkflow(workflowExecution, beforeTasks, afterTasks);
        return workflowExecution;
    }

    public WorkflowExecution buildExecuteRelationshipOperationWorkflow(List<AbstractTask> beforeTasks,
                                                                       List<AbstractTask> afterTasks,
                                                                       Map<String, Root> nodeInstances,
                                                                       Set<tosca.relationships.Root> relationshipInstances,
                                                                       Set<tosca.relationships.Root> concernedRelationshipInstances,
                                                                       String interfaceName,
                                                                       String operationName,
                                                                       String workflowId,
                                                                       boolean transientExecution) {
        List<AbstractTask> relationshipTasks = concernedRelationshipInstances.stream().map(relationshipInstance -> new GenericRelationshipTask(nodeInstances, relationshipInstances, relationshipInstance, interfaceName, operationName)).collect(Collectors.toList());
        WorkflowExecution workflowExecution;
        if (transientExecution) {
            workflowExecution = new WorkflowExecution(workflowId, createWorkflowExecutorService());
        } else {
            workflowExecution = new WorkflowExecution(workflowId, createWorkflowExecutorService(), deploymentPersister);
        }
        workflowExecution.addTasks(relationshipTasks);
        augmentWorkflow(workflowExecution, beforeTasks, afterTasks);
        return workflowExecution;
    }

    /**
     * Build an uninstall workflow without launching it
     *
     * @param afterTasks            tasks that should be done after the uninstall workflow to cleanup resources
     * @param nodeInstances         the concerned node instances
     * @param relationshipInstances the concerned relationship instances
     * @param workflowId            id of the workflow
     * @return the built workflow execution
     */
    public WorkflowExecution buildUninstallWorkflow(List<AbstractTask> beforeTasks,
                                                    List<AbstractTask> afterTasks,
                                                    Map<String, Root> nodeInstances,
                                                    Set<tosca.relationships.Root> relationshipInstances,
                                                    String workflowId) {
        validateDryRun(doUninstall(nodeInstances, relationshipInstances, mockUninstallLifeCycleTasksFactory, () -> new WorkflowExecution(workflowId, createWorkflowExecutorService(), null)));
        WorkflowExecution workflowExecution = doBuildUninstallWorkflow(nodeInstances, relationshipInstances, uninstallLifeCycleTasksFactory, () -> new WorkflowExecution(workflowId, createWorkflowExecutorService(), deploymentPersister));
        augmentWorkflow(workflowExecution, beforeTasks, afterTasks);
        return workflowExecution;
    }

    private WorkflowExecution doBuildUninstallWorkflow(Map<String, Root> nodeInstances,
                                                       Set<tosca.relationships.Root> relationshipInstances,
                                                       UninstallLifeCycleTasksFactory uninstallLifeCycleTasksFactory,
                                                       WorkflowExecutionFactory workflowExecutionFactory) {
        Map<Root, UninstallLifeCycleTasks> allNodesTasks = new HashMap<>();
        WorkflowExecution workflowExecution = workflowExecutionFactory.create();
        for (Map.Entry<String, Root> nodeInstanceEntry : nodeInstances.entrySet()) {
            Root nodeInstance = nodeInstanceEntry.getValue();
            UninstallLifeCycleTasks uninstallLifeCycleTasks = uninstallLifeCycleTasksFactory.create(nodeInstances, relationshipInstances, nodeInstance);
            allNodesTasks.put(nodeInstance, uninstallLifeCycleTasks);
            workflowExecution.addTasks(uninstallLifeCycleTasks.getTasks());
        }
        Map<tosca.relationships.Root, RelationshipUninstallLifeCycleTasks> allRelationshipsTasks = new HashMap<>();
        for (tosca.relationships.Root relationship : relationshipInstances) {
            RelationshipUninstallLifeCycleTasks relationshipUninstallLifeCycleTasks = uninstallLifeCycleTasksFactory.create(nodeInstances, relationshipInstances, relationship);
            allRelationshipsTasks.put(relationship, relationshipUninstallLifeCycleTasks);
            workflowExecution.addTasks(relationshipUninstallLifeCycleTasks.getTasks());
        }
        allRelationshipsTasks.entrySet().stream().forEach(taskEntry -> {
            tosca.relationships.Root relationshipInstance = taskEntry.getKey();
            UninstallLifeCycleTasks sourceInstallLifeCycleTasks = allNodesTasks.get(relationshipInstance.getSource());
            if (sourceInstallLifeCycleTasks == null) {
                // Relationship which comes from a node out of the set of nodes (for example when we scale)
                sourceInstallLifeCycleTasks = WorkflowUtil.mockUninstallLifeCycleTasks(nodeInstances, relationshipInstances, relationshipInstance.getSource());
                allNodesTasks.put(relationshipInstance.getSource(), sourceInstallLifeCycleTasks);
                workflowExecution.addTasks(sourceInstallLifeCycleTasks.getTasks());
            }
            UninstallLifeCycleTasks targetInstallLifeCycleTasks = allNodesTasks.get(relationshipInstance.getTarget());
            if (targetInstallLifeCycleTasks == null) {
                // Relationship which comes out of the set of nodes (for example when we scale)
                targetInstallLifeCycleTasks = WorkflowUtil.mockUninstallLifeCycleTasks(nodeInstances, relationshipInstances, relationshipInstance.getTarget());
                allNodesTasks.put(relationshipInstance.getTarget(), targetInstallLifeCycleTasks);
                workflowExecution.addTasks(targetInstallLifeCycleTasks.getTasks());
            }
        });
        ProviderWorkflowProcessingResult providerWorkflowResult = providerHook.postConstructUninstallWorkflow(nodeInstances, relationshipInstances, allNodesTasks, allRelationshipsTasks, workflowExecution);
        // Only process life cycles of nodes that were not processed by the provider
        allNodesTasks.entrySet().stream()
                .filter(taskEntry -> !providerWorkflowResult.getNodeInstances().containsKey(taskEntry.getKey().getId()))
                .forEach(taskEntry -> WorkflowUtil.declareNodeUninstallDependencies(taskEntry.getValue()));
        // Only process life cycles of relationships that were not processed by the provider
        allRelationshipsTasks.entrySet().stream()
                .filter(taskEntry -> !providerWorkflowResult.getRelationshipInstances().contains(taskEntry.getKey()))
                .forEach(taskEntry -> {
                    tosca.relationships.Root relationshipInstance = taskEntry.getKey();
                    UninstallLifeCycleTasks sourceInstallLifeCycleTasks = allNodesTasks.get(relationshipInstance.getSource());
                    UninstallLifeCycleTasks targetInstallLifeCycleTasks = allNodesTasks.get(relationshipInstance.getTarget());
                    WorkflowUtil.declareRelationshipUninstallDependencies(taskEntry.getValue(), sourceInstallLifeCycleTasks, targetInstallLifeCycleTasks);
                    if (relationshipInstance instanceof HostedOn) {
                        // Specific hosted on task dependencies
                        WorkflowUtil.declareHostedOnUninstallDependencies(taskEntry.getValue(), sourceInstallLifeCycleTasks, targetInstallLifeCycleTasks);
                    } else {
                        // Specific depends on task dependencies
                        WorkflowUtil.declareDependsOnUninstallDependencies(taskEntry.getValue(), sourceInstallLifeCycleTasks, targetInstallLifeCycleTasks);
                    }
                });
        return workflowExecution;
    }

    private WorkflowExecution doUninstall(Map<String, Root> nodeInstances,
                                          Set<tosca.relationships.Root> relationshipInstances,
                                          UninstallLifeCycleTasksFactory uninstallLifeCycleTasksFactory,
                                          WorkflowExecutionFactory workflowExecutionFactory) {
        WorkflowExecution workflowExecution = doBuildUninstallWorkflow(nodeInstances, relationshipInstances, uninstallLifeCycleTasksFactory, workflowExecutionFactory);
        workflowExecution.launch();
        return workflowExecution;
    }

    public void setProviderHook(ProviderHook providerHook) {
        this.providerHook = providerHook;
    }

    public void setDeploymentPersister(DeploymentPersister deploymentPersister) {
        this.deploymentPersister = deploymentPersister;
    }
}
