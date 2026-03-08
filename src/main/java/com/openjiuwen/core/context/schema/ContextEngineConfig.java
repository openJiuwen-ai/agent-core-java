/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.context.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for the ContextEngine.
 * <p>
 * Mirrors Python's {@code ContextEngineConfig} from
 * {@code context_engine/schema/config.py}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextEngineConfig {

    /**
     * Maximum number of messages retained in the context message buffer.
     * {@code null} means unlimited.
     */
    private Integer maxContextMessageNum;

    /**
     * Default window size (number of messages) when building a context window.
     * {@code null} means no default limit.
     */
    private Integer defaultWindowMessageNum;

    /**
     * Default number of dialogue rounds to keep in the context window.
     * {@code null} means no round-based truncation by default.
     */
    private Integer defaultWindowRoundNum;

    /**
     * Whether to enable KV cache release optimisation for inference-affinity models.
     */
    @Builder.Default
    private boolean enableKvCacheRelease = false;

    /**
     * Whether to enable the reload tool that can re-inject offloaded messages.
     */
    @Builder.Default
    private boolean enableReload = false;
}
