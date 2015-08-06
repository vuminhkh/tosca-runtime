package tosca.nodes;

import java.util.Map;

/**
 * A compute is a node that can execute an artifact locally on it-self
 */
public abstract class Compute extends Root {

    public abstract void execute(String operationArtifactPath, Map<String, String> inputs);
}
