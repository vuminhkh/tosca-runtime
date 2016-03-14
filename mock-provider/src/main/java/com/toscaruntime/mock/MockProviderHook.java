package com.toscaruntime.mock;

import java.util.Map;
import java.util.Set;

import com.toscaruntime.sdk.AbstractProviderHook;
import com.toscaruntime.sdk.Deployment;

import tosca.nodes.Root;

public class MockProviderHook extends AbstractProviderHook {
    @Override
    public void postConstruct(Deployment deployment, Map<String, String> providerProperties, Map<String, Object> bootstrapContext) {

    }

    @Override
    public void postConstructInstances(Map<String, Root> nodeInstances, Set<tosca.relationships.Root> relationshipInstances) {

    }
}
