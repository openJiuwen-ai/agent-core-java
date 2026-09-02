/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.openjiuwen.core.context.ContextStats;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Compression state event payload.
 * <p>
 * Mirrors Python's {@code ContextCompressionState}.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextCompressionState {
    /**
     * CONTEXT_COMPRESSION_STATE_TYPE.
     * 
     * @since 0.1.7
     */
    public static final String CONTEXT_COMPRESSION_STATE_TYPE = "context_compression_state";

    @Builder.Default
    private String type = CONTEXT_COMPRESSION_STATE_TYPE;

    @JsonProperty("operation_id")
    private String operationId;

    private String status;

    private String phase;

    @Builder.Default
    private String processor = "";

    @Builder.Default
    private String model = "";

    private ContextCompressionMetric before;

    private ContextCompressionMetric after;

    @Builder.Default
    /**
     * ContextStats.
     * 
     * @since 0.1.7
     */
    private ContextStats statistic = new ContextStats();

    private ContextCompressionSaved saved;

    @JsonProperty("duration_ms")
    private Integer durationMs;

    @JsonProperty("context_max")
    private Integer contextMax;

    @Builder.Default
    private String summary = "";

    private String error;
}
