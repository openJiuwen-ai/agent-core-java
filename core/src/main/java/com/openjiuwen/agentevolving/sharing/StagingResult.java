/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.sharing;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.openjiuwen.agentevolving.checkpointing.EvolutionRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * Mirrors Python's {@code StagingResult} in
 * {@code openjiuwen/agent_evolving/sharing/types.py}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class StagingResult {

    private List<SharedExperience> stagedForShare = new ArrayList<>();
    private List<DroppedExperience> droppedForShare = new ArrayList<>();

    public StagingResult() {
    }

    public static StagingResult empty() {
        return new StagingResult();
    }

    public boolean hasShareable() {
        return !stagedForShare.isEmpty();
    }

    public List<SharedExperience> getStagedForShare() {
        return new ArrayList<>(stagedForShare);
    }

    public void setStagedForShare(List<SharedExperience> stagedForShare) {
        this.stagedForShare = stagedForShare == null ? new ArrayList<>() : new ArrayList<>(stagedForShare);
    }

    public List<DroppedExperience> getDroppedForShare() {
        return new ArrayList<>(droppedForShare);
    }

    public void setDroppedForShare(List<DroppedExperience> droppedForShare) {
        this.droppedForShare = droppedForShare == null ? new ArrayList<>() : new ArrayList<>(droppedForShare);
    }

    /**
     * Mirrors the tuple payload in {@code dropped_for_share}.
     */
    public record DroppedExperience(EvolutionRecord record, String reason) {
    }
}
