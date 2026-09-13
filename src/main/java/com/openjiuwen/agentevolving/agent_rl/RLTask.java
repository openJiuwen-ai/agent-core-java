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
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RLTask {
    private String taskId;
    private String originTaskId;

    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> taskSample = new LinkedHashMap<>();
    private int roundNum = 0;

    /**
     * RLTask.
     * 
     * @param taskId taskId
     * @param originTaskId originTaskId
     * @since 0.1.7
     */
    public RLTask(String taskId, String originTaskId) {
        this(taskId, originTaskId, null, 0);
    }

    /**
     * RLTask.
     * 
     * @param taskId taskId
     * @param originTaskId originTaskId
     * @param taskSample taskSample
     * @param roundNum roundNum
     * @since 0.1.7
     */
    public RLTask(String taskId, String originTaskId, Map<String, Object> taskSample, int roundNum) {
        this.taskId = Objects.requireNonNull(taskId, "taskId is required");
        this.originTaskId = Objects.requireNonNull(originTaskId, "originTaskId is required");
        this.taskSample = taskSample != null ? taskSample : new LinkedHashMap<>();
        this.roundNum = roundNum;
    }

    /**
     * setTaskId.
     * 
     * @param taskId taskId
     * @since 0.1.7
     */
    public void setTaskId(String taskId) {
        this.taskId = Objects.requireNonNull(taskId, "taskId is required");
    }

    /**
     * setOriginTaskId.
     * 
     * @param originTaskId originTaskId
     * @since 0.1.7
     */
    public void setOriginTaskId(String originTaskId) {
        this.originTaskId = Objects.requireNonNull(originTaskId, "originTaskId is required");
    }

    /**
     * getTask_id.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getTask_id() {
        return getTaskId();
    }

    /**
     * getOrigin_task_id.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getOrigin_task_id() {
        return getOriginTaskId();
    }

    /**
     * getTask_sample.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getTask_sample() {
        return getTaskSample();
    }

    /**
     * getRound_num.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getRound_num() {
        return getRoundNum();
    }
}
