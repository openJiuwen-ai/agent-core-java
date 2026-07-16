/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.utils;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public class ProgressStep used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgressStep {
    private ProgressStage stage;
    private ProgressStatus status;
    private String message;
    @Builder.Default
    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> details = new LinkedHashMap<>();
    private Double duration;
    private String error;
    @Builder.Default
    /**
     * Instant.now.
     * 
     * @since 0.1.7
     */
    private Instant timestamp = Instant.now();

    /**
     * toMap.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> toMap() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stage", stage != null ? stage.getValue() : null);
        data.put("status", status != null ? status.getValue() : null);
        data.put("message", message);
        data.put("details", details);
        data.put("duration", duration);
        data.put("error", error);
        data.put("timestamp", timestamp != null ? timestamp.toString() : null);
        return data;
    }
}
