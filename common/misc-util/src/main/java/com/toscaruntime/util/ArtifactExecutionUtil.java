package com.toscaruntime.util;

import java.util.Map;
import java.util.stream.Collectors;

public class ArtifactExecutionUtil {

    public static String escapeQuote(String envValue) {
        return envValue.replace("'", "'\\''");
    }

    /**
     * Process inputs and deployment artifacts for operation
     *
     * @param inputs              inputs of the operation
     * @param deploymentArtifacts deployment artifacts for the operation
     * @param recipeLocation      location of the recipe
     * @return parsed and processed environment variables
     */
    public static Map<String, String> processInputs(Map<String, Object> inputs, Map<String, String> deploymentArtifacts, String recipeLocation, String fileSeparator) {
        Map<String, String> envVarTransformed = inputs.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> escapeQuote(PropertyUtil.propertyValueToString(entry.getValue()))));
        Map<String, String> deploymentArtifactsTransformed = deploymentArtifacts.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> recipeLocation + fileSeparator + entry.getValue()));
        envVarTransformed.putAll(deploymentArtifactsTransformed);
        return envVarTransformed;
    }
}
