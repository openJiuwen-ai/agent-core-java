/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Compression metric snapshot.
 * <p>
 * Mirrors Python's {@code ContextCompressionMetric}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextCompressionMetric {
    private String time;

    @Builder.Default
    private int messages = 0;

    @Builder.Default
    private int tokens = 0;

    @JsonProperty("context_percent")
    private Integer contextPercent;
}
