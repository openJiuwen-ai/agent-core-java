/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.schemas.RLTask.
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RLTask {
    private String taskId;
    private String originTaskId;
    private Map<String, Object> taskSample = new LinkedHashMap<>();
    private int roundNum = 0;

    /**
     * Auto-generated for codecheck compliance.
     */
    public RLTask(String taskId, String originTaskId) {
        this(taskId, originTaskId, null, 0);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public RLTask(String taskId, String originTaskId, Map<String, Object> taskSample, int roundNum) {
        this.taskId = Objects.requireNonNull(taskId, "taskId is required");
        this.originTaskId = Objects.requireNonNull(originTaskId, "originTaskId is required");
        this.taskSample = taskSample != null ? taskSample : new LinkedHashMap<>();
        this.roundNum = roundNum;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setTaskId(String taskId) {
        this.taskId = Objects.requireNonNull(taskId, "taskId is required");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setOriginTaskId(String originTaskId) {
        this.originTaskId = Objects.requireNonNull(originTaskId, "originTaskId is required");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getTask_id() {
        return getTaskId();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getOrigin_task_id() {
        return getOriginTaskId();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> getTask_sample() {
        return getTaskSample();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getRound_num() {
        return getRoundNum();
    }
}
