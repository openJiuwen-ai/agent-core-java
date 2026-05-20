/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.utils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class BuildProgress used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class BuildProgress {
    private String sessionId;
    private String agentType;
    private ProgressStage currentStage;
    private ProgressStatus currentStatus;
    private String currentMessage;
    @Builder.Default
    private List<ProgressStep> steps = new ArrayList<>();
    @Builder.Default
    private Double overallProgress = 0.0;
    @Builder.Default
    private Instant startTime = Instant.now();
    @Builder.Default
    private Instant lastUpdateTime = Instant.now();
    private String error;

    /**
     * Auto-generated for codecheck compliance.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("session_id", sessionId);
        data.put("agent_type", agentType);
        data.put("current_stage", currentStage != null ? currentStage.getValue() : null);
        data.put("current_status", currentStatus != null ? currentStatus.getValue() : null);
        data.put("current_message", currentMessage);
        data.put("steps", steps.stream().map(ProgressStep::toMap).toList());
        data.put("overall_progress", overallProgress);
        data.put("start_time", startTime != null ? startTime.toString() : null);
        data.put("last_update_time", lastUpdateTime != null ? lastUpdateTime.toString() : null);
        data.put("error", error);
        return data;
    }
}
