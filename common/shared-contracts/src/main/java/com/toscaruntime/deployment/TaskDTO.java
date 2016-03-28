package com.toscaruntime.deployment;

public class TaskDTO {

    private String taskId;

    public TaskDTO(String taskId) {
        this.taskId = taskId;
    }

    public String getTaskId() {
        return taskId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TaskDTO taskDTO = (TaskDTO) o;

        return getTaskId().equals(taskDTO.getTaskId());

    }

    @Override
    public int hashCode() {
        return getTaskId().hashCode();
    }
}
