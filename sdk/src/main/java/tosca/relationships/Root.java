package tosca.relationships;

import java.util.Map;

import com.toscaruntime.exception.IllegalFunctionException;
import com.toscaruntime.exception.NonRecoverableException;
import com.toscaruntime.sdk.AbstractRuntimeType;
import com.toscaruntime.util.FunctionUtil;

public abstract class Root extends AbstractRuntimeType {

    private tosca.nodes.Root source;

    private tosca.nodes.Root target;

    public tosca.nodes.Root getSource() {
        return source;
    }

    public tosca.nodes.Root getTarget() {
        return target;
    }

    protected Map<String, String> executeOperation(String operationName, String operationArtifactPath, Map<String, String> inputs) {
        switch (operationName) {
            case "pre_configure_source":
            case "post_configure_source":
            case "add_target":
            case "target_changed":
            case "remove_target":
                return executeSourceOperation(operationArtifactPath, inputs);
            case "pre_configure_target":
            case "post_configure_target":
            case "add_source":
                return executeTargetOperation(operationArtifactPath, inputs);
            default:
                if (operationName.endsWith("_source")) {
                    return executeSourceOperation(operationArtifactPath, inputs);
                } else if (operationName.endsWith("_target")) {
                    return executeTargetOperation(operationArtifactPath, inputs);
                } else {
                    throw new NonRecoverableException("Operation does not specify to be executed on source or target node (must be suffixed by _source or _target)");
                }
        }
    }

    protected Map<String, String> executeSourceOperation(String operationArtifactPath, Map<String, String> inputs) {
        if (source == null || source.getHost() == null) {
            throw new NonRecoverableException("The relationship's source is not set or not hosted on a compute, operation cannot be executed");
        }
        return source.getHost().execute(source.getId(), operationArtifactPath, inputs);
    }

    protected Map<String, String> executeTargetOperation(String operationArtifactPath, Map<String, String> inputs) {
        if (target == null) {
            throw new NonRecoverableException("The relationship's target is not hosted on a compute, operation cannot be executed");
        }
        return target.getHost().execute(target.getId(), operationArtifactPath, inputs);
    }

    public String evaluateFunction(String functionName, String... paths) {
        String entity = paths[0];
        switch (entity) {
            case "SOURCE":
                return source.evaluateFunction(functionName, FunctionUtil.setEntityToSelf(paths));
            case "TARGET":
                return target.evaluateFunction(functionName, FunctionUtil.setEntityToSelf(paths));
            case "SELF":
                switch (functionName) {
                    case "get_property":
                        return getProperty(paths[1]);
                    case "get_attribute":
                        return getAttribute(paths[1]);
                    case "get_input":
                        return getInput(paths[1]);
                    case "get_operation_output":
                        return getOperationOutput(paths[1], paths[2], paths[3]);
                    default:
                        throw new IllegalFunctionException("Function " + functionName + " is not supported");
                }
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

    @Override
    public void setAttribute(String key, String value) {
        super.setAttribute(key, value);
        // Attribute of the relationship is copied to the node
        getSource().setAttribute(key, value);
        getTarget().setAttribute(key, value);
    }

    @Override
    public void removeAttribute(String key) {
        super.removeAttribute(key);
        getSource().removeAttribute(key);
        getTarget().removeAttribute(key);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        Root root = (Root) o;

        if (source != null ? !source.equals(root.source) : root.source != null)
            return false;
        return !(target != null ? !target.equals(root.target) : root.target != null);

    }

    @Override
    public int hashCode() {
        int result = source != null ? source.hashCode() : 0;
        result = 31 * result + (target != null ? target.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Relationship{" +
                "source=" + source +
                ", target=" + target +
                '}';
    }
}
