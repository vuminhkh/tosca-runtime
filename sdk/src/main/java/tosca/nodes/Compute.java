package tosca.nodes;

import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import com.google.common.collect.Maps;
import com.toscaruntime.sdk.AbstractRuntimeType;

/**
 * A compute is a node that can execute an artifact locally on it-self
 */
public abstract class Compute extends Root {

    public abstract Map<String, String> execute(String nodeId, String operationArtifactPath, Map<String, Object> inputs);

    public Map<String, String> getHostDeploymentArtifacts() {
        Map<String, String> allArtifacts = Maps.newHashMap();
        Queue<AbstractRuntimeType> childrenQueue = new LinkedList<>();
        childrenQueue.add(this);
        while (!childrenQueue.isEmpty()) {
            AbstractRuntimeType child = childrenQueue.poll();
            allArtifacts.putAll(child.getDeploymentArtifacts());
            if (child instanceof Root) {
                Root node = (Root) child;
                childrenQueue.addAll(node.getChildren());
                childrenQueue.addAll(node.getDeployment().getRelationshipInstanceBySourceId(node.getId()));
                childrenQueue.addAll(node.getDeployment().getRelationshipInstanceByTargetId(node.getId()));
            }
        }
        return allArtifacts;
    }
}
