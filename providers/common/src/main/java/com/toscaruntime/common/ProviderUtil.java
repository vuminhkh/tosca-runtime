package com.toscaruntime.common;

import com.toscaruntime.configuration.ConnectionRegistry;
import com.toscaruntime.util.PropertyUtil;
import tosca.nodes.Root;

import java.util.Map;

public class ProviderUtil {

    private static String getNodeTarget(Map<String, Object> nodeProperties) {
        return PropertyUtil.getPropertyAsString(nodeProperties, "provider.target", "default");
    }

    public static <T> T newConnection(ConnectionRegistry<T> connectionRegistry, Root node) {
        return connectionRegistry.getConnection(getNodeTarget(node.getProperties()), (Map<String, Object>) PropertyUtil.getProperty(node.getProperties(), "provider.configuration"));
    }
}
