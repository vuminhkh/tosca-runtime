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
        return normalizeIdentifiers(envVarTransformed);
    }

    private static Map<String, String> normalizeIdentifiers(Map<String, String> envVars) {
        // Shell script environment variable can only alpha numeric character and begin with an alphabetic character
        return envVars.entrySet().stream().collect(Collectors.toMap(
                entry -> {
                    String normalizedKey = entry.getKey().replaceAll("\\W", "_");
                    if (Character.isDigit(normalizedKey.charAt(0))) {
                        normalizedKey = "_" + normalizedKey;
                    }
                    return normalizedKey;
                }, Map.Entry::getValue
        ));
    }
}
