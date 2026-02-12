// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.singleagent.schema;

import com.openjiuwen.core.controller.schema.TaskStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the result of an agent execution.
 * 
 * <p>Contains task information, status, artifacts produced, and metadata.
 * 
 * <p>Python reference: {@code agent-core/openjiuwen/core/single_agent/schema/agent_result.py}
 */
public class AgentResult {
    
    /**
     * The task ID.
     */
    private String taskId;
    
    /**
     * The session ID.
     */
    private String sessionId;
    
    /**
     * The task status.
     */
    private TaskStatus status;
    
    /**
     * Artifacts produced by the agent.
     */
    private List<Artifact> artifacts;
    
    /**
     * Additional metadata.
     */
    private Map<String, Object> metadata;
    
    /**
     * Creates an empty agent result.
     */
    public AgentResult() {
        this.artifacts = new ArrayList<>();
        this.metadata = new HashMap<>();
    }
    
    /**
     * Creates an agent result with the specified task ID.
     *
     * @param taskId the task ID
     */
    public AgentResult(String taskId) {
        this.taskId = taskId;
        this.artifacts = new ArrayList<>();
        this.metadata = new HashMap<>();
    }
    
    /**
     * Creates an agent result with full details.
     *
     * @param taskId the task ID
     * @param sessionId the session ID
     * @param status the task status
     */
    public AgentResult(String taskId, String sessionId, TaskStatus status) {
        this.taskId = taskId;
        this.sessionId = sessionId;
        this.status = status;
        this.artifacts = new ArrayList<>();
        this.metadata = new HashMap<>();
    }
    
    // Getters and Setters
    
    public String getTaskId() {
        return taskId;
    }
    
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
    
    public TaskStatus getStatus() {
        return status;
    }
    
    public void setStatus(TaskStatus status) {
        this.status = status;
    }
    
    public List<Artifact> getArtifacts() {
        return artifacts;
    }
    
    public void setArtifacts(List<Artifact> artifacts) {
        this.artifacts = artifacts != null ? new ArrayList<>(artifacts) : new ArrayList<>();
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }
    
    /**
     * Adds an artifact to this result.
     *
     * @param artifact the artifact to add
     * @return this result for chaining
     */
    public AgentResult addArtifact(Artifact artifact) {
        if (artifact != null) {
            this.artifacts.add(artifact);
        }
        return this;
    }
    
    /**
     * Adds a metadata entry.
     *
     * @param key the metadata key
     * @param value the metadata value
     * @return this result for chaining
     */
    public AgentResult addMetadata(String key, Object value) {
        if (key != null) {
            this.metadata.put(key, value);
        }
        return this;
    }
    
    @Override
    public String toString() {
        return String.format("AgentResult{taskId='%s', sessionId='%s', status=%s, artifacts=%d}", 
            taskId, sessionId, status, artifacts != null ? artifacts.size() : 0);
    }
}

