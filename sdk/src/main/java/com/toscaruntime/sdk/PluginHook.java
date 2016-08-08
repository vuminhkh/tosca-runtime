package com.toscaruntime.sdk;

import tosca.nodes.Root;

public interface PluginHook extends Hook {

    /**
     * Called before executing node operation
     *
     * @param node          the node
     * @param interfaceName interface's name
     * @param operationName operation's name
     */
    void preExecuteNodeOperation(Root node, String interfaceName, String operationName) throws Throwable;

    /**
     * Called before executing node operation
     *
     * @param node          the node
     * @param interfaceName interface's name
     * @param operationName operation's name
     */
    void postExecuteNodeOperation(Root node, String interfaceName, String operationName) throws Throwable;

    /**
     * Called before executing relationship operation
     *
     * @param relationship  the relationship
     * @param interfaceName interface's name
     * @param operationName operation's name
     */
    void preExecuteRelationshipOperation(tosca.relationships.Root relationship, String interfaceName, String operationName) throws Throwable;

    /**
     * Called after executing relationship operation
     *
     * @param relationship  the relationship
     * @param interfaceName interface's name
     * @param operationName operation's name
     */
    void postExecuteRelationshipOperation(tosca.relationships.Root relationship, String interfaceName, String operationName) throws Throwable;
}
