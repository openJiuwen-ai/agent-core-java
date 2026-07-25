/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.schemas;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Complete execution result for a single task.
 *
 * <p>Mirrors Python's {@code RolloutMessage} in
 * {@code openjiuwen/agent_evolving/agent_rl/schemas.py}.</p>
 */
public class RolloutMessage {

    @JsonProperty("task_id")
    private String taskId;

    @JsonProperty("origin_task_id")
    private String originTaskId;

    @JsonProperty("rollout_id")
    private String rolloutId;

    @JsonProperty("start_time")
    private String startTime;

    @JsonProperty("end_time")
    private String endTime;

    @JsonProperty("rollout_info")
    private List<Rollout> rolloutInfo = new ArrayList<>();

    @JsonProperty("reward_list")
    private List<Double> rewardList = new ArrayList<>();

    @JsonProperty("global_reward")
    private Double globalReward;

    @JsonProperty("turn_count")
    private int turnCount = 0;

    @JsonProperty("round_num")
    private Integer roundNum;

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

    public String getRolloutId() {
        return rolloutId;
    }

    public void setRolloutId(String rolloutId) {
        this.rolloutId = rolloutId;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public List<Rollout> getRolloutInfo() {
        return rolloutInfo;
    }

    public void setRolloutInfo(List<Rollout> rolloutInfo) {
        this.rolloutInfo = rolloutInfo != null ? new ArrayList<>(rolloutInfo) : new ArrayList<>();
    }

    public List<Double> getRewardList() {
        return rewardList;
    }

    public void setRewardList(List<Double> rewardList) {
        this.rewardList = rewardList != null ? new ArrayList<>(rewardList) : new ArrayList<>();
    }

    public Double getGlobalReward() {
        return globalReward;
    }

    public void setGlobalReward(Double globalReward) {
        this.globalReward = globalReward;
    }

    public int getTurnCount() {
        return turnCount;
    }

    public void setTurnCount(int turnCount) {
        this.turnCount = turnCount;
    }

    public Integer getRoundNum() {
        return roundNum;
    }

    public void setRoundNum(Integer roundNum) {
        this.roundNum = roundNum;
    }
}
