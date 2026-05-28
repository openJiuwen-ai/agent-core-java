/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metric snapshot for a single point in the compression lifecycle.
 * <p>
 * Mirrors Python's {@code ContextCompressionMetric} from
 * {@code context_engine/schema/context_state.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextCompressionMetric {

    @Builder.Default
    private String time = null;

    @Builder.Default
    private int messages = 0;

    @Builder.Default
    private int tokens = 0;

    @Builder.Default
    private Integer contextPercent = null;
}
