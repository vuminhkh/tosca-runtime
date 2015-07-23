package tosca.relationships;

import java.util.Map;

import tosca.nodes.Compute;

import com.mkv.exception.NonRecoverableException;

public abstract class Root {

    private Compute sourceHost;

    private Compute targetHost;

    protected void executeSourceOperation(String operationArtifactPath, Map<String, String> inputs) {
        if (sourceHost == null) {
            throw new NonRecoverableException("The relationship's source is not hosted on a compute, operation cannot be executed");
        }
        sourceHost.execute(operationArtifactPath, inputs);
    }

    protected void executeTargetOperation(String operationArtifactPath, Map<String, String> inputs) {
        if (targetHost == null) {
            throw new NonRecoverableException("The relationship's target is not hosted on a compute, operation cannot be executed");
        }
        targetHost.execute(operationArtifactPath, inputs);
    }

    public void setSourceHost(Compute sourceHost) {
        this.sourceHost = sourceHost;
    }

    public void setTargetHost(Compute targetHost) {
        this.targetHost = targetHost;
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
