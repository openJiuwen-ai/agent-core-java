  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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
     * {@code null} means unlimited. Must be > 0 if set.
     */
    private Integer maxContextMessageNum;

    /**
     * Default window size (number of messages) when building a context window.
     * {@code null} means no default limit. Must be > 0 if set.
     */
    private Integer defaultWindowMessageNum;

    /**
     * Default number of dialogue rounds to keep in the context window.
     * {@code null} means no round-based truncation by default. Must be > 0 if set.
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

    /**
     * Validate configuration constraints matching Python Pydantic {@code Field(gt=0)} rules.
     *
     * @throws IllegalArgumentException if any constraint is violated
     */
    public void validate() {
        if (maxContextMessageNum != null && maxContextMessageNum <= 0) {
            throw new IllegalArgumentException(
                    "Input should be greater than 0 [type=greater_than, input_value=" + maxContextMessageNum + ", input_type=int]");
        }
        if (defaultWindowMessageNum != null && defaultWindowMessageNum <= 0) {
            throw new IllegalArgumentException(
                    "Input should be greater than 0 [type=greater_than, input_value=" + defaultWindowMessageNum + ", input_type=int]");
        }
        if (defaultWindowRoundNum != null && defaultWindowRoundNum <= 0) {
            throw new IllegalArgumentException(
                    "Input should be greater than 0 [type=greater_than, input_value=" + defaultWindowRoundNum + ", input_type=int]");
        }
    }
}
