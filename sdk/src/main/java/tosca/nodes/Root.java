package tosca.nodes;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.toscaruntime.exception.IllegalFunctionException;
import com.toscaruntime.exception.NonRecoverableException;
import com.toscaruntime.sdk.AbstractRuntimeType;
import com.toscaruntime.util.FunctionUtil;

public abstract class Root extends AbstractRuntimeType {

    private String id;

    private String name;

    private Root parent;

    private Set<Root> dependsOnNodes = new HashSet<>();

    private Set<Root> dependedByNodes = new HashSet<>();

    private Set<Root> children = new HashSet<>();

    public Set<Root> getChildren() {
        return children;
    }

    public void setChildren(Set<Root> children) {
        this.children = children;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Compute getHost() {
        if (parent == null) {
            return null;
        }
        Root host = parent;
        while (host.getParent() != null) {
            host = host.getParent();
        }
        if (host instanceof Compute) {
            return (Compute) host;
        } else {
            return null;
        }
    }

    public Root getParent() {
        return parent;
    }

    public void setParent(Root parent) {
        this.parent = parent;
        this.parent.getChildren().add(this);
    }

    public Set<Root> getDependsOnNodes() {
        return dependsOnNodes;
    }

    public void setDependsOnNodes(Set<Root> dependsOnNodes) {
        this.dependsOnNodes = dependsOnNodes;
    }

    public Set<Root> getDependedByNodes() {
        return dependedByNodes;
    }

    public void setDependedByNodes(Set<Root> dependedByNodes) {
        this.dependedByNodes = dependedByNodes;
    }

    protected Map<String, String> executeOperation(String operationArtifactPath, Map<String, Object> inputs) {
        Compute host = getHost();
        if (host == null) {
            throw new NonRecoverableException("Non hosted node cannot have operation");
        }
        return host.execute(getId(), operationArtifactPath, inputs);
    }

    public void create() {

    }

    public void configure() {

    }

    public void start() {

    }

    public void stop() {

    }

    public void delete() {
        getAttributes().clear();
    }

    private String functionToString(String functionName, String... paths) {
        StringBuilder buffer = new StringBuilder(functionName).append("[ ");
        for (String path : paths) {
            buffer.append(path).append(",");
        }
        buffer.setLength(buffer.length() - 1);
        buffer.append("]");
        return buffer.toString();
    }

    public Object evaluateFunction(String functionName, String... paths) {
        if (paths.length == 0) {
            throw new IllegalFunctionException("Function " + functionName + " path is empty");
        }
        String entity = paths[0];
        Object value;
        switch (entity) {
            case "HOST":
                if (getParent() == null) {
                    throw new IllegalFunctionException("Cannot " + functionToString(functionName, paths) + " as this node does not have a parent");
                }
                return getParent().evaluateFunction(functionName, FunctionUtil.setEntityToSelf(paths));
            case "SELF":
                switch (functionName) {
                    case "get_property":
                        value = getProperty(paths[1]);
                        break;
                    case "get_input":
                        value = getInput(paths[1]);
                        break;
                    case "get_attribute":
                        value = getAttribute(paths[1]);
                        break;
                    case "get_operation_output":
                        value = getOperationOutput(paths[1], paths[2], paths[3]);
                        break;
                    default:
                        throw new IllegalFunctionException("Function " + functionName + " is not supported");
                }
                break;
            default:
                throw new IllegalFunctionException("Entity " + entity + " is not supported");
        }
        if (value == null) {
            if (getParent() != null) {
                return getParent().evaluateFunction(functionName, paths);
            } else {
                return "";
            }
        } else {
            return value;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Root root = (Root) o;
        return id.equals(root.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "NodeInstance{" +
                "name='" + name + '\'' +
                ", id='" + id + '\'' +
                '}';
    }
}
