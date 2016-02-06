package com.toscaruntime.sdk.workflow;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import com.toscaruntime.sdk.workflow.tasks.InstallLifeCycleTasks;
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

    private ExecutorService executorService = Executors.newCachedThreadPool(new ThreadFactory() {

        private AtomicInteger count = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("WorkflowThread_" + count.incrementAndGet());
            return t;
        }
    });


    /**
     * Perform installation of the given set of node instances and relationships instances
     *
     * @param nodeInstances         node instances to be installed
     * @param relationshipInstances relationship instances to be installed
     */
    public WorkflowExecution install(Map<String, Root> nodeInstances,
                                     Set<tosca.relationships.Root> relationshipInstances) {
        Map<Root, InstallLifeCycleTasks> allTasks = new HashMap<>();
        WorkflowExecution workflowExecution = new WorkflowExecution();
        for (Map.Entry<String, Root> nodeInstanceEntry : nodeInstances.entrySet()) {
            Root nodeInstance = nodeInstanceEntry.getValue();
            InstallLifeCycleTasks installLifeCycleTasks = new InstallLifeCycleTasks(nodeInstances, relationshipInstances, nodeInstance, executorService, workflowExecution);
            allTasks.put(nodeInstance, installLifeCycleTasks);
            workflowExecution.addTasks(installLifeCycleTasks.getTasks());
        }
        // For scaling sometimes we have relationship that is from or target out of the given node instances subset
        relationshipInstances.stream().filter(
                relationshipInstance -> !allTasks.containsKey(relationshipInstance.getSource())
        ).forEach(relationshipInstance -> {
            AddTargetTask addTargetTask = new AddTargetTask(nodeInstances, relationshipInstances, relationshipInstance.getSource(), executorService, workflowExecution);
            InstallLifeCycleTasks installLifeCycleTasks = new InstallLifeCycleTasks(addTargetTask);
            allTasks.put(relationshipInstance.getSource(), installLifeCycleTasks);
            workflowExecution.addTasks(installLifeCycleTasks.getTasks());
        });
        relationshipInstances.stream().filter(
                relationshipInstance -> !allTasks.containsKey(relationshipInstance.getTarget())
        ).forEach(relationshipInstance -> {
            AddSourceTask addSourceTask = new AddSourceTask(nodeInstances, relationshipInstances, relationshipInstance.getTarget(), executorService, workflowExecution);
            InstallLifeCycleTasks installLifeCycleTasks = new InstallLifeCycleTasks(addSourceTask);
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
        // Let start the install workflow by execute create task that do not have any dependencies
        // The whole workflow will be executed in a cascading manner
        allTasks.values().stream().filter(installLifeCycle ->
                installLifeCycle.getCreateTask().canRun()
        ).forEach(installLifeCycle -> executorService.execute(installLifeCycle.getCreateTask()));
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
        Map<Root, UninstallLifeCycleTasks> allTasks = new HashMap<>();
        WorkflowExecution workflowExecution = new WorkflowExecution();
        for (Map.Entry<String, Root> nodeInstanceEntry : nodeInstances.entrySet()) {
            Root nodeInstance = nodeInstanceEntry.getValue();
            UninstallLifeCycleTasks uninstallLifeCycleTasks = new UninstallLifeCycleTasks(nodeInstances, relationshipInstances, nodeInstance, executorService, workflowExecution);
            allTasks.put(nodeInstance, uninstallLifeCycleTasks);
            workflowExecution.addTasks(uninstallLifeCycleTasks.getTasks());
        }
        // For scaling sometimes we have relationship that is from or target out of the given node instances subset
        relationshipInstances.stream().filter(
                relationshipInstance -> !allTasks.containsKey(relationshipInstance.getSource())
        ).forEach(relationshipInstance -> {
            RemoveTargetTask removeTargetTask = new RemoveTargetTask(nodeInstances, relationshipInstances, relationshipInstance.getSource(), executorService, workflowExecution);
            allTasks.put(relationshipInstance.getSource(), new UninstallLifeCycleTasks(removeTargetTask));
        });
        relationshipInstances.stream().filter(
                relationshipInstance -> !allTasks.containsKey(relationshipInstance.getTarget())
        ).forEach(relationshipInstance -> {
            RemoveSourceTask removeSourceTask = new RemoveSourceTask(nodeInstances, relationshipInstances, relationshipInstance.getTarget(), executorService, workflowExecution);
            allTasks.put(relationshipInstance.getTarget(), new UninstallLifeCycleTasks(removeSourceTask));
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
        // Let start the uninstall workflow by execute create task that do not have any dependencies
        // The whole workflow will be executed in a cascading manner
        allTasks.values().stream().filter(uninstallLifeCycleTask ->
                uninstallLifeCycleTask.getRemoveSourceTask().canRun() || uninstallLifeCycleTask.getRemoveTargetTask().canRun()
        ).forEach(uninstallLifeCycle -> {
            if (uninstallLifeCycle.getRemoveSourceTask().canRun()) {
                executorService.execute(uninstallLifeCycle.getRemoveSourceTask());
            }
            if (uninstallLifeCycle.getRemoveTargetTask().canRun()) {
                executorService.execute(uninstallLifeCycle.getRemoveTargetTask());
            }
        });
        return workflowExecution;
    }
}
