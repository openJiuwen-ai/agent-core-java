/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.schemas;

import java.util.ArrayList;
import java.util.List;

/**
 * Complete execution result for a single task, aggregating
 * multiple turns and the associated rewards.
 * <p>
 * Mirrors Python's {@code RolloutMessage} in
 * {@code openjiuwen.agent_evolving.agent_rl.schemas}.
 */
public class RolloutMessage {

    private String taskId;
    private String originTaskId;
    private String rolloutId;
    private String startTime;
    private String endTime;
    private List<Rollout> rolloutInfo = new ArrayList<>();
    private List<Double> rewardList = new ArrayList<>();
    private Double globalReward;
    private int turnCount = 0;
    private Integer roundNum;

    public RolloutMessage() {
    }

    // Getters and setters
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getOriginTaskId() { return originTaskId; }
    public void setOriginTaskId(String originTaskId) { this.originTaskId = originTaskId; }
    public String getRolloutId() { return rolloutId; }
    public void setRolloutId(String rolloutId) { this.rolloutId = rolloutId; }
    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public List<Rollout> getRolloutInfo() { return rolloutInfo; }
    public void setRolloutInfo(List<Rollout> rolloutInfo) { 
        this.rolloutInfo = rolloutInfo != null ? new ArrayList<>(rolloutInfo) : new ArrayList<>(); 
    }
    public List<Double> getRewardList() { return rewardList; }
    public void setRewardList(List<Double> rewardList) { 
        this.rewardList = rewardList != null ? new ArrayList<>(rewardList) : new ArrayList<>(); 
    }
    public Double getGlobalReward() { return globalReward; }
    public void setGlobalReward(Double globalReward) { this.globalReward = globalReward; }
    public int getTurnCount() { return turnCount; }
    public void setTurnCount(int turnCount) { this.turnCount = turnCount; }
    public Integer getRoundNum() { return roundNum; }
    public void setRoundNum(Integer roundNum) { this.roundNum = roundNum; }

    /**
     * Add a rollout to the rollout info list.
     * 
     * @param rollout Rollout to add
     */
    public void addRollout(Rollout rollout) {
        if (rollout != null) {
            rolloutInfo.add(rollout);
            turnCount = rolloutInfo.size();
        }
    }

    /**
     * Add a reward to the reward list.
     * 
     * @param reward Reward value
     */
    public void addReward(double reward) {
        rewardList.add(reward);
    }
}