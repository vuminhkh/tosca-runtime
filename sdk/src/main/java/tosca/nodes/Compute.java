package tosca.nodes;

import java.util.Map;
import java.util.Set;

import com.google.common.collect.Maps;

/**
 * A compute is a node that can execute an artifact locally on it-self
 */
public abstract class Compute extends Root {

    public abstract Map<String, String> execute(String nodeId, String operationArtifactPath, Map<String, Object> inputs);

    public Map<String, String> getHostDeploymentArtifacts() {
        Set<Root> children = getChildren();
        Map<String, String> allArtifacts = Maps.newHashMap(deploymentArtifacts);
        for (Root child : children) {
            allArtifacts.putAll(child.getDeploymentArtifacts());
        }
        return allArtifacts;
    }
}
