package tosca.nodes;

import java.util.Map;

import com.google.common.collect.Maps;
import com.mkv.exception.IllegalFunctionException;
import com.mkv.exception.NonRecoverableException;

public abstract class Root {

    private String name;

    private Compute host;

    private Root parent;

    public Map<String, String> properties = Maps.newHashMap();

    public Map<String, String> attributes = Maps.newHashMap();

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {

        return name;
    }

    public Compute getHost() {
        return host;
    }

    public void setHost(Compute host) {
        this.host = host;
    }

    public Root getParent() {
        return parent;
    }

    public void setParent(Root parent) {
        this.parent = parent;
    }

    protected void executeOperation(String operationArtifactPath, Map<String, String> inputs) {
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
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    public String getInput(String functionName, String entity, String path) {
        String value = null;
        switch (entity) {
        case "HOST":
            if (getParent() == null) {
                throw new IllegalFunctionException("Cannot access to HOST's <" + path + "> property/attribute as this node do not have a parent");
            }
            return getParent().getInput(functionName, "SELF", path);
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
                return getParent().getInput(functionName, entity, path);
            } else {
                return "";
            }
        } else {
            return value;
        }
    }

    protected String getProperty(String propertyName) {
        return this.properties.get(propertyName);
    }

    protected String getAttribute(String attributeName) {
        return this.attributes.get(attributeName);
    }
}
