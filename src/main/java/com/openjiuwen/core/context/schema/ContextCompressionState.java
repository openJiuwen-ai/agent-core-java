/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.schema;

import com.openjiuwen.core.context.ContextStats;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Full state record of a single context-compression lifecycle event.
 * <p>
 * Mirrors Python's {@code ContextCompressionState} from
 * {@code context_engine/schema/context_state.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextCompressionState {

    public static final String TYPE = "context_compression_state";

    @Builder.Default
    private String type = TYPE;

    private String operationId;

    /**
     * One of: started, completed, noop, skipped, failed.
     */
    private String status;

    /**
     * One of: add_messages, get_context_window, active_compress.
     */
    private String phase;

    @Builder.Default
    private String processor = "";

    @Builder.Default
    private String model = "";

    private ContextCompressionMetric before;

    private ContextCompressionMetric after;

    @Builder.Default
    private ContextStats statistic = new ContextStats();

    private ContextCompressionSaved saved;

    private Integer durationMs;

    private Integer contextMax;

    @Builder.Default
    private String summary = "";

    private String error;
}
