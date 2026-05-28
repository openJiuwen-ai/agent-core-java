/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.schemas;

import java.util.HashMap;
import java.util.Map;

/**
 * Minimal training task unit.
 * <p>
 * Mirrors Python's {@code RLTask} in
 * {@code openjiuwen.agent_evolving.agent_rl.schemas}.
 */
public class RLTask {

    private String taskId;
    private String originTaskId;
    private Map<String, Object> taskSample = new HashMap<>();
    private int roundNum = 0;

    public RLTask() {
    }

    public RLTask(String taskId, String originTaskId) {
        this.taskId = taskId;
        this.originTaskId = originTaskId;
    }

    public RLTask(String taskId, String originTaskId, Map<String, Object> taskSample, int roundNum) {
        this.taskId = taskId;
        this.originTaskId = originTaskId;
        this.taskSample = taskSample != null ? new HashMap<>(taskSample) : new HashMap<>();
        this.roundNum = roundNum;
    }

    // Getters and setters
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getOriginTaskId() { return originTaskId; }
    public void setOriginTaskId(String originTaskId) { this.originTaskId = originTaskId; }
    public Map<String, Object> getTaskSample() { return taskSample; }
    public void setTaskSample(Map<String, Object> taskSample) { 
        this.taskSample = taskSample != null ? new HashMap<>(taskSample) : new HashMap<>(); 
    }
    public int getRoundNum() { return roundNum; }
    public void setRoundNum(int roundNum) { this.roundNum = roundNum; }
}