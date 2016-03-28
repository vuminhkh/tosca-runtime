package com.toscaruntime.deployment;

import java.util.List;
import java.util.Map;

/**
 * This defines common contract to persist deployment to data source, all method is synchronous call to datasource
 *
 * @author Minh Khang VU
 */
public interface DeploymentPersister {

    void syncInsertNodeIfNotExist(String id, int instancesCount);

    void syncSaveNodeInstancesCount(String nodeId, int newInstancesCount);

    int syncGetNodeInstancesCount(String id);

    void syncInsertInstanceIfNotExist(String id, String nodeId, String state);

    void syncDeleteInstance(String id);

    void syncSaveInstanceState(String id, String newState);

    String syncGetInstanceState(String id);

    void syncSaveInstanceAttribute(String instanceId, String key, String value);

    void syncDeleteInstanceAttribute(String instanceId, String key);

    void syncInsertRelationshipIfNotExist(String sourceId, String targetId, String relationshipType);

    void syncInsertRelationshipInstanceIfNotExist(String sourceInstanceId, String targetInstanceId, String sourceNodeId, String targetNodeId, String relationshipType, String state);

    void syncDeleteRelationshipInstance(String sourceInstanceId, String targetInstanceId, String relationshipType);

    void syncSaveRelationshipAttribute(String sourceInstanceId, String targetInstanceId, String relationshipType, String key, String value);

    void syncDeleteRelationshipAttribute(String sourceInstanceId, String targetInstanceId, String relationshipType, String key);

    void syncSaveRelationshipState(String sourceInstanceId, String targetInstanceId, String relationshipType, String newState);

    void syncSaveInstanceOutputs(String instanceId, String interfaceName, String operationName, Map<String, String> outputs);

    void syncSaveRelationshipOutputs(String sourceInstanceId, String targetInstanceId, String relationshipType, String interfaceName, String operationName, Map<String, String> outputs);

    Map<String, String> syncGetOutputs(String instanceId, String interfaceName, String operationName);

    Map<String, String> syncGetAttributes(String instanceId);

    Map<String, String> syncGetRelationshipAttributes(String sourceInstanceId, String targetInstanceId, String relationshipType);

    List<String> syncGetOutputInterfaces(String instanceId);

    List<String> syncGetOutputOperations(String instanceId, String interfaceName);

    List<String> syncGetRelationshipOutputInterfaces(String sourceInstanceId, String targetInstanceId, String relationshipType);

    List<String> syncGetRelationshipOutputOperations(String sourceInstanceId, String targetInstanceId, String relationshipType, String interfaceName);

    Map<String, String> syncGetRelationshipOutputs(String sourceInstanceId, String targetInstanceId, String relationshipType, String interfaceName, String operationName);

    String syncGetRelationshipInstanceState(String sourceInstanceId, String targetInstanceId, String relationshipType);

    boolean hasExistingData();

    void syncInsertNewNodeTask(String instanceId, String interfaceName, String operationName);

    void syncStartNodeTask(String instanceId, String interfaceName, String operationName);

    void syncFinishNodeTask(String instanceId, String interfaceName, String operationName);

    void syncStopNodeTask(String instanceId, String interfaceName, String operationName, String error);

    void syncInsertNewTask(String taskId);

    void syncStartTask(String taskId);

    void syncFinishTask(String taskId);

    void syncStopTask(String taskId, String error);

    void syncInsertNewRelationshipTask(String sourceInstanceId, String targetInstanceId, String relationshipType, String interfaceName, String operationName);

    void syncStartRelationshipTask(String sourceInstanceId, String targetInstanceId, String relationshipType, String interfaceName, String operationName);

    void syncFinishRelationshipTask(String sourceInstanceId, String targetInstanceId, String relationshipType, String interfaceName, String operationName);

    void syncStopRelationshipTask(String sourceInstanceId, String targetInstanceId, String relationshipType, String interfaceName, String operationName, String error);

    RunningExecutionDTO syncGetRunningExecution();

    Map<NodeTaskDTO, String> syncGetRunningExecutionNodeTasks();

    Map<RelationshipTaskDTO, String> syncGetRunningExecutionRelationshipTasks();

    Map<TaskDTO, String> syncGetRunningExecutionTasks();

    void syncInsertExecutionInputs(Map<String, Object> inputs);

    void syncFinishRunningExecution();

    void syncStopRunningExecution(String error);

    void syncStopRunningExecution();

    void syncCancelRunningExecution();
}
