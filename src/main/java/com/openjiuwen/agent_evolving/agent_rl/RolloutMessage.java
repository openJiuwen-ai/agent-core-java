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

    private List<Rollout> rolloutInfo = new ArrayList<>();
    private List<Double> rewardList = new ArrayList<>();
    private Double globalReward;
    private int turnCount = 0;
    private Integer roundNum;

    /**
     * Auto-generated for codecheck compliance.
     */
    public RolloutMessage(
            String taskId,
            String originTaskId,
            String rolloutId,
            String startTime,
            String endTime,
            List<Rollout> rolloutInfo,
            List<Double> rewardList,
            Double globalReward,
            int turnCount,
            Integer roundNum
    ) {
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
     * Auto-generated for codecheck compliance.
     */
    public List<Rollout> getRollout_info() {
        return getRolloutInfo();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<Double> getReward_list() {
        return getRewardList();
    }
}
