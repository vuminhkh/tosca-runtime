package com.toscaruntime.deployment;

/**
 * This represents an unfinished relationship task of the running execution that was loaded at initial load
 *
 * @author Minh Khang VU
 */
public class RelationshipTaskDTO {

    private Relationship relationship;

    private String interfaceName;

    private String operationName;

    public RelationshipTaskDTO(String sourceInstanceId, String targetInstanceId, String relationshipType, String interfaceName, String operationName) {
        this.relationship = new Relationship(sourceInstanceId, targetInstanceId, relationshipType);
        this.interfaceName = interfaceName;
        this.operationName = operationName;
    }

    public Relationship getRelationship() {
        return relationship;
    }

    public String getSourceInstanceId() {
        return relationship.getSourceInstanceId();
    }

    public String getTargetInstanceId() {
        return relationship.getTargetInstanceId();
    }

    public String getRelationshipType() {
        return relationship.getRelationshipType();
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public String getOperationName() {
        return operationName;
    }

    public static class Relationship {

        private String sourceInstanceId;

        private String targetInstanceId;

        private String relationshipType;

        public Relationship(String sourceInstanceId, String targetInstanceId, String relationshipType) {
            this.sourceInstanceId = sourceInstanceId;
            this.targetInstanceId = targetInstanceId;
            this.relationshipType = relationshipType;
        }

        public String getSourceInstanceId() {
            return sourceInstanceId;
        }

        public String getTargetInstanceId() {
            return targetInstanceId;
        }

        public String getRelationshipType() {
            return relationshipType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Relationship that = (Relationship) o;

            if (!sourceInstanceId.equals(that.sourceInstanceId)) return false;
            if (!targetInstanceId.equals(that.targetInstanceId)) return false;
            return relationshipType.equals(that.relationshipType);

        }

        @Override
        public int hashCode() {
            int result = sourceInstanceId.hashCode();
            result = 31 * result + targetInstanceId.hashCode();
            result = 31 * result + relationshipType.hashCode();
            return result;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RelationshipTaskDTO that = (RelationshipTaskDTO) o;

        if (!getRelationship().equals(that.getRelationship())) return false;
        if (!getInterfaceName().equals(that.getInterfaceName())) return false;
        return getOperationName().equals(that.getOperationName());

    }

    @Override
    public int hashCode() {
        int result = getRelationship().hashCode();
        result = 31 * result + getInterfaceName().hashCode();
        result = 31 * result + getOperationName().hashCode();
        return result;
    }
}
