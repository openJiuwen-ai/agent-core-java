/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.schemas;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal training task unit.
 *
 * <p>Mirrors Python's {@code RLTask} in
 * {@code openjiuwen/agent_evolving/agent_rl/schemas.py}.</p>
 */
public class RLTask {

    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("origin_task_id")
    private String originTaskId;

    @JsonProperty("task_sample")
    private Map<String, Object> taskSample = new HashMap<>();

    @JsonProperty("round_num")
    private int roundNum = 0;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getOriginTaskId() {
        return originTaskId;
    }

    public void setOriginTaskId(String originTaskId) {
        this.originTaskId = originTaskId;
    }

    public Map<String, Object> getTaskSample() {
        return taskSample;
    }

    public void setTaskSample(Map<String, Object> taskSample) {
        this.taskSample = taskSample != null ? new HashMap<>(taskSample) : new HashMap<>();
    }

    public int getRoundNum() {
        return roundNum;
    }

    public void setRoundNum(int roundNum) {
        this.roundNum = roundNum;
    }
}
