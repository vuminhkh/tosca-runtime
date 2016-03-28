package com.toscaruntime.deployment;

/**
 * This represents an unfinished node task of the running execution that was loaded at initial load
 *
 * @author Minh Khang VU
 */
public class NodeTaskDTO {

    private String nodeInstanceId;

    private String interfaceName;

    private String operationName;

    public NodeTaskDTO(String nodeInstanceId, String interfaceName, String operationName) {
        this.nodeInstanceId = nodeInstanceId;
        this.interfaceName = interfaceName;
        this.operationName = operationName;
    }

    public String getNodeInstanceId() {
        return nodeInstanceId;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public String getOperationName() {
        return operationName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NodeTaskDTO that = (NodeTaskDTO) o;

        if (!getNodeInstanceId().equals(that.getNodeInstanceId())) return false;
        if (!getInterfaceName().equals(that.getInterfaceName())) return false;
        return getOperationName().equals(that.getOperationName());

    }

    @Override
    public int hashCode() {
        int result = getNodeInstanceId().hashCode();
        result = 31 * result + getInterfaceName().hashCode();
        result = 31 * result + getOperationName().hashCode();
        return result;
    }
}
