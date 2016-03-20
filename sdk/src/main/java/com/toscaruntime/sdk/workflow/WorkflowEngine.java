package com.toscaruntime.sdk.workflow;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.toscaruntime.exception.deployment.workflow.InvalidWorkflowException;
import com.toscaruntime.sdk.ProviderHook;
import com.toscaruntime.sdk.ProviderWorkflowProcessingResult;
import com.toscaruntime.sdk.util.WorkflowUtil;
import com.toscaruntime.sdk.workflow.tasks.InstallLifeCycleTasks;
import com.toscaruntime.sdk.workflow.tasks.RelationshipInstallLifeCycleTasks;
import com.toscaruntime.sdk.workflow.tasks.RelationshipUninstallLifeCycleTasks;
import com.toscaruntime.sdk.workflow.tasks.UninstallLifeCycleTasks;

import tosca.nodes.Root;
import tosca.relationships.HostedOn;

/**
 * The default workflow engine for toscaruntime
 *
 * @author Minh Khang VU
 */
public class WorkflowEngine {

    private ProviderHook providerHook;

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
        public InstallLifeCycleTasks create(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, WorkflowExecution workflowExecution) {
            return WorkflowUtil.mockInstallLifeCycleTasks(nodeInstances, relationshipInstances, nodeInstance, workflowExecution);
        }

        @Override
        public RelationshipInstallLifeCycleTasks create(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, tosca.relationships.Root relationshipInstance, WorkflowExecution workflowExecution) {
            return WorkflowUtil.mockRelationshipInstallLifeCycleTasks(nodeInstances, relationshipInstances, relationshipInstance, workflowExecution);
        }
    };

    private UninstallLifeCycleTasksFactory mockUninstallLifeCycleTasksFactory = new UninstallLifeCycleTasksFactory() {
        @Override
        public UninstallLifeCycleTasks create(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, WorkflowExecution workflowExecution) {
            return WorkflowUtil.mockUninstallLifeCycleTasks(nodeInstances, relationshipInstances, nodeInstance, workflowExecution);
        }

        @Override
        public RelationshipUninstallLifeCycleTasks create(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, tosca.relationships.Root relationshipInstance, WorkflowExecution workflowExecution) {
            return WorkflowUtil.mockRelationshipUninstallLifeCycleTasks(nodeInstances, relationshipInstances, relationshipInstance, workflowExecution);
        }
    };

    private InstallLifeCycleTasksFactory installLifeCycleTasksFactory = new InstallLifeCycleTasksFactory() {
        @Override
        public InstallLifeCycleTasks create(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, WorkflowExecution workflowExecution) {
            return new InstallLifeCycleTasks(nodeInstances, relationshipInstances, nodeInstance, workflowExecution);
        }

        @Override
        public RelationshipInstallLifeCycleTasks create(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, tosca.relationships.Root relationshipInstance, WorkflowExecution workflowExecution) {
            return new RelationshipInstallLifeCycleTasks(nodeInstances, relationshipInstances, relationshipInstance, workflowExecution);
        }
    };

    private UninstallLifeCycleTasksFactory uninstallLifeCycleTasksFactory = new UninstallLifeCycleTasksFactory() {
        @Override
        public UninstallLifeCycleTasks create(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, WorkflowExecution workflowExecution) {
            return new UninstallLifeCycleTasks(nodeInstances, relationshipInstances, nodeInstance, workflowExecution);
        }

        @Override
        public RelationshipUninstallLifeCycleTasks create(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, tosca.relationships.Root relationshipInstance, WorkflowExecution workflowExecution) {
            return new RelationshipUninstallLifeCycleTasks(nodeInstances, relationshipInstances, relationshipInstance, workflowExecution);
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
                log.info("Workflow dry run execution finished, begin real execution");
            }
        } catch (InvalidWorkflowException e) {
            throw e;
        } catch (Throwable e) {
            throw new InvalidWorkflowException("Workflow is invalid and did not pass through dry run", e);
        }
    }

    /**
     * Perform installation of the given set of node instances and relationships instances
     *
     * @param nodeInstances         node instances to be installed
     * @param relationshipInstances relationship instances to be installed
     */
    public WorkflowExecution install(Map<String, Root> nodeInstances,
                                     Set<tosca.relationships.Root> relationshipInstances) {
        validateDryRun(doInstall(nodeInstances, relationshipInstances, mockInstallLifeCycleTasksFactory));
        return doInstall(nodeInstances, relationshipInstances, installLifeCycleTasksFactory);
    }

    private interface InstallLifeCycleTasksFactory {

        InstallLifeCycleTasks create(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, WorkflowExecution workflowExecution);

        RelationshipInstallLifeCycleTasks create(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, tosca.relationships.Root relationshipInstance, WorkflowExecution workflowExecution);
    }

    private interface UninstallLifeCycleTasksFactory {

        UninstallLifeCycleTasks create(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, WorkflowExecution workflowExecution);

        RelationshipUninstallLifeCycleTasks create(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, tosca.relationships.Root relationshipInstance, WorkflowExecution workflowExecution);
    }

    private WorkflowExecution doInstall(Map<String, Root> nodeInstances,
                                        Set<tosca.relationships.Root> relationshipInstances,
                                        InstallLifeCycleTasksFactory lifeCycleTasksFactory) {
        WorkflowExecution workflowExecution = new WorkflowExecution(createWorkflowExecutorService());
        Map<Root, InstallLifeCycleTasks> allNodesTasks = new HashMap<>();
        for (Map.Entry<String, Root> nodeInstanceEntry : nodeInstances.entrySet()) {
            Root nodeInstance = nodeInstanceEntry.getValue();
            InstallLifeCycleTasks installLifeCycleTasks = lifeCycleTasksFactory.create(nodeInstances, relationshipInstances, nodeInstance, workflowExecution);
            allNodesTasks.put(nodeInstance, installLifeCycleTasks);
            workflowExecution.addTasks(installLifeCycleTasks.getTasks());
        }
        Map<tosca.relationships.Root, RelationshipInstallLifeCycleTasks> allRelationshipsTasks = new HashMap<>();
        for (tosca.relationships.Root relationship : relationshipInstances) {
            RelationshipInstallLifeCycleTasks relationshipInstallLifeCycleTasks = lifeCycleTasksFactory.create(nodeInstances, relationshipInstances, relationship, workflowExecution);
            allRelationshipsTasks.put(relationship, relationshipInstallLifeCycleTasks);
            workflowExecution.addTasks(relationshipInstallLifeCycleTasks.getTasks());
        }
        relationshipInstances.stream().forEach(relationshipInstance -> {
            InstallLifeCycleTasks sourceInstallLifeCycleTasks = allNodesTasks.get(relationshipInstance.getSource());
            if (sourceInstallLifeCycleTasks == null) {
                // Relationship which comes from a node out of the set of nodes (for example when we scale)
                sourceInstallLifeCycleTasks = WorkflowUtil.mockInstallLifeCycleTasks(nodeInstances, relationshipInstances, relationshipInstance.getSource(), workflowExecution);
                allNodesTasks.put(relationshipInstance.getSource(), sourceInstallLifeCycleTasks);
                workflowExecution.addTasks(sourceInstallLifeCycleTasks.getTasks());
            }
            InstallLifeCycleTasks targetInstallLifeCycleTasks = allNodesTasks.get(relationshipInstance.getTarget());
            if (targetInstallLifeCycleTasks == null) {
                // Relationship which comes out of the set of nodes (for example when we scale)
                targetInstallLifeCycleTasks = WorkflowUtil.mockInstallLifeCycleTasks(nodeInstances, relationshipInstances, relationshipInstance.getTarget(), workflowExecution);
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
        workflowExecution.launch();
        return workflowExecution;
    }

    /**
     * Perform un-installation of the given set of node instances and relationships instances
     *
     * @param nodeInstances         node instances to be uninstalled
     * @param relationshipInstances relationship instances to be uninstalled
     */
    public WorkflowExecution uninstall(Map<String, Root> nodeInstances,
                                       Set<tosca.relationships.Root> relationshipInstances) {
        validateDryRun(doUninstall(nodeInstances, relationshipInstances, mockUninstallLifeCycleTasksFactory));
        return doUninstall(nodeInstances, relationshipInstances, uninstallLifeCycleTasksFactory);
    }

    public WorkflowExecution doUninstall(Map<String, Root> nodeInstances,
                                         Set<tosca.relationships.Root> relationshipInstances,
                                         UninstallLifeCycleTasksFactory uninstallLifeCycleTasksFactory) {
        Map<Root, UninstallLifeCycleTasks> allNodesTasks = new HashMap<>();
        WorkflowExecution workflowExecution = new WorkflowExecution(createWorkflowExecutorService());
        for (Map.Entry<String, Root> nodeInstanceEntry : nodeInstances.entrySet()) {
            Root nodeInstance = nodeInstanceEntry.getValue();
            UninstallLifeCycleTasks uninstallLifeCycleTasks = uninstallLifeCycleTasksFactory.create(nodeInstances, relationshipInstances, nodeInstance, workflowExecution);
            allNodesTasks.put(nodeInstance, uninstallLifeCycleTasks);
            workflowExecution.addTasks(uninstallLifeCycleTasks.getTasks());
        }
        Map<tosca.relationships.Root, RelationshipUninstallLifeCycleTasks> allRelationshipsTasks = new HashMap<>();
        for (tosca.relationships.Root relationship : relationshipInstances) {
            RelationshipUninstallLifeCycleTasks relationshipUninstallLifeCycleTasks = uninstallLifeCycleTasksFactory.create(nodeInstances, relationshipInstances, relationship, workflowExecution);
            allRelationshipsTasks.put(relationship, relationshipUninstallLifeCycleTasks);
            workflowExecution.addTasks(relationshipUninstallLifeCycleTasks.getTasks());
        }
        allRelationshipsTasks.entrySet().stream().forEach(taskEntry -> {
            tosca.relationships.Root relationshipInstance = taskEntry.getKey();
            UninstallLifeCycleTasks sourceInstallLifeCycleTasks = allNodesTasks.get(relationshipInstance.getSource());
            if (sourceInstallLifeCycleTasks == null) {
                // Relationship which comes from a node out of the set of nodes (for example when we scale)
                sourceInstallLifeCycleTasks = WorkflowUtil.mockUninstallLifeCycleTasks(nodeInstances, relationshipInstances, relationshipInstance.getSource(), workflowExecution);
                allNodesTasks.put(relationshipInstance.getSource(), sourceInstallLifeCycleTasks);
                workflowExecution.addTasks(sourceInstallLifeCycleTasks.getTasks());
            }
            UninstallLifeCycleTasks targetInstallLifeCycleTasks = allNodesTasks.get(relationshipInstance.getTarget());
            if (targetInstallLifeCycleTasks == null) {
                // Relationship which comes out of the set of nodes (for example when we scale)
                targetInstallLifeCycleTasks = WorkflowUtil.mockUninstallLifeCycleTasks(nodeInstances, relationshipInstances, relationshipInstance.getTarget(), workflowExecution);
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
        workflowExecution.launch();
        return workflowExecution;
    }

    public void setProviderHook(ProviderHook providerHook) {
        this.providerHook = providerHook;
    }
}
