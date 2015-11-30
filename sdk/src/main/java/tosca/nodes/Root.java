package tosca.nodes;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.toscaruntime.exception.IllegalFunctionException;
import com.toscaruntime.exception.NonRecoverableException;
import com.toscaruntime.sdk.AbstractRuntimeType;
import com.toscaruntime.util.FunctionUtil;

public abstract class Root extends AbstractRuntimeType {

    private static final Logger log = LoggerFactory.getLogger(Root.class);

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

    protected void executeOperation(String operationArtifactPath, Map<String, String> inputs) {
        Compute host = getHost();
        if (host == null) {
            throw new NonRecoverableException("Non hosted node cannot have operation");
        }
        host.execute(operationArtifactPath, inputs);
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

    public Object evaluateCompositeFunction(String functionName, Object... memberValue) {
        if ("concat".equals(functionName)) {
            return FunctionUtil.concat(memberValue);
        } else {
            throw new IllegalFunctionException("Function " + functionName + " is not supported on node");
        }
    }

    public String evaluateFunction(String functionName, String entity, String path) {
        String value;
        switch (entity) {
            case "HOST":
                if (getParent() == null) {
                    throw new IllegalFunctionException("Cannot access to HOST's <" + path + "> property/attribute as this node do not have a parent");
                }
                return getParent().evaluateFunction(functionName, "SELF", path);
            case "SELF":
                switch (functionName) {
                    case "get_property":
                        value = getProperty(path);
                        break;
                    case "get_attribute":
                        value = getAttribute(path);
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
                return getParent().evaluateFunction(functionName, entity, path);
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
