/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors Python's openjiuwen.agent_evolving.agent_rl.schemas.RolloutMessage.
 * 
 * @since 0.1.7
 */
@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RolloutMessage {
    private String taskId;
    private String originTaskId;
    private String rolloutId;
    private String startTime;
    private String endTime;

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<Rollout> rolloutInfo = new ArrayList<>();

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<Double> rewardList = new ArrayList<>();
    private Double globalReward;
    private int turnCount = 0;
    private Integer roundNum;

    /**
     * RolloutMessage.
     * 
     * @param taskId taskId
     * @param originTaskId originTaskId
     * @param rolloutId rolloutId
     * @param startTime startTime
     * @param endTime endTime
     * @param rolloutInfo rolloutInfo
     * @param rewardList rewardList
     * @param globalReward globalReward
     * @param turnCount turnCount
     * @param roundNum roundNum
     * @since 0.1.7
     */
    public RolloutMessage(String taskId, String originTaskId, String rolloutId, String startTime, String endTime,
            List<Rollout> rolloutInfo, List<Double> rewardList, Double globalReward, int turnCount, Integer roundNum) {
        this.taskId = taskId;
        this.originTaskId = originTaskId;
        this.rolloutId = rolloutId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.rolloutInfo = rolloutInfo != null ? rolloutInfo : new ArrayList<>();
        this.rewardList = rewardList != null ? rewardList : new ArrayList<>();
        this.globalReward = globalReward;
        this.turnCount = turnCount;
        this.roundNum = roundNum;
    }

    /**
     * getRollout_info.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Rollout> getRollout_info() {
        return getRolloutInfo();
    }

    /**
     * getReward_list.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<Double> getReward_list() {
        return getRewardList();
    }
}
