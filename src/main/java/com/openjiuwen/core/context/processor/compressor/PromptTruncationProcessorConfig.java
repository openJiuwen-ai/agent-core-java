/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for the {@link PromptTruncationProcessor} ContextProcessor.
 * <p>
 * Truncates oversized {@code UserMessage} content in-place when the context
 * window exceeds {@link #maxContextTokens}. No LLM call is made; the
 * processor is purely local and character-level.
 *
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptTruncationProcessorConfig {
    @Builder.Default
    private int maxContextTokens = 4096;

    @Builder.Default
    private int preserveHeadChars = 200;

    @Builder.Default
    private int preserveTailChars = 100;

    @Builder.Default
    private String truncatedMarker = "...[TRUNCATED: removed %d chars]...";

    @Builder.Default
    private boolean isTruncateSystemMessages = false;

    /**
     * validate.
     *
     * @since 0.1.7
     */
    public void validate() {
        if (maxContextTokens <= 0) {
            throw new IllegalArgumentException("maxContextTokens must be > 0, got " + maxContextTokens);
        }
        if (preserveHeadChars < 0) {
            throw new IllegalArgumentException("preserveHeadChars must be >= 0, got " + preserveHeadChars);
        }
        if (preserveTailChars < 0) {
            throw new IllegalArgumentException("preserveTailChars must be >= 0, got " + preserveTailChars);
        }
        if (truncatedMarker == null || truncatedMarker.isBlank()) {
            throw new IllegalArgumentException("truncatedMarker must not be blank");
        }
    }
}
