package com.toscaruntime.sdk;

import tosca.nodes.Root;

public abstract class AbstractPluginHook implements PluginHook {

    @Override
    public void preNodeInitialLoad(Root node) {
    }

    @Override
    public void postNodeInitialLoad(Root node) {
    }

    @Override
    public void preRelationshipInitialLoad(tosca.relationships.Root node) {
    }

    @Override
    public void postRelationshipInitialLoad(tosca.relationships.Root node) {
    }

    @Override
    public void preExecuteNodeOperation(Root node, String interfaceName, String operationName) {
    }

    @Override
    public void postExecuteNodeOperation(Root node, String interfaceName, String operationName) {
    }

    @Override
    public void preExecuteRelationshipOperation(tosca.relationships.Root relationship, String interfaceName, String operationName) {
    }

    @Override
    public void postExecuteRelationshipOperation(tosca.relationships.Root relationship, String interfaceName, String operationName) {
    }
}
