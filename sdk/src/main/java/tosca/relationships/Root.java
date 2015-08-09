package tosca.relationships;

import java.util.Map;

import com.mkv.exception.IllegalFunctionException;
import com.mkv.exception.NonRecoverableException;
import com.mkv.tosca.sdk.AbstractRuntimeType;

public abstract class Root extends AbstractRuntimeType {

    private tosca.nodes.Root source;

    private tosca.nodes.Root target;

    public tosca.nodes.Root getSource() {
        return source;
    }

    public tosca.nodes.Root getTarget() {
        return target;
    }

    protected void executeOperation(String operationName, String operationArtifactPath, Map<String, String> inputs) {
        switch (operationName) {
        case "pre_configure_source":
        case "post_configure_source":
        case "add_target":
        case "target_changed":
        case "remove_target":
            executeSourceOperation(operationArtifactPath, inputs);
            break;
        case "pre_configure_target":
        case "post_configure_target":
        case "add_source":
            executeTargetOperation(operationArtifactPath, inputs);
            break;
        default:
            if (operationName.endsWith("_source")) {
                executeSourceOperation(operationArtifactPath, inputs);
            } else if (operationName.endsWith("_target")) {
                executeTargetOperation(operationArtifactPath, inputs);
            } else {
                throw new NonRecoverableException("Operation does not specify to be executed on source or target node (must be suffixed by _source or _target)");
            }
        }
    }

    protected void executeSourceOperation(String operationArtifactPath, Map<String, String> inputs) {
        if (source == null || source.getHost() == null) {
            throw new NonRecoverableException("The relationship's source is not set or not hosted on a compute, operation cannot be executed");
        }
        source.getHost().execute(operationArtifactPath, inputs);
    }

    protected void executeTargetOperation(String operationArtifactPath, Map<String, String> inputs) {
        if (target == null) {
            throw new NonRecoverableException("The relationship's target is not hosted on a compute, operation cannot be executed");
        }
        target.getHost().execute(operationArtifactPath, inputs);
    }

    public String evaluateFunction(String functionName, String entity, String path) {
        String value = null;
        switch (entity) {
        case "SOURCE":
            return source.evaluateFunction(functionName, "SELF", path);
        case "TARGET":
            return target.evaluateFunction(functionName, "SELF", path);
        default:
            throw new IllegalFunctionException("Entity " + entity + " is not supported");
        }
    }

    public void setSource(tosca.nodes.Root source) {
        this.source = source;
    }

    public void setTarget(tosca.nodes.Root target) {
        this.target = target;
    }

    public void preConfigureSource() {
    }

    public void preConfigureTarget() {
    }

    public void postConfigureSource() {
    }

    public void postConfigureTarget() {
    }

    public void addTarget() {
    }

    public void addSource() {
    }

    public void removeSource() {
    }

    public void removeTarget() {
    }
}
