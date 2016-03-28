package com.toscaruntime.sdk.workflow.tasks;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.toscaruntime.sdk.workflow.tasks.relationships.AbstractRelationshipTask;
import com.toscaruntime.sdk.workflow.tasks.relationships.AddSourceTask;
import com.toscaruntime.sdk.workflow.tasks.relationships.AddTargetTask;
import com.toscaruntime.sdk.workflow.tasks.relationships.PostConfigureSourceTask;
import com.toscaruntime.sdk.workflow.tasks.relationships.PostConfigureTargetTask;
import com.toscaruntime.sdk.workflow.tasks.relationships.PreConfigureSourceTask;
import com.toscaruntime.sdk.workflow.tasks.relationships.PreConfigureTargetTask;

import tosca.nodes.Root;

public class RelationshipInstallLifeCycleTasks extends AbstractLifeCycleTasks {

    private AbstractRelationshipTask preConfigureSourceTask;

    private AbstractRelationshipTask preConfigureTargetTask;

    private AbstractTask postConfigureSourceTask;

    private AbstractTask postConfigureTargetTask;

    private AbstractTask addSourceTask;

    private AbstractTask addTargetTask;

    public RelationshipInstallLifeCycleTasks(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances, tosca.relationships.Root relationshipInstance) {
        this.preConfigureSourceTask = new PreConfigureSourceTask(nodeInstances, relationshipInstances, relationshipInstance);
        this.preConfigureTargetTask = new PreConfigureTargetTask(nodeInstances, relationshipInstances, relationshipInstance);
        this.postConfigureSourceTask = new PostConfigureSourceTask(nodeInstances, relationshipInstances, relationshipInstance);
        this.postConfigureTargetTask = new PostConfigureTargetTask(nodeInstances, relationshipInstances, relationshipInstance);
        this.addSourceTask = new AddSourceTask(nodeInstances, relationshipInstances, relationshipInstance);
        this.addTargetTask = new AddTargetTask(nodeInstances, relationshipInstances, relationshipInstance);
    }

    public RelationshipInstallLifeCycleTasks(AbstractRelationshipTask preConfigureSourceTask, AbstractRelationshipTask preConfigureTargetTask, AbstractTask postConfigureSourceTask, AbstractTask postConfigureTargetTask, AbstractTask addSourceTask, AbstractTask addTargetTask) {
        this.preConfigureSourceTask = preConfigureSourceTask;
        this.preConfigureTargetTask = preConfigureTargetTask;
        this.postConfigureSourceTask = postConfigureSourceTask;
        this.postConfigureTargetTask = postConfigureTargetTask;
        this.addSourceTask = addSourceTask;
        this.addTargetTask = addTargetTask;
    }

    public AbstractRelationshipTask getPreConfigureSourceTask() {
        return preConfigureSourceTask;
    }

    public AbstractRelationshipTask getPreConfigureTargetTask() {
        return preConfigureTargetTask;
    }

    public AbstractTask getPostConfigureSourceTask() {
        return postConfigureSourceTask;
    }

    public AbstractTask getPostConfigureTargetTask() {
        return postConfigureTargetTask;
    }

    public AbstractTask getAddSourceTask() {
        return addSourceTask;
    }

    public AbstractTask getAddTargetTask() {
        return addTargetTask;
    }

    @Override
    public List<AbstractTask> getTasks() {
        return Arrays.asList(
                getPreConfigureSourceTask(),
                getPreConfigureTargetTask(),
                getPostConfigureSourceTask(),
                getPostConfigureTargetTask(),
                getAddSourceTask(),
                getAddTargetTask()
        );
    }
}
