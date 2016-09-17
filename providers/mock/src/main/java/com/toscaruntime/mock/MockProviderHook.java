package com.toscaruntime.mock;

import com.toscaruntime.sdk.AbstractProviderHook;
import com.toscaruntime.sdk.Deployment;
import tosca.nodes.Root;

import java.util.Map;
import java.util.Set;

public class MockProviderHook extends AbstractProviderHook {
    @Override
    public void postConstruct(Deployment deployment, Map<String, Map<String, Object>> pluginProperties, Map<String, Object> bootstrapContext) {

    }

    @Override
    public void postConstructInstances(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances) {

    }
}
