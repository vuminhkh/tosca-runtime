package tosca.nodes;

import java.util.Map;

public abstract class Compute extends Root {

    public abstract void execute(String operationArtifactPath, Map<String, String> inputs);
}
