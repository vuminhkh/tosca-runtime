package com.toscaruntime.sdk.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;

import com.toscaruntime.sdk.model.OperationInputDefinition;

import tosca.nodes.Root;

public class OperationInputUtil {

    /**
     * Concatenate all instance ids separated by ","
     *
     * @param instances set of instances
     * @return all instance ids separated by ","
     */
    public static String makeInstancesVariable(Set<Root> instances) {
        return instances.stream().map(Root::getId).collect(Collectors.joining(","));
    }

    public static Map<String, Object> evaluateInputDefinitions(String prefix, Map<String, OperationInputDefinition> inputDefinitions) {
        Map<String, Object> inputs = new HashMap<>();
        if (inputDefinitions != null) {
            for (Map.Entry<String, OperationInputDefinition> inputDefinitionEntry : inputDefinitions.entrySet()) {
                String inputKey = inputDefinitionEntry.getKey();
                if (StringUtils.isNotBlank(prefix)) {
                    inputKey = prefix + "_" + inputKey;
                }
                inputs.put(inputKey, inputDefinitionEntry.getValue().evaluateOperationInput());
            }
        }
        return inputs;
    }

    public static Map<String, Object> evaluateInputDefinitions(Map<String, OperationInputDefinition> inputDefinitions) {
        return evaluateInputDefinitions(null, inputDefinitions);
    }
}
