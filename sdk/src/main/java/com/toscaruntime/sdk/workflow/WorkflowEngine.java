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

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

    private static ExecutorService createWorkflowExecutorService() {
        return Executors.newCachedThreadPool(new ThreadFactory() {

            private AtomicInteger count = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName("WorkflowThread_" + count.incrementAndGet());
                return t;
            }
        });
    }

    private InstallLifeCycleTasksFactory mockInstallLifeCycleTasksFactory = new InstallLifeCycleTasksFactory() {
        @Override
        public InstallLifeCycleTasks create(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, ExecutorService executorService, WorkflowExecution workflowExecution) {
            return new InstallLifeCycleTasks(new MockTask("to be copied from", nodeInstances, relationshipInstances, nodeInstance, executorService, workflowExecution));
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
        public InstallLifeCycleTasks create(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, ExecutorService executorService, WorkflowExecution workflowExecution) {
            return new InstallLifeCycleTasks(nodeInstances, relationshipInstances, nodeInstance, executorService, workflowExecution);
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
        public UninstallLifeCycleTasks create(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, ExecutorService executorService, WorkflowExecution workflowExecution) {
            return new UninstallLifeCycleTasks(new MockTask("to be copied from", nodeInstances, relationshipInstances, nodeInstance, executorService, workflowExecution));
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
        public UninstallLifeCycleTasks create(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, ExecutorService executorService, WorkflowExecution workflowExecution) {
            return new UninstallLifeCycleTasks(nodeInstances, relationshipInstances, nodeInstance, executorService, workflowExecution);
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

        InstallLifeCycleTasks create(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, ExecutorService executorService, WorkflowExecution workflowExecution);

        InstallLifeCycleTasks create(AddSourceTask addSourceTask);

        InstallLifeCycleTasks create(AddTargetTask addTargetTask);
    }

    private interface UninstallLifeCycleTasksFactory {

        UninstallLifeCycleTasks create(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, Root nodeInstance, ExecutorService executorService, WorkflowExecution workflowExecution);

        UninstallLifeCycleTasks create(RemoveSourceTask removeSourceTask);

        UninstallLifeCycleTasks create(RemoveTargetTask removeTargetTask);
    }

    private WorkflowExecution doInstall(Map<String, Root> nodeInstances,
                                        Set<tosca.relationships.Root> relationshipInstances,
                                        InstallLifeCycleTasksFactory lifeCycleTasksFactory) {
        Map<Root, InstallLifeCycleTasks> allTasks = new HashMap<>();
        ExecutorService executorService = createWorkflowExecutorService();
        WorkflowExecution workflowExecution = new WorkflowExecution(executorService);
        for (Map.Entry<String, Root> nodeInstanceEntry : nodeInstances.entrySet()) {
            Root nodeInstance = nodeInstanceEntry.getValue();
            InstallLifeCycleTasks installLifeCycleTasks = lifeCycleTasksFactory.create(nodeInstances, relationshipInstances, nodeInstance, executorService, workflowExecution);
            allTasks.put(nodeInstance, installLifeCycleTasks);
            workflowExecution.addTasks(installLifeCycleTasks.getTasks());
        }
        // For scaling sometimes we have relationship that is from or target out of the given node instances subset
        relationshipInstances.stream().filter(
                relationshipInstance -> !allTasks.containsKey(relationshipInstance.getSource())
        ).forEach(relationshipInstance -> {
            AddTargetTask addTargetTask = new AddTargetTask(nodeInstances, relationshipInstances, relationshipInstance.getSource(), executorService, workflowExecution);
            InstallLifeCycleTasks installLifeCycleTasks = lifeCycleTasksFactory.create(addTargetTask);
            allTasks.put(relationshipInstance.getSource(), installLifeCycleTasks);
            workflowExecution.addTasks(installLifeCycleTasks.getTasks());
        });
        relationshipInstances.stream().filter(
                relationshipInstance -> !allTasks.containsKey(relationshipInstance.getTarget())
        ).forEach(relationshipInstance -> {
            AddSourceTask addSourceTask = new AddSourceTask(nodeInstances, relationshipInstances, relationshipInstance.getTarget(), executorService, workflowExecution);
            InstallLifeCycleTasks installLifeCycleTasks = lifeCycleTasksFactory.create(addSourceTask);
            allTasks.put(relationshipInstance.getTarget(), installLifeCycleTasks);
            workflowExecution.addTasks(installLifeCycleTasks.getTasks());
        });
        for (Map.Entry<Root, InstallLifeCycleTasks> taskEntry : allTasks.entrySet()) {
            Root instance = taskEntry.getKey();
            InstallLifeCycleTasks instanceLifeCycle = taskEntry.getValue();

            Root host = instance.getHost();
            InstallLifeCycleTasks hostInstallLifeCycle = allTasks.get(host);
            if (hostInstallLifeCycle != null) {
                // Instance create task must depends on start task of its host
                // It means that the life cycle of the host and the hosted is sequential
                instanceLifeCycle.getCreateTask().dependsOn(hostInstallLifeCycle.getStartTask());
                // Add source of the the host depends on start of the instance, this way add source is called only when the source is already started
                hostInstallLifeCycle.getAddSourceTask().dependsOn(instanceLifeCycle.getStartTask());
            }
            instance.getNode().getDependsOnNodes().forEach(dependencyName ->
                    allTasks.keySet().stream().filter(taskInstance -> taskInstance.getName().equals(dependencyName)).forEach(dependency -> {
                        // instance = A depends on dependency = B
                        InstallLifeCycleTasks dependencyLifeCycle = allTasks.get(dependency);
                        if (dependencyLifeCycle != null) {
                            // A depends on B then A is configured only if B is started
                            instanceLifeCycle.getPreConfigureSourceTask().dependsOn(dependencyLifeCycle.getStartTask());
                            instanceLifeCycle.getPreConfigureTargetTask().dependsOn(dependencyLifeCycle.getStartTask());
                            // A depends on B then B is configured only if A is created
                            dependencyLifeCycle.getPreConfigureSourceTask().dependsOn(instanceLifeCycle.getCreateTask());
                            dependencyLifeCycle.getPreConfigureTargetTask().dependsOn(instanceLifeCycle.getCreateTask());
                            // A depends on B then B add source is executed only if A is started
                            dependencyLifeCycle.getAddSourceTask().dependsOn(instanceLifeCycle.getStartTask());
                        }
                    }));
        }
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
        ExecutorService executorService = createWorkflowExecutorService();
        WorkflowExecution workflowExecution = new WorkflowExecution(executorService);
        for (Map.Entry<String, Root> nodeInstanceEntry : nodeInstances.entrySet()) {
            Root nodeInstance = nodeInstanceEntry.getValue();
            UninstallLifeCycleTasks uninstallLifeCycleTasks = uninstallLifeCycleTasksFactory.create(nodeInstances, relationshipInstances, nodeInstance, executorService, workflowExecution);
            allTasks.put(nodeInstance, uninstallLifeCycleTasks);
            workflowExecution.addTasks(uninstallLifeCycleTasks.getTasks());
        }
        // For scaling sometimes we have relationship that is from or target out of the given node instances subset
        relationshipInstances.stream().filter(
                relationshipInstance -> !allTasks.containsKey(relationshipInstance.getSource())
        ).forEach(relationshipInstance -> {
            RemoveTargetTask removeTargetTask = new RemoveTargetTask(nodeInstances, relationshipInstances, relationshipInstance.getSource(), executorService, workflowExecution);
            UninstallLifeCycleTasks uninstallLifeCycleTasks = uninstallLifeCycleTasksFactory.create(removeTargetTask);
            allTasks.put(relationshipInstance.getSource(), uninstallLifeCycleTasks);
            workflowExecution.addTasks(uninstallLifeCycleTasks.getTasks());
        });
        relationshipInstances.stream().filter(
                relationshipInstance -> !allTasks.containsKey(relationshipInstance.getTarget())
        ).forEach(relationshipInstance -> {
            RemoveSourceTask removeSourceTask = new RemoveSourceTask(nodeInstances, relationshipInstances, relationshipInstance.getTarget(), executorService, workflowExecution);
            UninstallLifeCycleTasks uninstallLifeCycleTasks = uninstallLifeCycleTasksFactory.create(removeSourceTask);
            allTasks.put(relationshipInstance.getTarget(), uninstallLifeCycleTasks);
            workflowExecution.addTasks(uninstallLifeCycleTasks.getTasks());
        });
        for (Map.Entry<Root, UninstallLifeCycleTasks> taskEntry : allTasks.entrySet()) {
            Root instance = taskEntry.getKey();
            UninstallLifeCycleTasks instanceLifeCycle = taskEntry.getValue();

            Set<Root> instanceChildren = instance.getChildren();
            for (Root instanceChild : instanceChildren) {
                UninstallLifeCycleTasks instanceChildLifeCycle = allTasks.get(instanceChild);
                // The host can be stopped only if all children has been deleted
                instanceLifeCycle.getStopTask().dependsOn(instanceChildLifeCycle.getDeleteTask());
                // The child can be stopped only if the host has been notified of the fact that it's being removed
                instanceChildLifeCycle.getStopTask().dependsOn(instanceLifeCycle.getRemoveSourceTask());
            }

            instance.getNode().getDependedByNodes().forEach(subjectionName ->
                    allTasks.keySet().stream().filter(taskInstance -> taskInstance.getName().equals(subjectionName)).forEach(subjection -> {
                        // instance = A depended by subjection = B
                        UninstallLifeCycleTasks subjectionLifeCycle = allTasks.get(subjection);
                        if (subjectionLifeCycle != null) {
                            // If A is depended by B, then only stop A if B has been stopped already
                            instanceLifeCycle.getStopTask().dependsOn(subjectionLifeCycle.getStopTask());
                            // If A is depended by B, then notify A removal before B is stopped
                            subjectionLifeCycle.getStopTask().dependsOn(instanceLifeCycle.getRemoveSourceTask());
                        }
                    }));
        }
        workflowExecution.launch();
        return workflowExecution;
    }
}
