package tosca.nodes;

import java.util.Map;

import com.mkv.exception.NonRecoverableException;

public abstract class Root {

    private String name;

    private Compute host;

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {

        return name;
    }

    public Compute getHost() {
        return host;
    }

    public void setHostContainer(Compute host) {
        this.host = host;
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
}
