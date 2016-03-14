package com.toscaruntime.sdk.workflow;

import java.util.HashMap;
import java.util.HashSet;
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
import com.toscaruntime.sdk.workflow.tasks.MockTask;
import com.toscaruntime.sdk.workflow.tasks.UninstallLifeCycleTasks;
import com.toscaruntime.sdk.workflow.tasks.relationships.AddSourceTask;
import com.toscaruntime.sdk.workflow.tasks.relationships.AddTargetTask;
import com.toscaruntime.sdk.workflow.tasks.relationships.RemoveSourceTask;
import com.toscaruntime.sdk.workflow.tasks.relationships.RemoveTargetTask;

import tosca.nodes.Root;

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
            return new InstallLifeCycleTasks(new MockTask("to be copied from", nodeInstances, relationshipInstances, nodeInstance, workflowExecution));
        }

        @Override
        public InstallLifeCycleTasks create(AddSourceTask addSourceTask) {
            return new InstallLifeCycleTasks(WorkflowUtil.mockTask("to be copied from", addSourceTask));
        }

        @Override
        public InstallLifeCycleTasks create(AddTargetTask addTargetTask) {
            return new InstallLifeCycleTasks(WorkflowUtil.mockTask("to be copied from", addTargetTask));
        }
    };

    private InstallLifeCycleTasksFactory installLifeCycleTasksFactory = new InstallLifeCycleTasksFactory() {
        @Override
        public InstallLifeCycleTasks create(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, WorkflowExecution workflowExecution) {
            return new InstallLifeCycleTasks(nodeInstances, relationshipInstances, nodeInstance, workflowExecution);
        }

        @Override
        public InstallLifeCycleTasks create(AddSourceTask addSourceTask) {
            return new InstallLifeCycleTasks(addSourceTask);
        }

        @Override
        public InstallLifeCycleTasks create(AddTargetTask addTargetTask) {
            return new InstallLifeCycleTasks(addTargetTask);
        }
    };

    private UninstallLifeCycleTasksFactory mockUninstallLifeCycleTasksFactory = new UninstallLifeCycleTasksFactory() {
        @Override
        public UninstallLifeCycleTasks create(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, WorkflowExecution workflowExecution) {
            return new UninstallLifeCycleTasks(new MockTask("to be copied from", nodeInstances, relationshipInstances, nodeInstance, workflowExecution));
        }

        @Override
        public UninstallLifeCycleTasks create(RemoveSourceTask removeSourceTask) {
            return new UninstallLifeCycleTasks(WorkflowUtil.mockTask("to be copied from", removeSourceTask));
        }

        @Override
        public UninstallLifeCycleTasks create(RemoveTargetTask removeTargetTask) {
            return new UninstallLifeCycleTasks(WorkflowUtil.mockTask("to be copied from", removeTargetTask));
        }
    };

    private UninstallLifeCycleTasksFactory uninstallLifeCycleTasksFactory = new UninstallLifeCycleTasksFactory() {
        @Override
        public UninstallLifeCycleTasks create(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, WorkflowExecution workflowExecution) {
            return new UninstallLifeCycleTasks(nodeInstances, relationshipInstances, nodeInstance, workflowExecution);
        }

        @Override
        public UninstallLifeCycleTasks create(RemoveSourceTask removeSourceTask) {
            return new UninstallLifeCycleTasks(removeSourceTask);
        }

        @Override
        public UninstallLifeCycleTasks create(RemoveTargetTask removeTargetTask) {
            return new UninstallLifeCycleTasks(removeTargetTask);
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

        InstallLifeCycleTasks create(AddSourceTask addSourceTask);

        InstallLifeCycleTasks create(AddTargetTask addTargetTask);
    }

    private interface UninstallLifeCycleTasksFactory {

        UninstallLifeCycleTasks create(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, WorkflowExecution workflowExecution);

        UninstallLifeCycleTasks create(RemoveSourceTask removeSourceTask);

        UninstallLifeCycleTasks create(RemoveTargetTask removeTargetTask);
    }

    private WorkflowExecution doInstall(Map<String, Root> nodeInstances,
                                        Set<tosca.relationships.Root> relationshipInstances,
                                        InstallLifeCycleTasksFactory lifeCycleTasksFactory) {
        Map<Root, InstallLifeCycleTasks> allTasks = new HashMap<>();
        WorkflowExecution workflowExecution = new WorkflowExecution(createWorkflowExecutorService());
        for (Map.Entry<String, Root> nodeInstanceEntry : nodeInstances.entrySet()) {
            Root nodeInstance = nodeInstanceEntry.getValue();
            InstallLifeCycleTasks installLifeCycleTasks = lifeCycleTasksFactory.create(nodeInstances, relationshipInstances, nodeInstance, workflowExecution);
            allTasks.put(nodeInstance, installLifeCycleTasks);
            workflowExecution.addTasks(installLifeCycleTasks.getTasks());
        }
        // For scaling sometimes we have relationship that is from or target out of the given node instances subset
        relationshipInstances.stream().filter(
                relationshipInstance -> !allTasks.containsKey(relationshipInstance.getSource())
        ).forEach(relationshipInstance -> {
            AddTargetTask addTargetTask = new AddTargetTask(nodeInstances, relationshipInstances, relationshipInstance.getSource(), workflowExecution);
            InstallLifeCycleTasks installLifeCycleTasks = lifeCycleTasksFactory.create(addTargetTask);
            allTasks.put(relationshipInstance.getSource(), installLifeCycleTasks);
            workflowExecution.addTasks(installLifeCycleTasks.getTasks());
        });
        relationshipInstances.stream().filter(
                relationshipInstance -> !allTasks.containsKey(relationshipInstance.getTarget())
        ).forEach(relationshipInstance -> {
            AddSourceTask addSourceTask = new AddSourceTask(nodeInstances, relationshipInstances, relationshipInstance.getTarget(), workflowExecution);
            InstallLifeCycleTasks installLifeCycleTasks = lifeCycleTasksFactory.create(addSourceTask);
            allTasks.put(relationshipInstance.getTarget(), installLifeCycleTasks);
            workflowExecution.addTasks(installLifeCycleTasks.getTasks());
        });
        ProviderWorkflowProcessingResult providerWorkflowResult = providerHook.postConstructInstallWorkflow(nodeInstances, relationshipInstances, allTasks);
        Set<tosca.relationships.Root> nonProcessedRelationshipInstances = new HashSet<>(relationshipInstances);
        nonProcessedRelationshipInstances.removeAll(providerWorkflowResult.getRelationshipInstances());
        // Only process life cycles of nodes that were not processed by the provider, and based on relationships that were not processed by the provider
        allTasks.entrySet().stream().filter(taskEntry -> !providerWorkflowResult.getNodeInstances().containsKey(taskEntry.getKey().getId())).forEach(taskEntry -> {
            Root instance = taskEntry.getKey();
            InstallLifeCycleTasks instanceLifeCycle = taskEntry.getValue();
            WorkflowUtil.declareNodeInstallDependenciesWithRelationship(instance, instanceLifeCycle, allTasks, nonProcessedRelationshipInstances);
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
        Map<Root, UninstallLifeCycleTasks> allTasks = new HashMap<>();
        WorkflowExecution workflowExecution = new WorkflowExecution(createWorkflowExecutorService());
        for (Map.Entry<String, Root> nodeInstanceEntry : nodeInstances.entrySet()) {
            Root nodeInstance = nodeInstanceEntry.getValue();
            UninstallLifeCycleTasks uninstallLifeCycleTasks = uninstallLifeCycleTasksFactory.create(nodeInstances, relationshipInstances, nodeInstance, workflowExecution);
            allTasks.put(nodeInstance, uninstallLifeCycleTasks);
            workflowExecution.addTasks(uninstallLifeCycleTasks.getTasks());
        }
        // For scaling sometimes we have relationship that is from or target out of the given node instances subset
        relationshipInstances.stream().filter(
                relationshipInstance -> !allTasks.containsKey(relationshipInstance.getSource())
        ).forEach(relationshipInstance -> {
            RemoveTargetTask removeTargetTask = new RemoveTargetTask(nodeInstances, relationshipInstances, relationshipInstance.getSource(), workflowExecution);
            UninstallLifeCycleTasks uninstallLifeCycleTasks = uninstallLifeCycleTasksFactory.create(removeTargetTask);
            allTasks.put(relationshipInstance.getSource(), uninstallLifeCycleTasks);
            workflowExecution.addTasks(uninstallLifeCycleTasks.getTasks());
        });
        relationshipInstances.stream().filter(
                relationshipInstance -> !allTasks.containsKey(relationshipInstance.getTarget())
        ).forEach(relationshipInstance -> {
            RemoveSourceTask removeSourceTask = new RemoveSourceTask(nodeInstances, relationshipInstances, relationshipInstance.getTarget(), workflowExecution);
            UninstallLifeCycleTasks uninstallLifeCycleTasks = uninstallLifeCycleTasksFactory.create(removeSourceTask);
            allTasks.put(relationshipInstance.getTarget(), uninstallLifeCycleTasks);
            workflowExecution.addTasks(uninstallLifeCycleTasks.getTasks());
        });
        ProviderWorkflowProcessingResult providerWorkflowResult = providerHook.postConstructUninstallWorkflow(nodeInstances, relationshipInstances, allTasks);
        Set<tosca.relationships.Root> nonProcessedRelationshipInstances = new HashSet<>(relationshipInstances);
        nonProcessedRelationshipInstances.removeAll(providerWorkflowResult.getRelationshipInstances());
        // Only process life cycles of nodes that were not processed by the provider, and based on relationships that were not processed by the provider
        allTasks.entrySet().stream().filter(taskEntry -> !providerWorkflowResult.getNodeInstances().containsKey(taskEntry.getKey().getId())).forEach(taskEntry -> {
            Root instance = taskEntry.getKey();
            UninstallLifeCycleTasks instanceLifeCycle = taskEntry.getValue();
            WorkflowUtil.declareNodeUninstallDependenciesWithRelationship(instance, instanceLifeCycle, allTasks, relationshipInstances);
        });
        workflowExecution.launch();
        return workflowExecution;
    }

    public void setProviderHook(ProviderHook providerHook) {
        this.providerHook = providerHook;
    }
}
