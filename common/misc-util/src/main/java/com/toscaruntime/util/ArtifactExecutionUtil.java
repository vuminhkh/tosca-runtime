package com.toscaruntime.util;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Collectors;

public class ArtifactExecutionUtil {

    public static String escapeQuote(String envValue) {
        return envValue.replace("'", "'\\''");
    }

    /**
     * Process inputs and deployment artifacts for operation
     *
     * @param inputs inputs of the operation
     * @return parsed and processed environment variables
     */
    public static Map<String, Object> processInputs(Map<String, Object> inputs) {
        Map<String, Object> envVarTransformed = StreamUtil.safeEntryStream(inputs).collect(Collectors.toMap(Map.Entry::getKey, entry -> escapeQuote(PropertyUtil.propertyValueToString(entry.getValue()))));
        return normalizeIdentifiers(envVarTransformed);
    }

    /**
     * Process deployment artifacts to append recipe location to the relative artifact path
     *
     * @param deploymentArtifacts deployment artifacts map
     * @param recipeLocation      location of the recipe
     * @param fileSeparator       file separator
     * @return normalized artifact map
     */
    public static Map<String, Object> processDeploymentArtifacts(Map<String, String> deploymentArtifacts, String recipeLocation, String fileSeparator) {
        Map<String, Object> deploymentArtifactsTransformed = StreamUtil.safeEntryStream(deploymentArtifacts).collect(Collectors.toMap(Map.Entry::getKey, entry -> recipeLocation + fileSeparator + entry.getValue()));
        return normalizeIdentifiers(deploymentArtifactsTransformed);
    }

    public static String resolve(Path basePath, String childPath) {
        if (Paths.get(childPath).isAbsolute()) {
            return basePath.resolve(childPath.substring(1, childPath.length())).toString();
        } else {
            return basePath.resolve(childPath).toString();
        }
    }

    private static Map<String, Object> normalizeIdentifiers(Map<String, Object> envVars) {
        // Shell script environment variable can only alpha numeric character and begin with an alphabetic character
        return StreamUtil.safeEntryStream(envVars).collect(Collectors.toMap(
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
